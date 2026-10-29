package com.example.identity_service.common;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.common.web.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer slice: no database or Eureka. {@code @WebMvcTest} picks up {@code RequestIdFilter} and
 * {@code GlobalExceptionHandler} itself; the test-only controller is nested, so it is imported explicitly.
 */
@WebMvcTest(controllers = WebContractTests.ContractController.class)
@Import(WebContractTests.ContractController.class)
class WebContractTests {

	@Autowired
	MockMvc mvc;

	@Test
	void singleResourceIsWrappedInDataWithoutMeta() throws Exception {
		mvc.perform(get("/test/item"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("item"))
				.andExpect(jsonPath("$.meta").doesNotExist());
	}

	@Test
	void pageUsesOneBasedPageNumbers() throws Exception {
		mvc.perform(get("/test/page").param("page", "2").param("size", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(5)))
				.andExpect(jsonPath("$.data[0]").value(5))
				.andExpect(jsonPath("$.meta.page").value(2))
				.andExpect(jsonPath("$.meta.size").value(5))
				.andExpect(jsonPath("$.meta.totalItems").value(12))
				.andExpect(jsonPath("$.meta.totalPages").value(3));
	}

	@Test
	void pageDefaultsToFirstPageOfTwenty() throws Exception {
		mvc.perform(get("/test/page"))
				.andExpect(jsonPath("$.meta.page").value(1))
				.andExpect(jsonPath("$.meta.size").value(20));
	}

	@Test
	void pageSizeIsCappedAtOneHundred() throws Exception {
		mvc.perform(get("/test/page").param("size", "500"))
				.andExpect(jsonPath("$.meta.size").value(100));
	}

	@Test
	void beanValidationFailureListsEachField() throws Exception {
		mvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\": \"\", \"name\": \"too long\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error.details", hasSize(2)))
				.andExpect(jsonPath("$.error.details[?(@.field == 'email')].issue").value("NOT_BLANK"))
				.andExpect(jsonPath("$.error.details[?(@.field == 'name')].issue").value("SIZE"));
	}

	@Test
	void malformedJsonIsAValidationError() throws Exception {
		mvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{not json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error.details").isArray());
	}

	@Test
	void apiExceptionKeepsCodeMessageAndDetails() throws Exception {
		mvc.perform(get("/test/conflict"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"))
				.andExpect(jsonPath("$.error.message").value("Seller application is pending review"))
				.andExpect(jsonPath("$.error.details[0].currentStatus").value("PENDING"));
	}

	@Test
	void unknownPathIsNotFoundEnvelope() throws Exception {
		mvc.perform(get("/test/does-not-exist"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void wrongMethodKeepsItsStatus() throws Exception {
		mvc.perform(post("/test/item"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void unexpectedExceptionDoesNotLeakItsMessage() throws Exception {
		mvc.perform(get("/test/boom"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.error.message").value(ErrorCode.INTERNAL_ERROR.defaultMessage()));
	}

	@Test
	void incomingRequestIdIsEchoedAndUsedInErrors() throws Exception {
		mvc.perform(get("/test/conflict").header("X-Request-Id", "req-123"))
				.andExpect(header().string("X-Request-Id", "req-123"))
				.andExpect(jsonPath("$.error.requestId").value("req-123"));
	}

	@Test
	void missingRequestIdIsNotInvented() throws Exception {
		mvc.perform(get("/test/conflict"))
				.andExpect(header().doesNotExist("X-Request-Id"))
				.andExpect(jsonPath("$.error.requestId").value(nullValue()));
	}

	@Test
	void unsafeRequestIdIsIgnored() throws Exception {
		mvc.perform(get("/test/conflict").header("X-Request-Id", "bad id\r\nX-Injected: 1"))
				.andExpect(header().doesNotExist("X-Request-Id"))
				.andExpect(jsonPath("$.error.requestId").value(nullValue()));
	}

	@RestController
	static class ContractController {

		record Body(@NotBlank String email, @Size(max = 5) String name) {
		}

		@GetMapping("/test/item")
		ApiResponse<Map<String, String>> item() {
			return ApiResponse.of(Map.of("name", "item"));
		}

		@GetMapping("/test/page")
		ApiResponse<List<Integer>> page(Pageable pageable) {
			List<Integer> all = java.util.stream.IntStream.range(0, 12).boxed().toList();
			int from = (int) Math.min(pageable.getOffset(), all.size());
			int to = Math.min(from + pageable.getPageSize(), all.size());
			return ApiResponse.page(new PageImpl<>(all.subList(from, to), pageable, all.size()));
		}

		@PostMapping("/test/validate")
		ApiResponse<Body> validate(@Valid @RequestBody Body body) {
			return ApiResponse.of(body);
		}

		@GetMapping("/test/conflict")
		ApiResponse<Void> conflict() {
			throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "Seller application is pending review",
					List.of(Map.of("currentStatus", "PENDING")));
		}

		@GetMapping("/test/boom")
		ApiResponse<Void> boom() {
			throw new IllegalStateException("database password is hunter2");
		}

	}

}
