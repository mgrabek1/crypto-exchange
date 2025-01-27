package com.example.cryptoexchange.dto;

import java.math.BigDecimal;

public record ExchangeResult(
        BigDecimal rate,
        BigDecimal amount,
        BigDecimal result,
        BigDecimal fee
) {}
