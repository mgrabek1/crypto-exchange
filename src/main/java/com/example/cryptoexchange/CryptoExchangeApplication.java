package com.example.cryptoexchange;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients(basePackages = "com.example.cryptoexchange.client")
@SpringBootApplication
public class CryptoExchangeApplication {
    public static void main(String[] args) {
        SpringApplication.run(CryptoExchangeApplication.class, args);
    }
}
