package com.example.gateway_service.error;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import com.example.gateway_service.web.RequestIds;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.coyote.ActionCode;

/**
 * Tomcat's error page for requests it rejects itself, before Spring sees them (e.g. an encoded {@code /} or
 * {@code \} in the path): the API §3.3 envelope instead of Tomcat's HTML. Errors the application handles never get here,
 * because {@link GatewayErrorController} has already written the body.
 * <p>
 * Installed as the host's error report valve by {@link TomcatErrorPageConfig}; Tomcat creates it by class name, so it
 * needs a public no-argument constructor.
 */
public class JsonErrorReportValve extends ErrorReportValve {

	@Override
	protected void report(Request request, Response response, Throwable throwable) {
		int status = response.getStatus();
		if (status < 400 || response.getContentWritten() > 0) {
			return;
		}
		AtomicBoolean ioAllowed = new AtomicBoolean(true);
		response.getCoyoteResponse().action(ActionCode.IS_IO_ALLOWED, ioAllowed);
		if (!ioAllowed.get()) {
			return;
		}
		ErrorCode code = ErrorCode.forStatus(status);
		String requestId = RequestIds.current(request);
		// No user input except a request id already limited to [A-Za-z0-9._-], so no JSON escaping is needed.
		String body = "{\"error\":{\"code\":\"" + code.name() + "\",\"message\":\"" + code.defaultMessage()
				+ "\",\"details\":[],\"requestId\":" + (requestId == null ? "null" : "\"" + requestId + "\"") + "}}";
		try {
			response.setContentType("application/json");
			response.setCharacterEncoding(StandardCharsets.UTF_8.name());
			Writer writer = response.getReporter();
			if (writer != null) {
				writer.write(body);
				response.finishResponse();
			}
		}
		catch (IOException | IllegalStateException ignored) {
			// The connection is gone or the response was committed meanwhile; nothing more can be sent.
		}
	}

}
