package com.example.gateway_service.error;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

/**
 * A request the gateway refuses with a message of its own (e.g. a body over the size limit found while streaming it).
 * {@link GatewayErrorHandler} answers with its status, the matching code and its reason as the message.
 */
public class GatewayRejection extends ResponseStatusException {

	public GatewayRejection(int status, String message) {
		super(HttpStatusCode.valueOf(status), message);
	}

}
