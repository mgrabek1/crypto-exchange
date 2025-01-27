package com.example.cryptoexchange.dto;

import java.math.BigDecimal;

public record RateInfo(
        String name,
        String unit,
        BigDecimal value,
        String type
) {}