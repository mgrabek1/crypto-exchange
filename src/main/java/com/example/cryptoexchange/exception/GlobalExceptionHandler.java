package com.example.cryptoexchange.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CurrencyNotFoundException.class)
    public ErrorMessage handleCurrencyNotFound(CurrencyNotFoundException ex) {
        return new ErrorMessage(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "Currency Not Found",
                ex.getMessage()
        );
    }

    @ExceptionHandler(ExchangeCalculationException.class)
    public ErrorMessage handleExchangeCalculation(ExchangeCalculationException ex) {
        return new ErrorMessage(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Exchange Calculation Error",
                ex.getMessage()
        );
    }

    @ExceptionHandler(ExternalApiException.class)
    public ErrorMessage handleExternalApiError(ExternalApiException ex) {
        return new ErrorMessage(
                LocalDateTime.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "External API Error",
                ex.getMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    public ErrorMessage handleGenericError(Exception ex) {
        return new ErrorMessage(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                ex.getMessage()
        );
    }
}
