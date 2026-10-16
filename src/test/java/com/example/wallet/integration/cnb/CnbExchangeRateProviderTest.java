package com.example.wallet.integration.cnb;

import com.example.wallet.domain.ExchangeRates;
import com.example.wallet.service.ExchangeRateProviderException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** HTTP and JSON handling against a mocked CNB server. */
class CnbExchangeRateProviderTest {

    private static final String DAILY_RATES_URL = "https://api.cnb.cz/cnbapi/exrates/daily?lang=EN";

    // Copied from a real response, trimmed to two currencies
    private static final String CNB_RESPONSE = """
            {"rates":[
              {"validFor":"2026-10-13","order":197,"country":"Hungary","currency":"forint","amount":100,"currencyCode":"HUF","rate":6.690},
              {"validFor":"2026-10-13","order":197,"country":"USA","currency":"dollar","amount":1,"currencyCode":"USD","rate":21.788}
            ]}""";

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer cnbServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final CnbExchangeRateProvider provider =
            new CnbExchangeRateProvider(restClientBuilder, new CnbProperties(URI.create("https://api.cnb.cz")));

    @Test
    void fetchesAndParsesTheDailyRates() {
        cnbServer.expect(requestTo(DAILY_RATES_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(CNB_RESPONSE, MediaType.APPLICATION_JSON));

        ExchangeRates rates = provider.fetchLatest();

        assertThat(rates.validFor()).isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(rates.czkPerUnit()).containsOnlyKeys("HUF", "USD");
        assertThat(rates.czkPerUnit().get("HUF")).isEqualByComparingTo("0.0669");
        cnbServer.verify();
    }

    @Test
    void serverErrorIsReportedAsProviderFailure() {
        cnbServer.expect(requestTo(DAILY_RATES_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(provider::fetchLatest).isInstanceOf(ExchangeRateProviderException.class);
    }

    @Test
    void networkErrorIsReportedAsProviderFailure() {
        cnbServer.expect(requestTo(DAILY_RATES_URL)).andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(provider::fetchLatest).isInstanceOf(ExchangeRateProviderException.class);
    }

    @Test
    void malformedJsonIsReportedAsProviderFailure() {
        cnbServer.expect(requestTo(DAILY_RATES_URL)).andRespond(withSuccess("<html>maintenance</html>", MediaType.APPLICATION_JSON));

        assertThatThrownBy(provider::fetchLatest).isInstanceOf(ExchangeRateProviderException.class);
    }
}
