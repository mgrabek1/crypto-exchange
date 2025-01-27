package com.example.cryptoexchange.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.Set;

public record ExchangeRequest(
        @NotBlank String from,
        @NotEmpty Set<String> to,
        @Positive BigDecimal amount
) {}
