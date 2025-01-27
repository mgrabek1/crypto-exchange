package com.example.cryptoexchange.dto;

import java.math.BigDecimal;
import java.util.Map;

public record CurrencyRatesResponse(
        String source,
        Map<String, BigDecimal> rates
) {}
