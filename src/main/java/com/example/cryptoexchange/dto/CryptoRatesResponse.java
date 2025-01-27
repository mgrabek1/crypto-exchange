package com.example.cryptoexchange.dto;

import java.util.Map;

public record CryptoRatesResponse(
        Map<String, RateInfo> rates
) {}