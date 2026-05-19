package com.thetealover.candidate.api.filter;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.slf4j.MDC;

@Filter("/api/**")
@Slf4j(topic = "http")
public class RequestContextFilter implements HttpServerFilter {

  /** Run before any other filter so MDC + correlationId are set up first. */
  public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

  @Override
  public int getOrder() {
    return ORDER;
  }

  private static final String CORRELATION_HEADER = "X-Correlation-Id";
  private static final String ACTOR_HEADER = "X-Actor-Id";

  /**
   * Request attribute key for the resolved correlationId. Stashed here so downstream code
   * (exception handlers in particular) can read it without depending on MDC, which doesn't
   * propagate across the Netty IO → blocking executor thread switch.
   */
  public static final CharSequence CORRELATION_ID_ATTR = "correlationId";

  @Override
  public Publisher<MutableHttpResponse<?>> doFilter(
      final HttpRequest<?> request, final ServerFilterChain chain) {

    final long started = System.nanoTime();
    final String correlationId =
        request.getHeaders().get(CORRELATION_HEADER) != null
            ? request.getHeaders().get(CORRELATION_HEADER)
            : UUID.randomUUID().toString();
    final String actorId = request.getHeaders().get(ACTOR_HEADER);

    request.setAttribute(CORRELATION_ID_ATTR, correlationId);
    MDC.put("correlationId", correlationId);
    if (actorId != null) MDC.put("actorId", actorId);

    log.debug(
        "http.request.received method={} path={} query={} actorId={}",
        request.getMethod(),
        request.getPath(),
        request.getUri().getQuery(),
        actorId);

    return Publishers.map(
        chain.proceed(request),
        response -> {
          response.header(CORRELATION_HEADER, correlationId);
          final long durationMs = (System.nanoTime() - started) / 1_000_000;
          log.info(
              "http.request.completed method={} path={} status={} durationMs={}",
              request.getMethod(),
              request.getPath(),
              response.getStatus().getCode(),
              durationMs);
          MDC.remove("correlationId");
          MDC.remove("actorId");
          return response;
        });
  }
}
