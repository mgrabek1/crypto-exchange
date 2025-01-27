package com.example.cryptoexchange.exception;

import java.util.Set;

public class CurrencyNotFoundException extends RuntimeException {
    public CurrencyNotFoundException(String currency) {
        super("Currency " + currency + " not found in exchange rates.");
    }

    public CurrencyNotFoundException(Set<String> invalidCurrencies) {
        super("Currencies not found in exchange rates: " + String.join(", ", invalidCurrencies));
    }
}
