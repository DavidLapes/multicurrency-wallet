package com.example.wallet.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static com.example.wallet.observability.RequestLoggingFilter.REQUEST_ID_HEADER;
import static com.example.wallet.observability.RequestLoggingFilter.REQUEST_ID_MDC_KEY;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final AtomicReference<String> requestIdSeenInMdc = new AtomicReference<>();
    private final FilterChain chain = (req, res) -> requestIdSeenInMdc.set(MDC.get(REQUEST_ID_MDC_KEY));

    @Test
    void generatesARequestIdAndReturnsIt() throws Exception {
        filter.doFilter(apiRequest(), response, chain);

        assertThat(response.getHeader(REQUEST_ID_HEADER)).matches(UUID_PATTERN);
    }

    @Test
    void reusesAValidRequestIdFromTheClient() throws Exception {
        MockHttpServletRequest request = apiRequest();
        request.addHeader(REQUEST_ID_HEADER, "client-id-123");

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(REQUEST_ID_HEADER)).isEqualTo("client-id-123");
    }

    @Test
    void replacesAnUnsafeRequestIdFromTheClient() throws Exception {
        MockHttpServletRequest request = apiRequest();
        request.addHeader(REQUEST_ID_HEADER, "evil\nfake log line");

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(REQUEST_ID_HEADER)).matches(UUID_PATTERN);
    }

    @Test
    void requestIdIsInTheMdcDuringTheRequestAndRemovedAfter() throws Exception {
        filter.doFilter(apiRequest(), response, chain);

        assertThat(requestIdSeenInMdc.get()).isEqualTo(response.getHeader(REQUEST_ID_HEADER));
        assertThat(MDC.get(REQUEST_ID_MDC_KEY)).isNull();
    }

    @Test
    void logsOneLinePerRequest(CapturedOutput output) throws Exception {
        filter.doFilter(apiRequest(), response, chain);

        assertThat(output).containsOnlyOnce("HTTP request");
    }

    @Test
    void skipsHealthAndSwaggerRequests(CapturedOutput output) throws Exception {
        for (String path : new String[] {"/actuator/health", "/swagger-ui/index.html", "/v3/api-docs"}) {
            filter.doFilter(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), chain);
        }

        assertThat(output).doesNotContain("HTTP request");
        assertThat(requestIdSeenInMdc.get()).isNull();
    }

    private static MockHttpServletRequest apiRequest() {
        return new MockHttpServletRequest("POST", "/api/v1/wallet/deposits");
    }
}
