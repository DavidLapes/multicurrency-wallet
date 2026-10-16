package com.example.wallet.api.controller;

import com.example.wallet.api.dto.RatesResponse;
import com.example.wallet.service.ExchangeRateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rates")
@Tag(name = "Exchange rates")
public class RatesController {

    private final ExchangeRateService exchangeRateService;

    public RatesController(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    @GetMapping
    @Operation(summary = "Exchange rates in use",
            description = "The CNB fixing currently used for conversions, as CZK per one unit of each currency.")
    public RatesResponse getRates() {
        return RatesResponse.from(exchangeRateService.currentRates());
    }
}
