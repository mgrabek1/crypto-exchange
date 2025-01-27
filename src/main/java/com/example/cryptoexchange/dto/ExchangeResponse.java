package com.example.cryptoexchange.dto;

import java.util.Map;

public record ExchangeResponse(
        String from,
        Map<String, ExchangeResult> conversions
) {}
