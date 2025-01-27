package com.example.cryptoexchange.exception;

public class ExchangeCalculationException extends RuntimeException {
    public ExchangeCalculationException(String message) {
        super("Exchange calculation error: " + message);
    }
}
