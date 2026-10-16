package com.example.wallet.api.error;

import com.example.wallet.domain.exception.InsufficientFundsException;
import com.example.wallet.domain.exception.InvalidAmountException;
import com.example.wallet.domain.exception.UnsupportedCurrencyException;
import com.example.wallet.service.ExchangeRatesUnavailableException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String CODE = "code";

    @ExceptionHandler(InsufficientFundsException.class)
    ProblemDetail handleInsufficientFunds(InsufficientFundsException e) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.INSUFFICIENT_FUNDS, e.getMessage());
        problem.setProperty("currency", e.currency());
        problem.setProperty("available", e.available().toPlainString());
        problem.setProperty("requested", e.requested().toPlainString());
        return problem;
    }

    @ExceptionHandler(UnsupportedCurrencyException.class)
    // TODO: Could become huge response
    ProblemDetail handleUnsupportedCurrency(UnsupportedCurrencyException e) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.UNSUPPORTED_CURRENCY, e.getMessage());
        problem.setProperty("supportedCurrencies", e.supportedCurrencies().stream().sorted().toList());
        return problem;
    }

    @ExceptionHandler(InvalidAmountException.class)
    ProblemDetail handleInvalidAmount(InvalidAmountException e) {
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_AMOUNT, e.getMessage());
    }

    @ExceptionHandler(ExchangeRatesUnavailableException.class)
    ProblemDetail handleRatesUnavailable(ExchangeRatesUnavailableException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.EXCHANGE_RATES_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        log.atError().setCause(e).log("Unexpected error");
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new TreeMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.merge(error.getField(), String.valueOf(error.getDefaultMessage()), (a, b) -> a + "; " + b));

        ProblemDetail problem = problem(status, ErrorCode.VALIDATION_ERROR, "Request validation failed");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatusCode status, ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE, code);
        return problem;
    }
}
