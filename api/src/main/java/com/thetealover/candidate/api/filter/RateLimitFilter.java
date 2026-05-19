package com.thetealover.candidate.api.filter;

import com.thetealover.candidate.api.ratelimit.RateLimit;
import com.thetealover.candidate.api.ratelimit.RateLimitConfig;
import com.thetealover.candidate.api.ratelimit.RateLimitDecision;
import com.thetealover.candidate.api.ratelimit.RateLimitExceededException;
import com.thetealover.candidate.api.ratelimit.RateLimitStore;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;

/**
 * Token-bucket rate limiter applied to {@code /api/**}. Runs after {@link RequestContextFilter} so
 * 429 responses still carry the {@code X-Correlation-Id} header set on the way out.
 *
 * <p>For every request: consume one token from the per-IP bucket. For {@code PUT
 * /api/v1/candidates/&#123;id&#125;/eligibility} and {@code DELETE
 * /api/v1/candidates/&#123;id&#125;} also consume one token from the per-actor bucket (keyed by
 * {@code X-Actor-Id}). On per-actor rejection the per-IP token is refunded so the rejected request
 * doesn't count against the IP's budget.
 */
@Filter("/api/**")
@Requires(property = "rate-limit.enabled", value = "true")
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter implements HttpServerFilter {

  public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

  private static final String XFF_HEADER = "X-Forwarded-For";
  private static final String ACTOR_HEADER = "X-Actor-Id";

  private static final Pattern PUT_ELIGIBILITY =
      Pattern.compile("^/api/v1/candidates/[^/]+/eligibility$");
  private static final Pattern DELETE_CANDIDATE = Pattern.compile("^/api/v1/candidates/[^/]+$");

  private final RateLimitStore store;
  private final RateLimitConfig config;

  @Override
  public int getOrder() {
    return ORDER;
  }

  @Override
  public Publisher<MutableHttpResponse<?>> doFilter(
      final HttpRequest<?> request, final ServerFilterChain chain) {

    try {
      final String ipKey = "ip:%s".formatted(extractClientIp(request));
      final RateLimit ipLimit =
          request.getMethod() == HttpMethod.GET ? config.ipRead() : config.ipWrite();

      final RateLimitDecision ipDecision = store.tryConsume(ipKey, ipLimit);
      if (!ipDecision.allowed()) {
        log.debug(
            "rate-limit.rejected dimension=ip key={} method={} path={}",
            ipKey,
            request.getMethod(),
            request.getPath());
        throw new RateLimitExceededException(ipDecision);
      }

      final String actorId = actorIdIfActorEndpoint(request);
      if (actorId != null) {
        final RateLimitDecision actorDecision =
            store.tryConsume("actor:%s".formatted(actorId), config.actorWrite());
        if (!actorDecision.allowed()) {
          store.refund(ipKey, ipLimit);
          log.debug(
              "rate-limit.rejected dimension=actor actorId={} method={} path={}",
              actorId,
              request.getMethod(),
              request.getPath());
          throw new RateLimitExceededException(actorDecision);
        }
      }
    } catch (final RateLimitExceededException rateLimitExceeded) {
      throw rateLimitExceeded;
    } catch (final RuntimeException storeFailure) {
      log.error(
          "rate-limit.store-failure method={} path={}",
          request.getMethod(),
          request.getPath(),
          storeFailure);
      // Fail open — soft control.
    }

    return chain.proceed(request);
  }

  private String extractClientIp(final HttpRequest<?> request) {
    final String xff = request.getHeaders().get(XFF_HEADER);
    if (xff != null && !xff.isBlank()) {
      final String first = xff.split(",", 2)[0].trim();
      if (!first.isEmpty()) {
        return first;
      }
    }
    if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
      return request.getRemoteAddress().getAddress().getHostAddress();
    }
    return "unknown";
  }

  private String actorIdIfActorEndpoint(final HttpRequest<?> request) {
    final String path = request.getPath();
    final HttpMethod method = request.getMethod();

    final boolean isActorEndpoint =
        (method == HttpMethod.PUT && PUT_ELIGIBILITY.matcher(path).matches())
            || (method == HttpMethod.DELETE && DELETE_CANDIDATE.matcher(path).matches());
    if (!isActorEndpoint) {
      return null;
    }

    final String actorId = request.getHeaders().get(ACTOR_HEADER);
    return (actorId != null && !actorId.isBlank()) ? actorId : null;
  }
}
