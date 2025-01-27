package com.example.cryptoexchange.service;

import com.example.cryptoexchange.client.CryptoRatesClient;
import com.example.cryptoexchange.dto.*;
import com.example.cryptoexchange.exception.CurrencyNotFoundException;
import com.example.cryptoexchange.exception.ExchangeCalculationException;
import com.example.cryptoexchange.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service responsible for fetching, caching, and processing cryptocurrency exchange rates.
 * It retrieves exchange rates from an external API, caches them in Redis, and provides
 * currency conversion calculations.
 */
@Service
@RequiredArgsConstructor
public class CryptoExchangeService {

    private static final Logger logger = LoggerFactory.getLogger(CryptoExchangeService.class);
    private static final String CACHE_KEY = "cryptoRates";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final int EXCHANGE_SCALE = 8;
    private static final BigDecimal FEE_RATE = BigDecimal.valueOf(0.01);

    private final CryptoRatesClient cryptoRatesClient;
    private final RedisTemplate<String, CryptoRatesResponse> redisTemplate;

    /**
     * Scheduled task to refresh cryptocurrency exchange rates every 5 minutes.
     */
    @Scheduled(fixedRate = 300_000)
    public void scheduledCryptoRatesUpdate() {
        logger.info("Scheduled update: Refreshing crypto rates...");
        refreshCryptoRates();
    }

    /**
     * Retrieves cryptocurrency exchange rates asynchronously.
     *
     * @param currency The base currency (case-insensitive).
     * @param filter   A set of target currencies to filter results. If empty, returns all available rates.
     * @return A {@link CompletableFuture} containing {@link CurrencyRatesResponse}.
     */
    public CompletableFuture<CurrencyRatesResponse> getCryptoRatesAsync(String currency, Set<String> filter) {
        return CompletableFuture.supplyAsync(() -> {
            CryptoRatesResponse cachedRates = redisTemplate.opsForValue().get(CACHE_KEY);
            if (cachedRates != null) {
                logger.info("Returning cached crypto rates...");
                return buildCurrencyRatesResponse(currency, cachedRates, filter);
            }
            logger.info("Cache expired or not found. Fetching new rates...");
            CryptoRatesResponse refreshedRates = refreshCryptoRates();
            return buildCurrencyRatesResponse(currency, refreshedRates, filter);
        });
    }

    /**
     * Fetches exchange rates from an external API and stores them in Redis.
     *
     * @return The latest {@link CryptoRatesResponse}.
     */
    public CryptoRatesResponse refreshCryptoRates() {
        try {
            CryptoRatesResponse response = cryptoRatesClient.getRates();
            validateApiResponse(response);

            Map<String, RateInfo> normalizedRates = response.rates()
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            entry -> entry.getKey().toLowerCase(),
                            Map.Entry::getValue
                    ));

