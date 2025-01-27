package com.example.cryptoexchange.controller;

import com.example.cryptoexchange.dto.*;
import com.example.cryptoexchange.service.CryptoExchangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Set;

@RestController
@RequestMapping("/currencies")
@RequiredArgsConstructor
public class CryptoExchangeController {

    private static final Logger logger = LoggerFactory.getLogger(CryptoExchangeController.class);
    private final CryptoExchangeService exchangeService;

    @Operation(summary = "Get cryptocurrency rates", description = "Fetches exchange rates for a specified cryptocurrency.")
    @ApiResponse(responseCode = "200", description = "Successful retrieval of rates",
            content = @Content(schema = @Schema(implementation = CryptoRatesResponse.class)))
    @GetMapping("/{currency}")
    public Mono<CurrencyRatesResponse> getCryptoRates(
            @PathVariable
            @Parameter(
                    name = "currency",
                    description = "Currency code (e.g. btc, eth)",
                    example = "BTC",
                    required = true
            )
            String currency,
            @RequestParam(required = false)
            @Parameter(
                    name = "filter",
                    description = "Comma-separated list of currencies to filter",
                    example = "USD,ETH",
                    schema = @Schema(type = "array")
            )
            Set<String> filter
    ) {
        logger.info("Fetching rates for currency: {}", currency);
        return Mono.fromFuture(exchangeService.getCryptoRatesAsync(currency, filter));
    }

    @Operation(summary = "Exchange cryptocurrency", description = "Calculates a cryptocurrency exchange forecast.")
    @ApiResponse(responseCode = "200", description = "Successful exchange calculation",
            content = @Content(schema = @Schema(implementation = ExchangeResponse.class)))
    @PostMapping("/exchange")
    public Mono<ExchangeResponse> exchange(@RequestBody ExchangeRequest request) {
        logger.info("Processing exchange request: {}", request);
        return Mono.fromFuture(exchangeService.calculateExchangeAsync(request));
    }
}
