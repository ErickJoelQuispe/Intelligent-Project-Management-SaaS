package com.epm.gateway.infrastructure.filter;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

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

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
            .header(TRACE_ID_HEADER, finalTraceId)
            .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
            .request(mutatedRequest)
            .response(new org.springframework.http.server.reactive.ServerHttpResponseDecorator(
                    exchange.getResponse()) {
                @Override
                public org.springframework.http.HttpHeaders getHeaders() {
                    org.springframework.http.HttpHeaders headers = super.getHeaders();
                    if (!headers.containsKey(TRACE_ID_HEADER)) {
                        headers.add(TRACE_ID_HEADER, finalTraceId);
                    }
                    return headers;
                }
            })
            .build();

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
