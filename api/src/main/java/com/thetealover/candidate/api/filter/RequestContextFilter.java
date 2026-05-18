package com.thetealover.candidate.api.filter;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import java.util.UUID;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Filter("/api/**")
public class RequestContextFilter implements HttpServerFilter {

  private static final Logger LOG = LoggerFactory.getLogger("http");

  private static final String CORRELATION_HEADER = "X-Correlation-Id";
  private static final String ACTOR_HEADER = "X-Actor-Id";

  @Override
  public Publisher<MutableHttpResponse<?>> doFilter(
      final HttpRequest<?> request, final ServerFilterChain chain) {

    final long started = System.nanoTime();
    final String correlationId =
        request.getHeaders().get(CORRELATION_HEADER) != null
            ? request.getHeaders().get(CORRELATION_HEADER)
            : UUID.randomUUID().toString();
    final String actorId = request.getHeaders().get(ACTOR_HEADER);

    MDC.put("correlationId", correlationId);
    if (actorId != null) MDC.put("actorId", actorId);

    LOG.debug(
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
          LOG.info(
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
