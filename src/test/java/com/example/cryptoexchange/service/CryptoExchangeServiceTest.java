package com.example.cryptoexchange.service;

import com.example.cryptoexchange.client.CryptoRatesClient;
import com.example.cryptoexchange.dto.*;
import com.example.cryptoexchange.exception.CurrencyNotFoundException;
import com.example.cryptoexchange.exception.ExchangeCalculationException;
import com.example.cryptoexchange.exception.ExternalApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CryptoExchangeServiceTest {

    private CryptoRatesClient cryptoRatesClient;
    private RedisTemplate<String, CryptoRatesResponse> redisTemplate;
    private ValueOperations<String, CryptoRatesResponse> valueOperations;
    private CryptoExchangeService cryptoExchangeService;

    @BeforeEach
    void setUp() {
        cryptoRatesClient = mock(CryptoRatesClient.class);
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        cryptoExchangeService = new CryptoExchangeService(
                cryptoRatesClient,
                redisTemplate
        );
    }

    @Test
    void shouldReturnCachedRatesWhenAvailable() throws ExecutionException, InterruptedException {
        Map<String, RateInfo> ratesMap = Map.of(
                "btc", new RateInfo("Bitcoin", "BTC", BigDecimal.valueOf(20000), "crypto"),
                "eth", new RateInfo("Ethereum", "ETH", BigDecimal.valueOf(1500), "crypto"),
                "usd", new RateInfo("US Dollar", "USD", BigDecimal.valueOf(1), "fiat")
        );
        CryptoRatesResponse cachedResponse = new CryptoRatesResponse(ratesMap);

        when(valueOperations.get("cryptoRates")).thenReturn(cachedResponse);

        Set<String> filter = Set.of("eth", "usd");
        var future = cryptoExchangeService.getCryptoRatesAsync("btc", filter);
        CurrencyRatesResponse response = future.get();

        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
        assertThat(response.source()).isEqualTo("BTC");
        assertThat(response.rates()).hasSize(2);
        assertThat(response.rates()).containsKeys("ETH", "USD");
    }

    @Test
    void shouldFetchNewRatesWhenCacheIsEmpty() throws ExecutionException, InterruptedException {
        when(valueOperations.get("cryptoRates")).thenReturn(null);

        Map<String, RateInfo> ratesMap = Map.of(
                "btc", new RateInfo("Bitcoin", "BTC", BigDecimal.valueOf(20000), "crypto"),
                "usd", new RateInfo("USDollar", "USD", BigDecimal.valueOf(1), "fiat")
        );
        CryptoRatesResponse apiResponse = new CryptoRatesResponse(ratesMap);

        when(cryptoRatesClient.getRates()).thenReturn(apiResponse);

        var future = cryptoExchangeService.getCryptoRatesAsync("btc", null);
        CurrencyRatesResponse response = future.get();

        verify(valueOperations).set(eq("cryptoRates"), any(CryptoRatesResponse.class), any(Duration.class));
        assertThat(response.source()).isEqualTo("BTC");
        assertThat(response.rates()).hasSize(1);
        assertThat(response.rates()).containsKey("USD");
        assertThat(response.rates().get("USD")).isEqualByComparingTo("0.00005");
    }

    @Test
    void shouldRefreshCryptoRates() {
        Map<String, RateInfo> ratesMap = Map.of(
                "BTC", new RateInfo("Bitcoin", "BTC", BigDecimal.valueOf(20000), "crypto"),
                "USD", new RateInfo("USDollar", "USD", BigDecimal.valueOf(1), "fiat")
        );
        CryptoRatesResponse apiResponse = new CryptoRatesResponse(ratesMap);
        when(cryptoRatesClient.getRates()).thenReturn(apiResponse);

        CryptoRatesResponse refreshed = cryptoExchangeService.refreshCryptoRates();
        assertThat(refreshed.rates()).containsKeys("btc", "usd");
        verify(redisTemplate.opsForValue()).set(eq("cryptoRates"),
                any(CryptoRatesResponse.class),
                eq(Duration.ofMinutes(5)));
    }

    @Test
    void shouldThrowExternalApiExceptionWhenApiReturnsEmpty() {
        when(cryptoRatesClient.getRates()).thenReturn(new CryptoRatesResponse(Map.of()));

        assertThatThrownBy(() -> cryptoExchangeService.refreshCryptoRates())
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    void shouldCalculateExchangeProperly() throws ExecutionException, InterruptedException {
        Map<String, RateInfo> ratesMap = Map.of(
                "btc", new RateInfo("Bitcoin", "BTC", BigDecimal.valueOf(20000), "crypto"),
                "usd", new RateInfo("USDollar", "USD", BigDecimal.valueOf(1), "fiat")
        );
        CryptoRatesResponse cachedResponse = new CryptoRatesResponse(ratesMap);
        when(valueOperations.get("cryptoRates")).thenReturn(cachedResponse);

        ExchangeRequest request = new ExchangeRequest("BTC", Set.of("USD"), BigDecimal.valueOf(2));
        var future = cryptoExchangeService.calculateExchangeAsync(request);
        ExchangeResponse exchangeResponse = future.get();

        assertThat(exchangeResponse.from()).isEqualTo("BTC");
        assertThat(exchangeResponse.conversions()).containsKey("USD");
        ExchangeResult result = exchangeResponse.conversions().get("USD");
        assertThat(result.rate()).isEqualByComparingTo("0.00005");
        assertThat(result.amount()).isEqualByComparingTo("2");
        assertThat(result.result()).isEqualByComparingTo("0.000099");
        assertThat(result.fee()).isEqualByComparingTo("0.000001");
    }

    @Test
    void shouldThrowExceptionWhenAmountIsNonPositive() {
        Map<String, RateInfo> ratesMap = Map.of(
                "btc", new RateInfo("Bitcoin", "BTC", BigDecimal.valueOf(20000), "crypto"),
                "eth", new RateInfo("Ethereum", "ETH", BigDecimal.valueOf(1500), "crypto")
        );
        when(valueOperations.get("cryptoRates")).thenReturn(new CryptoRatesResponse(ratesMap));

        ExchangeRequest request = new ExchangeRequest("BTC", Set.of("ETH"), BigDecimal.ZERO);

        assertThatThrownBy(() -> cryptoExchangeService.calculateExchangeAsync(request).get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ExchangeCalculationException.class)
                .hasMessageContaining("Amount must be greater than zero.");
    }

    @Test
    void shouldThrowCurrencyNotFoundWhenRequestedCurrencyDoesNotExist() {
        Map<String, RateInfo> ratesMap = Map.of(
                "btc", new RateInfo("Bitcoin", "BTC", BigDecimal.valueOf(20000), "crypto")
        );
        when(valueOperations.get("cryptoRates")).thenReturn(new CryptoRatesResponse(ratesMap));

        assertThatThrownBy(() -> cryptoExchangeService.getCryptoRatesAsync("ETH", null).get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(CurrencyNotFoundException.class)
                .hasMessageContaining("ETH");
    }
}
