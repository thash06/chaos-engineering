package com.fidelity.fbt.resiliency.refapp.controller;

import com.fidelity.fbt.resiliency.refapp.exception.ChaosEngineeringException;
import com.fidelity.fbt.resiliency.refapp.model.MockClientServiceResponse;
import com.fidelity.fbt.resiliency.refapp.service.ResiliencyDataService;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author souadhik
 * Controller class for resilient client application
 */
@RestController
@RequestMapping("resiliency-pattern")
public class ResiliencyPatternsController {

    private static final String DATA_SERVICE = "data-service";
    /**
     * Data layer dependency for invoking data methods
     */
    private final ResiliencyDataService resiliencyDataService;
    private CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;
    private final ThreadPoolBulkhead threadPoolBulkhead;
    private Retry retry;
    private RetryRegistry retryRegistry;
    private final RateLimiter rateLimiter;
    private final TimeLimiter timeLimiter;
    private final ScheduledExecutorService scheduledExecutorService;

    public ResiliencyPatternsController(
            ResiliencyDataService resiliencyDataService,
            ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry,
            BulkheadRegistry bulkheadRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            TimeLimiterRegistry timeLimiterRegistry) {
        //this.chaosEngineeringDataService = chaosEngineeringDataService;
        this.resiliencyDataService = resiliencyDataService;
        this.bulkhead = bulkheadRegistry.bulkhead(DATA_SERVICE);
        this.threadPoolBulkhead = threadPoolBulkheadRegistry.bulkhead(DATA_SERVICE);
        this.rateLimiter = rateLimiterRegistry.rateLimiter(DATA_SERVICE);
        this.timeLimiter = timeLimiterRegistry.timeLimiter(DATA_SERVICE);
        this.scheduledExecutorService = Executors.newScheduledThreadPool(3);
        this.circuitBreaker = createCircuitBreaker();
    }

    /**
     * @return This endpoint returns mock response for demonstrating fallback resiliency pattern
     */
    @GetMapping("/fallback")
    public Object getMockOfferings(@RequestParam String feature) {
        switch (feature) {
            case "retry":
                return executeWithRetry(resiliencyDataService::getDatafromRemoteServiceForFallbackPattern, this::fallback);
            case "circuit-breaker":
                return executeWithRetryAndCircuitBreaker(resiliencyDataService::getDatafromRemoteServiceForFallbackPattern, this::fallback);
            default:
                return resiliencyDataService.getDatafromRemoteServiceForFallbackPattern();
        }

    }

    private <T> T execute(Supplier<T> supplier, Function<Throwable, T> fallback) {
        return Decorators.ofSupplier(supplier)
                .withRetry(retry)
                .withCircuitBreaker(circuitBreaker)
                .withBulkhead(bulkhead)
                .withRateLimiter(rateLimiter)
                .withFallback(Arrays.asList(ChaosEngineeringException.class), fallback)
                .get();

    }

    private <T> T executeWithRetry(Supplier<T> supplier, Function<Throwable, T> fallback) {
        retry = Retry.of(DATA_SERVICE, this::createRetryConfig);
        // Create a RetryRegistry with a custom global configuration
        retryRegistry = RetryRegistry.of(createRetryConfig());
        return Decorators.ofSupplier(supplier)
                //.withFallback(Arrays.asList(ConnectException.class, ResourceAccessException.class), fallback)
                .withRetry(retry)
                .get();
    }

    private <T> T executeWithRetryAndCircuitBreaker(Supplier<T> supplier, Function<Throwable, T> fallback) {
        retry = Retry.of(DATA_SERVICE, this::createRetryConfig);
        return Decorators.ofSupplier(supplier)
                .withRetry(retry)
                .withCircuitBreaker(circuitBreaker)
                .get();
    }

    private CircuitBreaker createCircuitBreaker() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(25)
                .waitDurationInOpenState(Duration.ofMillis(10000))
                .permittedNumberOfCallsInHalfOpenState(2)
                .slidingWindowSize(2)
                .recordExceptions(ConnectException.class, ResourceAccessException.class)
                .ignoreExceptions(ChaosEngineeringException.class)
                .build();
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        return circuitBreakerRegistry.circuitBreaker(DATA_SERVICE);
    }

    private RetryConfig createRetryConfig() {
        IntervalFunction intervalWithCustomExponentialBackoff = IntervalFunction
                .ofExponentialBackoff(500l, 2d);
        return RetryConfig.custom()
                .intervalFunction(intervalWithCustomExponentialBackoff)
                .maxAttempts(3)
                .retryExceptions(ConnectException.class, ResourceAccessException.class)
                .build();
    }

    private MockClientServiceResponse fallback(Throwable ex) {
        return resiliencyDataService.fallbackOnFailure();
    }
}
