package com.example.gateway_service.web;

import java.util.concurrent.atomic.AtomicLong;

import com.example.gateway_service.error.ErrorCode;
import com.example.gateway_service.error.ErrorWriter;
import com.example.gateway_service.error.GatewayRejection;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Request bodies over {@code gateway.limits.max-body-size} (1 MB, decision 0002; media goes straight to object
 * storage) are refused with 413 and the {@code VALIDATION_ERROR} code (API §4.1 has no code of its own for it). A
 * declared {@code Content-Length} over the limit is refused before anything is read; a body without one (chunked) is
 * counted while it streams and the request fails as soon as it passes the limit.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
class BodySizeFilter implements WebFilter {

	private final long maxBytes;

	private final String message;

	private final ErrorWriter errors;

	BodySizeFilter(EdgeProperties properties, ErrorWriter errors) {
		this.maxBytes = properties.limits().maxBodySize().toBytes();
		this.message = "The request body must not be larger than " + readable(maxBytes) + ".";
		this.errors = errors;
	}

	static String readable(long bytes) {
		if (bytes > 0 && bytes % (1024 * 1024) == 0) {
			return bytes / (1024 * 1024) + " MB";
		}
		if (bytes > 0 && bytes % 1024 == 0) {
			return bytes / 1024 + " KB";
		}
		return bytes + " bytes";
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		ServerHttpRequest request = exchange.getRequest();
		long declared = request.getHeaders().getContentLength();
		if (declared > maxBytes) {
			return errors.write(exchange, HttpStatus.CONTENT_TOO_LARGE.value(), ErrorCode.VALIDATION_ERROR, message);
		}
		if (declared >= 0) {
			return chain.filter(exchange);
		}
		AtomicLong seen = new AtomicLong();
		ServerHttpRequest counted = new ServerHttpRequestDecorator(request) {
			@Override
			public Flux<DataBuffer> getBody() {
				return super.getBody().handle((buffer, sink) -> {
					if (seen.addAndGet(buffer.readableByteCount()) > maxBytes) {
						DataBufferUtils.release(buffer);
						sink.error(new GatewayRejection(HttpStatus.CONTENT_TOO_LARGE.value(), message));
						return;
					}
					sink.next(buffer);
				});
			}
		};
		return chain.filter(exchange.mutate().request(counted).build());
	}

}
