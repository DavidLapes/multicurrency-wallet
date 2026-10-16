package com.example.wallet.service;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * @param timeZone        time zone of the rate source's publication schedule
 * @param publicationTime time of day a new fixing is published on weekdays
 * @param retryInterval   minimum time between calls while a newer fixing is expected but not yet available
 */
@Validated
@ConfigurationProperties("exchange-rates")
public record ExchangeRateProperties(
        @NotNull ZoneId timeZone,
        @NotNull LocalTime publicationTime,
        @NotNull Duration retryInterval
) {
}
