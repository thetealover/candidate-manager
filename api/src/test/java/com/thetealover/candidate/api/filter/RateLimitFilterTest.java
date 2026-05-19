package com.thetealover.candidate.api.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thetealover.candidate.api.ratelimit.RateLimit;
import com.thetealover.candidate.api.ratelimit.RateLimitConfig;
import com.thetealover.candidate.api.ratelimit.RateLimitDecision;
import com.thetealover.candidate.api.ratelimit.RateLimitExceededException;
import com.thetealover.candidate.api.ratelimit.RateLimitStore;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.filter.ServerFilterChain;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

  private static final RateLimit IP_READ = new RateLimit(120, Duration.ofMinutes(1));
  private static final RateLimit IP_WRITE = new RateLimit(30, Duration.ofMinutes(1));
  private static final RateLimit ACTOR_WRITE = new RateLimit(10, Duration.ofMinutes(1));
  private static final Instant RESET_AT = Instant.parse("2026-05-19T12:00:00Z");
  private static final RateLimitDecision ALLOWED =
      new RateLimitDecision(true, 1L, RESET_AT, IP_READ);
  private static final RateLimitDecision REJECTED_IP =
      new RateLimitDecision(false, 0L, RESET_AT, IP_READ);
  private static final RateLimitDecision REJECTED_ACTOR =
      new RateLimitDecision(false, 0L, RESET_AT, ACTOR_WRITE);

  @Mock private RateLimitStore store;
  @Mock private ServerFilterChain chain;

  private RateLimitFilter filter;

  @BeforeEach
  void setUp() {
    final RateLimitConfig config = new RateLimitConfig();
    config.setEnabled(true);
    config.getPerIp().setRead(new RateLimitConfig.Limit(120, Duration.ofMinutes(1)));
    config.getPerIp().setWrite(new RateLimitConfig.Limit(30, Duration.ofMinutes(1)));
    config.getPerActor().setWrite(new RateLimitConfig.Limit(10, Duration.ofMinutes(1)));
    filter = new RateLimitFilter(store, config);

    lenient()
        .when(chain.proceed(any()))
        .thenReturn(Publishers.just((MutableHttpResponse<?>) HttpResponse.ok()));
  }

  // ---- Key extraction --------------------------------------------------------

  @Test
  void uses_x_forwarded_for_first_entry_when_present() {
    final HttpRequest<?> request =
        fakeRequest(
            HttpMethod.GET,
            "/api/v1/candidates",
            "203.0.113.4, 198.51.100.1",
            null,
            "198.51.100.50");

    when(store.tryConsume(eq("ip:203.0.113.4"), any())).thenReturn(ALLOWED);

    blockOne(filter.doFilter(request, chain));

    verify(store).tryConsume("ip:203.0.113.4", IP_READ);
  }

  @Test
  void falls_back_to_socket_remote_address_when_xff_absent() {
    final HttpRequest<?> request =
        fakeRequest(HttpMethod.GET, "/api/v1/candidates", null, null, "198.51.100.50");

    when(store.tryConsume(eq("ip:198.51.100.50"), any())).thenReturn(ALLOWED);

    blockOne(filter.doFilter(request, chain));

    verify(store).tryConsume("ip:198.51.100.50", IP_READ);
  }

  // ---- Method-based limit selection -----------------------------------------

  @Test
  void uses_write_limit_for_post() {
    final HttpRequest<?> request =
        fakeRequest(HttpMethod.POST, "/api/v1/candidates", null, null, "10.0.0.1");

    when(store.tryConsume(eq("ip:10.0.0.1"), eq(IP_WRITE))).thenReturn(ALLOWED);

    blockOne(filter.doFilter(request, chain));

    verify(store).tryConsume("ip:10.0.0.1", IP_WRITE);
  }

  // ---- Per-actor dimension --------------------------------------------------

  @Test
  void applies_per_actor_on_put_eligibility() {
    final HttpRequest<?> request =
        fakeRequest(
            HttpMethod.PUT, "/api/v1/candidates/abc/eligibility", null, "user-99", "10.0.0.2");

    when(store.tryConsume(eq("ip:10.0.0.2"), eq(IP_WRITE))).thenReturn(ALLOWED);
    when(store.tryConsume(eq("actor:user-99"), eq(ACTOR_WRITE))).thenReturn(ALLOWED);

    blockOne(filter.doFilter(request, chain));

    verify(store).tryConsume("ip:10.0.0.2", IP_WRITE);
    verify(store).tryConsume("actor:user-99", ACTOR_WRITE);
  }

  @Test
  void skips_per_actor_when_actor_header_missing() {
    final HttpRequest<?> request =
        fakeRequest(HttpMethod.PUT, "/api/v1/candidates/abc/eligibility", null, null, "10.0.0.3");

    when(store.tryConsume(eq("ip:10.0.0.3"), eq(IP_WRITE))).thenReturn(ALLOWED);

    blockOne(filter.doFilter(request, chain));

    verify(store).tryConsume("ip:10.0.0.3", IP_WRITE);
    verify(store, never()).tryConsume(eq("actor:"), any());
  }

  // ---- Sequential check + refund-on-failure --------------------------------

  @Test
  void rejects_with_per_ip_decision_and_skips_per_actor_when_ip_bucket_empty() {
    final HttpRequest<?> request =
        fakeRequest(
            HttpMethod.PUT, "/api/v1/candidates/abc/eligibility", null, "user-99", "10.0.0.4");

    when(store.tryConsume(eq("ip:10.0.0.4"), any())).thenReturn(REJECTED_IP);

    assertThatThrownBy(() -> blockOne(filter.doFilter(request, chain)))
        .isInstanceOf(RateLimitExceededException.class)
        .extracting(thrown -> ((RateLimitExceededException) thrown).decision())
        .isEqualTo(REJECTED_IP);

    verify(store, never()).tryConsume(eq("actor:user-99"), any());
    verify(store, never()).refund(any(), any());
  }

  @Test
  void refunds_per_ip_token_when_per_actor_rejects() {
    final HttpRequest<?> request =
        fakeRequest(
            HttpMethod.PUT, "/api/v1/candidates/abc/eligibility", null, "user-99", "10.0.0.5");

    when(store.tryConsume(eq("ip:10.0.0.5"), any())).thenReturn(ALLOWED);
    when(store.tryConsume(eq("actor:user-99"), any())).thenReturn(REJECTED_ACTOR);

    assertThatThrownBy(() -> blockOne(filter.doFilter(request, chain)))
        .isInstanceOf(RateLimitExceededException.class)
        .extracting(thrown -> ((RateLimitExceededException) thrown).decision())
        .isEqualTo(REJECTED_ACTOR);

    verify(store, times(1)).refund("ip:10.0.0.5", IP_WRITE);
  }

  // ---- Fail-open on store error ---------------------------------------------

  @Test
  void fails_open_when_store_throws() {
    final HttpRequest<?> request =
        fakeRequest(HttpMethod.GET, "/api/v1/candidates", null, null, "10.0.0.6");

    when(store.tryConsume(any(), any())).thenThrow(new RuntimeException("boom"));

    final MutableHttpResponse<?> response = blockOne(filter.doFilter(request, chain));

    assertThat(response).isNotNull();
    assertThat(response.getStatus().getCode()).isEqualTo(200);
  }

  // ---- Test fixture ---------------------------------------------------------

  /**
   * Build a fully-mocked HttpRequest. {@code xff} and {@code actor} may be null; {@code
   * socketAddress} is the dotted-quad string used for the socket remote.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static HttpRequest<?> fakeRequest(
      final HttpMethod method,
      final String path,
      final String xff,
      final String actor,
      final String socketAddress) {

    final HttpHeaders headers = mock(HttpHeaders.class);
    lenient().when(headers.get("X-Forwarded-For")).thenReturn(xff);
    lenient().when(headers.get("X-Actor-Id")).thenReturn(actor);

    final HttpRequest request = mock(HttpRequest.class);
    lenient().when(request.getMethod()).thenReturn(method);
    lenient().when(request.getPath()).thenReturn(path);
    lenient().when(request.getHeaders()).thenReturn(headers);
    lenient().when(request.getRemoteAddress()).thenReturn(new InetSocketAddress(socketAddress, 0));
    return request;
  }

  /**
   * Subscribe to a single-value {@link Publisher} and return its value, rethrowing any synchronous
   * error. The filter's publishers complete synchronously (either via {@code Publishers.just(...)}
   * from the mocked chain or by throwing on the calling thread), so an unbuffered drain is
   * sufficient — no reactor-core dependency needed.
   */
  private static MutableHttpResponse<?> blockOne(
      final Publisher<MutableHttpResponse<?>> publisher) {
    final AtomicReference<MutableHttpResponse<?>> valueRef = new AtomicReference<>();
    final AtomicReference<Throwable> errorRef = new AtomicReference<>();
    publisher.subscribe(
        new Subscriber<>() {
          @Override
          public void onSubscribe(final Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
          }

          @Override
          public void onNext(final MutableHttpResponse<?> response) {
            valueRef.set(response);
          }

          @Override
          public void onError(final Throwable throwable) {
            errorRef.set(throwable);
          }

          @Override
          public void onComplete() {
            // no-op
          }
        });
    final Throwable error = errorRef.get();
    if (error instanceof RuntimeException runtimeException) {
      throw runtimeException;
    }
    if (error != null) {
      throw new AssertionError("publisher emitted checked error", error);
    }
    return valueRef.get();
  }
}
