package com.example.wallet.integration.cnb;

import com.example.wallet.domain.ExchangeRates;
import com.example.wallet.service.ExchangeRateProvider;
import com.example.wallet.service.ExchangeRateProviderException;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class CnbExchangeRateProvider implements ExchangeRateProvider {

    static final String DAILY_RATES_PATH = "/cnbapi/exrates/daily";

    private final RestClient restClient;

    public CnbExchangeRateProvider(RestClient.Builder restClientBuilder, CnbProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl().toString()).build();
    }

    @Override
    public ExchangeRates fetchLatest() {
        CnbDailyRatesResponse response;
        try {
            response = restClient.get()
                    .uri(uri -> uri.path(DAILY_RATES_PATH).queryParam("lang", "EN").build())
                    .retrieve()
                    .body(CnbDailyRatesResponse.class);
        } catch (RestClientException e) {
            throw new ExchangeRateProviderException("CNB request failed: " + e.getMessage(), e);
        }
        if (response == null) {
            throw new ExchangeRateProviderException("CNB returned an empty body");
        }
        return response.toExchangeRates();
    }
}
