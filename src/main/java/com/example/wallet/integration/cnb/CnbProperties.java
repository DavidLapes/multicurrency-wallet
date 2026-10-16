package com.example.wallet.integration.cnb;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties("cnb")
public record CnbProperties(@NotNull URI baseUrl) {
}
