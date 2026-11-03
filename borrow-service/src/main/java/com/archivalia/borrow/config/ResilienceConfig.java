package com.archivalia.borrow.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.FeignException;
import feign.Retryer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;

/**
 * Resilience for the Feign clients:
 * <ul>
 *   <li>Circuit breaker (Resilience4j) - opens when too many calls fail; 4xx responses are business
 *       errors and do not count as failures.</li>
 *   <li>Time limiter (Resilience4j) - caps the total duration of a call.</li>
 *   <li>Retry - {@link SafeRetryer}, applied by Feign inside the circuit breaker.</li>
 * </ul>
 * All values are configurable in application.properties (resilience.*).
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCircuitBreakerCustomizer(
            @Value("${resilience.circuit-breaker.failure-rate-threshold:50}") float failureRateThreshold,
            @Value("${resilience.circuit-breaker.sliding-window-size:10}") int slidingWindowSize,
            @Value("${resilience.circuit-breaker.minimum-number-of-calls:5}") int minimumNumberOfCalls,
            @Value("${resilience.circuit-breaker.wait-duration-in-open-state-ms:10000}") long waitInOpenStateMs,
            @Value("${resilience.time-limiter.timeout-ms:8000}") long timeoutMs) {

        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .waitDurationInOpenState(Duration.ofMillis(waitInOpenStateMs))
                .permittedNumberOfCallsInHalfOpenState(3)
                .ignoreExceptions(FeignException.FeignClientException.class)
                .build();

        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(timeoutMs))
                .cancelRunningFuture(true)
                .build();

        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(circuitBreakerConfig)
                .timeLimiterConfig(timeLimiterConfig)
                .build());
    }

    @Bean
    public Retryer feignRetryer(
            @Value("${resilience.retry.period-ms:200}") long periodMs,
            @Value("${resilience.retry.max-period-ms:1000}") long maxPeriodMs,
            @Value("${resilience.retry.max-attempts:3}") int maxAttempts) {

        return new SafeRetryer(periodMs, maxPeriodMs, maxAttempts);
    }
}
