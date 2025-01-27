package com.example.cryptoexchange.exception;

public class ExternalApiException extends RuntimeException {

    public ExternalApiException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExternalApiException(String message) {
        super("Error fetching data from external API: " + message);
    }
}