            CryptoRatesResponse normalizedResponse = new CryptoRatesResponse(normalizedRates);
            redisTemplate.opsForValue().set(CACHE_KEY, normalizedResponse, CACHE_TTL);
            return normalizedResponse;
        } catch (Exception e) {
            logger.error("Error fetching crypto rates from external API.", e);
            throw new ExternalApiException("Error fetching crypto rates: " + e.getMessage(), e);
        }
    }

    /**
     * Calculates the exchange rate conversion asynchronously.
     *
     * @param request The exchange request containing the source currency, target currencies, and amount.
     * @return A {@link CompletableFuture} containing the {@link ExchangeResponse}.
     */
    public CompletableFuture<ExchangeResponse> calculateExchangeAsync(ExchangeRequest request) {
        return getCryptoRatesAsync(request.from(), request.to())
                .thenApply(rates -> calculateExchange(request, rates));
    }

    /**
     * Validates API response from the external service.
     *
     * @param response the {@link CryptoRatesResponse} to validate.
     */
    private void validateApiResponse(CryptoRatesResponse response) {
        if (response == null || response.rates() == null || response.rates().isEmpty()) {
            throw new ExternalApiException("Received empty or null response from external API.");
        }
    }

    /**
     * Builds a {@link CurrencyRatesResponse} after validating the base currency
     * and filtering/transforming rates accordingly.
     *
     * @param currency the base currency to use.
     * @param response the complete {@link CryptoRatesResponse}.
     * @param filter   optional set of currencies to filter.
     * @return a {@link CurrencyRatesResponse} for the given currency and filter.
     */
    private CurrencyRatesResponse buildCurrencyRatesResponse(String currency,
                                                             CryptoRatesResponse response,
                                                             Set<String> filter) {
        Map<String, RateInfo> rates = response.rates();
        String normalizedBaseCurrency = findNormalizedCurrencyOrThrow(currency, rates);
        BigDecimal baseValue = rates.get(normalizedBaseCurrency).value();

        if (baseValue.compareTo(BigDecimal.ZERO) == 0) {
            throw new ExchangeCalculationException("Base currency value is zero, cannot convert.");
        }

        if (filter == null || filter.isEmpty()) {
            Map<String, BigDecimal> allConverted = convertRates(normalizedBaseCurrency, baseValue, rates);
            allConverted.remove(normalizedBaseCurrency);
            return new CurrencyRatesResponse(currency.toUpperCase(), allConverted);
        }

        validateFilterCurrencies(filter, rates);

        Map<String, BigDecimal> filtered = convertRates(normalizedBaseCurrency, baseValue, rates)
                .entrySet()
                .stream()
                .filter(entry -> filter.stream().anyMatch(f -> f.equalsIgnoreCase(entry.getKey())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return new CurrencyRatesResponse(currency.toUpperCase(), filtered);
    }

    /**
     * Finds the normalized (lowercase) currency in the given map or throws if not found.
     *
     * @param currency the currency symbol (case-insensitive).
     * @param rates    the map of available currency rates.
     * @return the lowercase key from the rates map if found.
     */
    private String findNormalizedCurrencyOrThrow(String currency, Map<String, RateInfo> rates) {
        return rates.keySet().stream()
                .filter(key -> key.equalsIgnoreCase(currency))
                .findFirst()
                .orElseThrow(() -> new CurrencyNotFoundException(currency));
    }

    /**
     * Converts all rates by dividing them by the base value (excluding the base currency itself).
     *
     * @param normalizedBaseCurrency base currency key in lowercase.
     * @param baseValue              base currency value.
     * @param rates                  map of all available rates.
     * @return map of currency -> converted rate (BigDecimal).
     */
    private Map<String, BigDecimal> convertRates(String normalizedBaseCurrency,
                                                 BigDecimal baseValue,
                                                 Map<String, RateInfo> rates) {
        return rates.entrySet()
                .stream()
                .filter(entry -> !entry.getKey().equalsIgnoreCase(normalizedBaseCurrency))
                .collect(Collectors.toMap(
                        entry -> entry.getKey().toUpperCase(),
                        entry -> entry.getValue()
                                .value()
                                .divide(baseValue, EXCHANGE_SCALE, RoundingMode.HALF_UP)
                ));
    }

    /**
     * Validates that all currencies in the filter exist in the provided rates map.
     *
     * @param filter set of target currencies (case-insensitive).
     * @param rates  map of available currency rates.
     */
    private void validateFilterCurrencies(Set<String> filter, Map<String, RateInfo> rates) {
        Set<String> invalidFilters = filter.stream()
                .filter(f -> rates.keySet().stream().noneMatch(key -> key.equalsIgnoreCase(f)))
                .collect(Collectors.toSet());
        if (!invalidFilters.isEmpty()) {
            throw new CurrencyNotFoundException(invalidFilters);
        }
    }

    /**
     * Performs the exchange calculation based on provided rates.
     *
     * @param request The exchange request details.
     * @param rates   The available exchange rates.
     * @return The calculated {@link ExchangeResponse}.
     */
    private ExchangeResponse calculateExchange(ExchangeRequest request, CurrencyRatesResponse rates) {
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ExchangeCalculationException("Amount must be greater than zero.");
        }

        Map<String, BigDecimal> rateMap = rates.rates();
        validateTargetCurrencies(request.to(), rateMap);

        Map<String, ExchangeResult> conversions = request.to().stream()
                .collect(Collectors.toMap(
                        String::toUpperCase,
                        currency -> calculateConversion(rateMap, request.amount(), currency)
                ));

        return new ExchangeResponse(request.from(), conversions);
    }

    /**
     * Validates that all target currencies from the request exist in the given rates map.
     *
     * @param targets set of target currency symbols.
     * @param rateMap map of currency -> rate.
     */
    private void validateTargetCurrencies(Set<String> targets, Map<String, BigDecimal> rateMap) {
        Set<String> invalidTargets = targets.stream()
                .filter(currency -> rateMap.keySet().stream()
                        .noneMatch(key -> key.equalsIgnoreCase(currency))
                )
                .collect(Collectors.toSet());
        if (!invalidTargets.isEmpty()) {
            throw new CurrencyNotFoundException(invalidTargets);
        }
    }

    /**
     * Converts an amount based on the provided exchange rate.
     *
     * @param rates    The map of exchange rates.
     * @param amount   The amount to be converted.
     * @param currency The target currency symbol.
     * @return The calculated {@link ExchangeResult}.
     */
    private ExchangeResult calculateConversion(Map<String, BigDecimal> rates, BigDecimal amount, String currency) {
        String normalizedCurrency = rates.keySet()
                .stream()
                .filter(key -> key.equalsIgnoreCase(currency))
                .findFirst()
                .orElseThrow(() -> new ExchangeCalculationException("Invalid exchange rate for currency: " + currency));

        BigDecimal rate = rates.get(normalizedCurrency);
        BigDecimal rawResult = amount.multiply(rate);
        BigDecimal fee = rawResult.multiply(FEE_RATE);
        BigDecimal finalAmount = rawResult.subtract(fee);

        return new ExchangeResult(rate, amount, finalAmount, fee);
    }
}
