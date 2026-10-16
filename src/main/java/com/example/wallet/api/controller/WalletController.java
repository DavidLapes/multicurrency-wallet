package com.example.wallet.api.controller;

import com.example.wallet.api.dto.BalanceResponse;
import com.example.wallet.api.dto.DepositRequest;
import com.example.wallet.api.dto.TotalBalanceResponse;
import com.example.wallet.api.dto.WithdrawalRequest;
import com.example.wallet.service.WalletService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wallet")
@Tag(name = "Wallet", description = "Deposits, withdrawals and balances of the account")
@ApiResponse(responseCode = "400", description = "Invalid input (VALIDATION_ERROR, INVALID_AMOUNT) or malformed JSON",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "422", description = "Currency not supported (UNSUPPORTED_CURRENCY)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "503", description = "Exchange rates unavailable (EXCHANGE_RATES_UNAVAILABLE)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/deposits")
    @Operation(summary = "Deposit money", description = "Adds the amount to the balance of that currency.")
    @ApiResponse(responseCode = "200", description = "The new balance of the deposited currency")
    public BalanceResponse deposit(@Valid @RequestBody DepositRequest request) {
        return BalanceResponse.from(walletService.deposit(request.currency(), request.amount()));
    }

    @PostMapping("/withdrawals")
    @Operation(summary = "Withdraw money",
            description = "Takes the amount from the balance of that currency only; other currencies are never converted to cover it.")
    @ApiResponse(responseCode = "200", description = "The new balance of the withdrawn currency")
    @ApiResponse(responseCode = "422", description = "Balance of the currency is too low (INSUFFICIENT_FUNDS) or currency not supported",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public BalanceResponse withdraw(@Valid @RequestBody WithdrawalRequest request) {
        return BalanceResponse.from(walletService.withdraw(request.currency(), request.amount()));
    }

    @GetMapping("/balances")
    @Operation(summary = "Balance of each currency", description = "Unconverted balances of every currency ever deposited.")
    public List<BalanceResponse> balances() {
        return walletService.balances().stream().map(BalanceResponse::from).toList();
    }

    @GetMapping("/total")
    @Operation(summary = "Total balance in one currency",
            description = "Converts all balances to the requested currency using the latest CNB fixing.")
    public TotalBalanceResponse total(
            @Parameter(description = "ISO 4217 code of the target currency", example = "EUR")
            @RequestParam String currency) {
        return TotalBalanceResponse.from(walletService.total(currency));
    }
}
