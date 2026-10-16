package com.example.wallet.api.controller;

import com.example.wallet.domain.Balance;
import com.example.wallet.domain.exception.InsufficientFundsException;
import com.example.wallet.domain.exception.InvalidAmountException;
import com.example.wallet.domain.TotalBalance;
import com.example.wallet.domain.exception.UnsupportedCurrencyException;
import com.example.wallet.service.ExchangeRatesUnavailableException;
import com.example.wallet.service.WalletService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web layer only: request mapping, validation, JSON shape and error responses. The service is mocked. */
@WebMvcTest(WalletController.class)
class WalletControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    WalletService walletService;

    @Test
    void depositReturnsTheNewBalanceWithAmountAsString() throws Exception {
        when(walletService.deposit("USD", new BigDecimal("10.5")))
                .thenReturn(new Balance("USD", new BigDecimal("10.50")));

        postJson("/api/v1/wallet/deposits", """
                {"currency": "USD", "amount": 10.5}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.amount").value("10.50"));
    }

    @Test
    void acceptsTheAmountAsAString() throws Exception {
        when(walletService.withdraw("USD", new BigDecimal("3")))
                .thenReturn(new Balance("USD", new BigDecimal("7.00")));

        postJson("/api/v1/wallet/withdrawals", """
                {"currency": "USD", "amount": "3"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("7.00"));
    }

    @Test
    void listsBalances() throws Exception {
        when(walletService.balances()).thenReturn(List.of(
                new Balance("EUR", new BigDecimal("5.00")),
                new Balance("USD", new BigDecimal("7.00"))));

        mvc.perform(get("/api/v1/wallet/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currency").value("EUR"))
                .andExpect(jsonPath("$[1].amount").value("7.00"));
    }

    @Test
    void returnsTheTotalWithTheRatesUsed() throws Exception {
        when(walletService.total("USD"))
                .thenReturn(new TotalBalance("USD", new BigDecimal("12.80"), LocalDate.of(2026, 10, 13), false));

        mvc.perform(get("/api/v1/wallet/total").param("currency", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.amount").value("12.80"))
                .andExpect(jsonPath("$.ratesValidFor").value("2026-10-13"))
                .andExpect(jsonPath("$.ratesStale").value(false));
    }

    @Test
    void invalidBodyFieldsAreReportedPerField() throws Exception {
        postJson("/api/v1/wallet/deposits", """
                {"currency": "usd", "amount": -5}""")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.currency").exists())
                .andExpect(jsonPath("$.errors.amount").exists());
        verifyNoInteractions(walletService);
    }

    @Test
    void missingFieldsAreRejected() throws Exception {
        postJson("/api/v1/wallet/deposits", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currency").exists())
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    void hugeExponentIsRejectedBeforeAnyArithmetic() throws Exception {
        postJson("/api/v1/wallet/deposits", """
                {"currency": "USD", "amount": 1e999999999}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(walletService);
    }

    @Test
    void malformedJsonGetsSpringsStandardProblemResponse() throws Exception {
        postJson("/api/v1/wallet/deposits", "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(walletService);
    }

    @Test
    void missingTotalCurrencyParameterIsRejected() throws Exception {
        mvc.perform(get("/api/v1/wallet/total"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(walletService);
    }

    @Test
    void insufficientFundsIs422WithDetails() throws Exception {
        when(walletService.withdraw(anyString(), any()))
                .thenThrow(new InsufficientFundsException("USD", new BigDecimal("7.00"), new BigDecimal("50.00")));

        postJson("/api/v1/wallet/withdrawals", """
                {"currency": "USD", "amount": 50}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.available").value("7.00"))
                .andExpect(jsonPath("$.requested").value("50.00"));
    }

    @Test
    void unsupportedCurrencyIs422WithTheSupportedList() throws Exception {
        when(walletService.deposit(anyString(), any()))
                .thenThrow(new UnsupportedCurrencyException("GBP", Set.of("USD", "CZK")));

        postJson("/api/v1/wallet/deposits", """
                {"currency": "GBP", "amount": 1}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"))
                .andExpect(jsonPath("$.supportedCurrencies[0]").value("CZK"))
                .andExpect(jsonPath("$.supportedCurrencies[1]").value("USD"));
    }

    @Test
    void tooManyDecimalPlacesIs400() throws Exception {
        when(walletService.deposit(anyString(), any()))
                .thenThrow(new InvalidAmountException("USD amounts can have at most 2 decimal places"));

        postJson("/api/v1/wallet/deposits", """
                {"currency": "USD", "amount": 1.001}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));
    }

    @Test
    void ratesUnavailableIs503() throws Exception {
        when(walletService.total("USD")).thenThrow(new ExchangeRatesUnavailableException("CNB down"));

        mvc.perform(get("/api/v1/wallet/total").param("currency", "USD"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EXCHANGE_RATES_UNAVAILABLE"));
    }

    @Test
    void unexpectedErrorsDoNotLeakDetails() throws Exception {
        when(walletService.balances()).thenThrow(new IllegalStateException("secret internal detail"));

        mvc.perform(get("/api/v1/wallet/balances"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
