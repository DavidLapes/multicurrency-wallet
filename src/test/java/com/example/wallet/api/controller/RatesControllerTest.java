package com.example.wallet.api.controller;

import com.example.wallet.domain.ExchangeRates;
import com.example.wallet.service.ExchangeRateService;
import com.example.wallet.service.ExchangeRatesUnavailableException;
import com.example.wallet.service.RatesSnapshot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web layer only: the service is mocked, no CNB calls. */
@WebMvcTest(RatesController.class)
class RatesControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ExchangeRateService exchangeRateService;

    @Test
    void returnsTheRatesInUse() throws Exception {
        ExchangeRates rates = new ExchangeRates(LocalDate.of(2026, 10, 13), Map.of(
                "USD", new BigDecimal("21.788"),
                "HUF", new BigDecimal("0.0669")));
        when(exchangeRateService.currentRates()).thenReturn(new RatesSnapshot(rates, true, Instant.parse("2026-10-13T13:00:00Z")));

        mvc.perform(get("/api/v1/rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validFor").value("2026-10-13"))
                .andExpect(jsonPath("$.stale").value(true))
                .andExpect(jsonPath("$.czkPerUnit.USD").value(21.788))
                .andExpect(jsonPath("$.czkPerUnit.HUF").value(0.0669));
    }

    @Test
    void returns503WhenRatesAreUnavailable() throws Exception {
        when(exchangeRateService.currentRates()).thenThrow(new ExchangeRatesUnavailableException("CNB down"));

        mvc.perform(get("/api/v1/rates"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EXCHANGE_RATES_UNAVAILABLE"));
    }
}
