package com.example.wallet;

import com.example.wallet.domain.ExchangeRates;
import com.example.wallet.service.ExchangeRateProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "exchange-rates.refresh.on-startup=false",
        "exchange-rates.refresh.cron=-"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WalletApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ExchangeRateProvider exchangeRateProvider;

    @BeforeEach
    void cnbRates() {
        when(exchangeRateProvider.fetchLatest()).thenReturn(new ExchangeRates(LocalDate.of(2026, 10, 13), Map.of(
                "USD", new BigDecimal("22.00"),
                "EUR", new BigDecimal("25.52"))));
    }

    @Test
    void assignmentExampleWorkflow() throws Exception {
        move("deposits", "USD", "10").andExpect(jsonPath("$.amount").value("10.00"));
        move("deposits", "EUR", "5").andExpect(jsonPath("$.amount").value("5.00"));
        move("withdrawals", "USD", "3").andExpect(jsonPath("$.amount").value("7.00"));

        total("USD").andExpect(jsonPath("$.amount").value("12.80"));
        total("EUR").andExpect(jsonPath("$.amount").value("11.03"));
    }

    @Test
    void failedWithdrawalLeavesTheBalanceUnchanged() throws Exception {
        move("deposits", "USD", "10");

        move("withdrawals", "USD", "10.01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        mvc.perform(get("/api/v1/wallet/balances"))
                .andExpect(jsonPath("$[0].amount").value("10.00"));
    }

    @Test
    void lowercaseTotalCurrencyIsUnsupportedAndListsTheValidCodes() throws Exception {
        mvc.perform(get("/api/v1/wallet/total").param("currency", "usd"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"))
                .andExpect(jsonPath("$.supportedCurrencies").isArray());
    }

    @Test
    void publishesTheOpenApiDescriptionAndHealth() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/wallet/deposits']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/wallet/total']").exists())
                .andExpect(jsonPath("$.components.schemas.DepositRequest").exists())
                .andExpect(jsonPath("$.components.schemas.WithdrawalRequest").exists());
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void apiResponsesCarryARequestId() throws Exception {
        mvc.perform(get("/api/v1/wallet/balances").header("X-Request-Id", "trace-me-42"))
                .andExpect(header().string("X-Request-Id", "trace-me-42"));
    }

    @Test
    void healthStaysUpWhileNoRatesAreLoadedAndReportsThemOnceLoaded() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.exchangeRates.status").value("UNKNOWN"));

        total("USD");

        mvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.components.exchangeRates.status").value("UP"))
                .andExpect(jsonPath("$.components.exchangeRates.details.validFor").value("2026-10-13"))
                .andExpect(jsonPath("$.components.exchangeRates.details.stale").value(false));
    }

    private ResultActions move(String type, String currency, String amount) throws Exception {
        return mvc.perform(post("/api/v1/wallet/" + type)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currency": "%s", "amount": "%s"}""".formatted(currency, amount)));
    }

    private ResultActions total(String currency) throws Exception {
        return mvc.perform(get("/api/v1/wallet/total").param("currency", currency))
                .andExpect(status().isOk());
    }
}
