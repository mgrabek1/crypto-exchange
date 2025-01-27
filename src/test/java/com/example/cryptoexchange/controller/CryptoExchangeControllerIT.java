package com.example.cryptoexchange.controller;

import com.example.cryptoexchange.client.CryptoRatesClient;
import com.example.cryptoexchange.dto.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CryptoExchangeControllerIT {

    private static final GenericContainer<?> redisContainer =
            new GenericContainer<>(DockerImageName.parse("redis:7.0.5"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    }

    @BeforeAll
    static void startContainer() {
        redisContainer.start();
    }

    @MockBean
    private CryptoRatesClient cryptoRatesClient;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RedisTemplate<String, CryptoRatesResponse> redisTemplate;

    @AfterEach
    void clearRedis() {
        redisTemplate.delete("cryptoRates");
    }

    @Test
    @DisplayName("should return cached rates if present in Redis")
    void shouldReturnCachedRates() {
        Map<String, RateInfo> ratesMap = Map.of(
                "btc", new RateInfo("Bitcoin", "BTC", BigDecimal.valueOf(20000), "crypto"),
                "usd", new RateInfo("US Dollar", "USD", BigDecimal.valueOf(1), "fiat")
        );
        CryptoRatesResponse cachedResponse = new CryptoRatesResponse(ratesMap);
        redisTemplate.opsForValue().set("cryptoRates", cachedResponse, Duration.ofMinutes(5));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/currencies/{currency}")
                        .queryParam("filter", "USD")
                        .build("BTC"))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(CurrencyRatesResponse.class)
                .value(resp -> {
                    assertThat(resp.source()).isEqualTo("BTC");
                    assertThat(resp.rates()).containsKey("USD");
                    assertThat(resp.rates().get("USD")).isEqualByComparingTo("0.00005");
                });
    }

    @Test
    @DisplayName("should fetch new rates from API when cache is empty")
    void shouldFetchNewRatesWhenCacheEmpty() {
        redisTemplate.delete("cryptoRates");

        Map<String, RateInfo> ratesMap = Map.of(
                "btc", new RateInfo("Bitcoin", "BTC", BigDecimal.valueOf(20000), "crypto"),
                "usd", new RateInfo("USDollar", "USD", BigDecimal.valueOf(1), "fiat")
        );
        when(cryptoRatesClient.getRates()).thenReturn(new CryptoRatesResponse(ratesMap));

        webTestClient.get()
                .uri("/currencies/btc")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(CurrencyRatesResponse.class)
                .value(resp -> {
                    assertThat(resp.source()).isEqualTo("BTC");
                    assertThat(resp.rates().keySet()).containsOnly("USD");
                });

        CryptoRatesResponse inRedis = redisTemplate.opsForValue().get("cryptoRates");
        assertThat(inRedis).isNotNull();
        assertThat(inRedis.rates()).containsKeys("btc", "usd");
    }

    @Test
    @DisplayName("should calculate exchange in /currencies/exchange")
    void shouldCalculateExchange() {
        redisTemplate.delete("cryptoRates");
        Map<String, RateInfo> ratesMap = Map.of(
                "btc", new RateInfo("Bitcoin", "BTC", BigDecimal.valueOf(20000), "crypto"),
                "usd", new RateInfo("USDollar", "USD", BigDecimal.valueOf(1), "fiat")
        );
        when(cryptoRatesClient.getRates()).thenReturn(new CryptoRatesResponse(ratesMap));

        ExchangeRequest request = new ExchangeRequest("BTC", Set.of("USD"), BigDecimal.valueOf(2));

        webTestClient.post()
                .uri("/currencies/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ExchangeResponse.class)
                .value(resp -> {
                    assertThat(resp.from()).isEqualTo("BTC");
                    assertThat(resp.conversions()).containsKey("USD");
                    ExchangeResult er = resp.conversions().get("USD");
                    assertThat(er.rate()).isEqualByComparingTo("0.00005");
                    assertThat(er.amount()).isEqualByComparingTo("2");
                    assertThat(er.result()).isEqualByComparingTo("0.000099");
                    assertThat(er.fee()).isEqualByComparingTo("0.000001");
                });
    }
}
