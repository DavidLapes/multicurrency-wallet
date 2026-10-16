package com.example.wallet.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI walletOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Multicurrency Wallet API")
                .version("v1")
                .description("""
                        A single in-memory multicurrency account. Deposit and withdraw in CZK or any currency \
                        quoted by the Czech National Bank (CNB), and get the total balance in a currency of your \
                        choice, converted with the latest CNB fixing.

                        Money amounts are decimal strings in responses (e.g. "12.80") and may be strings or \
                        numbers in requests. Errors are RFC 9457 problem details with a stable `code` property."""));
    }
}
