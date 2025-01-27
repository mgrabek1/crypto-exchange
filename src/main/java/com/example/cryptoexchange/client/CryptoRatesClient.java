package com.example.cryptoexchange.client;

import com.example.cryptoexchange.dto.CryptoRatesResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "cryptoRatesClient", url = "${crypto.api.url}")
public interface CryptoRatesClient {
    @GetMapping("")
    CryptoRatesResponse getRates();
}
