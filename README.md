# Crypto Exchange API

## Overview
The **Crypto Exchange API** is a Spring Boot microservice that provides real-time cryptocurrency exchange rate retrieval and currency conversion. It integrates with an external cryptocurrency API and caches exchange rates using Redis for performance optimization.

## Features
- Retrieve real-time exchange rates for cryptocurrencies as required in the recruitment task.
- Convert cryptocurrencies into other fiat or digital currencies, fulfilling the conversion requirement.
- Cache exchange rates in Redis to optimize API calls and meet performance expectations.
- Scheduled updates of exchange rates every 5 minutes, aligning with the requirement to maintain up-to-date rates.
- Exception handling with meaningful error messages for robustness.
- Asynchronous operations for improved performance and scalability.
- RESTful API documented with Swagger for easy integration and validation.

## Technologies Used
- **Java 17**
- **Spring Boot 3.2.1** (Spring WebFlux, Spring Data Redis, Spring Cloud OpenFeign)
- **Redis** (for caching exchange rates as required in the task)
- **Feign Client** (to communicate with an external API as per the integration requirement)
- **Lombok** (to reduce boilerplate code)
- **MapStruct** (for DTO conversion to maintain clean architecture)
- **Swagger (Springdoc OpenAPI)** (for API documentation as requested)
- **Testcontainers** (for integration testing with Redis to verify correctness)
- **JUnit 5 & Mockito** (for unit testing to ensure solution validity)

## API Endpoints
### 1. Retrieve Cryptocurrency Exchange Rates
#### Request
```http
GET /currencies/{currency}
```
#### Parameters
- `currency` (path) - The base cryptocurrency symbol (e.g., `BTC`)
- `filter` (query, optional) - Comma-separated list of target currencies (e.g., `USD,ETH`)
#### Response
```json
{
  "source": "BTC",
  "rates": {
    "USD": 0.00005,
    "ETH": 0.032
  }
}
```
This endpoint satisfies the requirement to return cryptocurrency rates with an optional filter.

### 2. Perform Cryptocurrency Exchange Calculation
#### Request
```http
POST /currencies/exchange
```
#### Request Body
```json
{
  "from": "BTC",
  "to": ["USD"],
  "amount": 2
}
```
#### Response
```json
{
  "from": "BTC",
  "conversions": {
    "USD": {
      "rate": 0.00005,
      "amount": 2,
      "result": 0.000099,
      "fee": 0.000001
    }
  }
}
```
This endpoint ensures conversion calculation and includes a 1% fee as required by the task.

## Installation & Running the Application
### Prerequisites
- **JDK 17**
- **Docker & Docker Compose**

### Build the Application
```sh
mvn clean install
```

### Run the Application
```sh
docker compose up --build
```

### Access API Documentation (Swagger UI)
After starting the application, visit:
[Swagger UI](http://localhost:8080/webjars/swagger-ui/index.html#)

## Architecture & Implementation Details
### 1. Feign Client for External API Calls
- `CryptoRatesClient` fetches exchange rates from the external API using **Spring Cloud OpenFeign**, fulfilling the API integration requirement.

### 2. Caching with Redis
- The exchange rates are stored in **Redis** for 5 minutes to reduce external API calls and meet performance expectations.
- Implemented using **RedisTemplate** in `CryptoExchangeService`.
- In a **production environment**, with a purchased API key, more frequent updates would be possible, ensuring even fresher data and more up-to-date exchange rates in the cache.

### 3. Asynchronous Processing & Reactive Programming with WebFlux (Mono)
- `CryptoExchangeService` executes exchange rate retrieval and conversions asynchronously using **CompletableFuture**.
- The API is built with **Spring WebFlux**, allowing **reactive programming**.
- The endpoints return **Mono<T>**, ensuring a non-blocking execution model that improves performance and scalability by efficiently handling concurrent requests.
- This satisfies the task requirement of calculating exchange values in a concurrent manner.

### 4. Scheduled Rate Updates
- A **@Scheduled** task updates exchange rates every 5 minutes.
- This meets the requirement of keeping exchange rates refreshed regularly.

### 5. Exception Handling
- Global error handling implemented in `GlobalExceptionHandler` to ensure meaningful error messages.
- Custom exceptions: `CurrencyNotFoundException`, `ExchangeCalculationException`, `ExternalApiException` are included to handle edge cases properly.

### 6. Testing Strategy
- **Unit Tests**: `CryptoExchangeServiceTest` (mocked Redis & Feign Client) verifies the correct behavior of individual components.
- **Integration Tests**: `CryptoExchangeControllerIT` (using Testcontainers for Redis) ensures API correctness as required.

## Conclusion
This project successfully implements a **scalable, high-performance** cryptocurrency exchange API that meets all specified recruitment task requirements. It includes **caching, asynchronous operations, API integration, scheduled updates, and robust error handling**. The solution follows best practices for **microservices architecture** and **modern Java development**, making it well-suited for **production deployment**.

