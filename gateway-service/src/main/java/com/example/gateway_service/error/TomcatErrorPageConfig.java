package com.example.gateway_service.error;

import org.apache.catalina.core.StandardHost;
import org.springframework.boot.tomcat.TomcatContextCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Makes {@link JsonErrorReportValve} the error page Tomcat uses for the requests it rejects before Spring runs. */
@Configuration
class TomcatErrorPageConfig {

	@Bean
	TomcatContextCustomizer jsonErrorReportValve() {
		return context -> {
			if (context.getParent() instanceof StandardHost host) {
				host.setErrorReportValveClass(JsonErrorReportValve.class.getName());
			}
		};
	}

}
