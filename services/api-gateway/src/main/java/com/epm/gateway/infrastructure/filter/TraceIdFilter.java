package com.epm.gateway.infrastructure.filter;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Propagates a trace ID ({@value #TRACE_ID_HEADER}) on every request/response.
 *
 * <p>If the incoming request already carries the header, the same value is forwarded
 * downstream. Otherwise a new UUID is generated. The same ID is echoed back on the
 * response via {@link ServerHttpResponse#beforeCommit}, which runs just before headers
 * are written to the wire — safe for streaming and SSE responses.
 *
 * <p>The previous implementation used a {@code ServerHttpResponseDecorator} that
 * overrode {@code getHeaders()} and mutated the header map there. In WebFlux,
 * {@code getHeaders()} can be called after the response is already committed (e.g.
 * for flush events in streaming), so the mutation had no effect and the
 * {@code X-Request-ID} header was silently dropped on SSE / chunked responses.
 */
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);
    private static final String TRACE_ID_HEADER = "X-Request-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        final String finalTraceId = traceId;
        log.debug("Request: method={} path={} traceId={}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath(),
                finalTraceId);

        // Propagate the trace ID downstream on the request.
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(TRACE_ID_HEADER, finalTraceId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        // Echo the trace ID on the response using beforeCommit() — this hook runs
        // immediately before headers are flushed to the client, so it works correctly
        // for normal responses, streaming (SSE), and chunked transfers alike.
        mutatedExchange.getResponse().beforeCommit(() -> {
            mutatedExchange.getResponse().getHeaders()
                    .addIfAbsent(TRACE_ID_HEADER, finalTraceId);
            return Mono.empty();
        });

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
