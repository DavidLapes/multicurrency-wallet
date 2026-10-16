package com.example.wallet.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param cron      when to refresh rates in the background, in the {@code exchange-rates.time-zone};
 *                  {@code -} disables the schedule
 * @param onStartup whether to load rates once when the application starts
 */
@Validated
@ConfigurationProperties("exchange-rates.refresh")
public record ExchangeRateRefreshProperties(
        @NotBlank String cron,
        boolean onStartup
) {
}
