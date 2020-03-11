package com.fidelity.fbt.resiliency.refapp.controller;

import com.fidelity.fbt.resiliency.refapp.exception.ChaosEngineeringException;
import com.fidelity.fbt.resiliency.refapp.model.MockClientServiceResponse;
import com.fidelity.fbt.resiliency.refapp.service.ResiliencyDataService;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author souadhik
 * Controller class for resilient client application
 */
@RestController
@RequestMapping("resiliency-pattern")
public class CircuitBreakerController {
    private static Logger LOGGER = LoggerFactory.getLogger(CircuitBreakerController.class);
    private AtomicInteger atomicInteger = new AtomicInteger(0);
    private static final String DATA_SERVICE = "data-service";
    /**
     * Data layer dependency for invoking data methods
     */
    private final ResiliencyDataService resiliencyDataService;
    private CircuitBreaker circuitBreaker;
    private Retry retry;


    public CircuitBreakerController(
            ResiliencyDataService resiliencyDataService,
            ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            TimeLimiterRegistry timeLimiterRegistry) {
        //this.chaosEngineeringDataService = chaosEngineeringDataService;
        this.resiliencyDataService = resiliencyDataService;
        this.circuitBreaker = createCircuitBreaker();

    }


    /**
     * @return This endpoint returns mock response for demonstrating fallback resiliency pattern
     */
    @GetMapping("/circuit-breaker")
    public Object getMockOfferings() {
        LOGGER.info("Invoking CircuitBreakerController {}", atomicInteger.incrementAndGet());
        return executeWithRetryAndCircuitBreaker(resiliencyDataService::getDatafromRemoteServiceForFallbackPattern, this::fallback);
    }

//    private <T> T execute(Supplier<T> supplier, Function<Throwable, T> fallback) {
//        return Decorators.ofSupplier(supplier)
//                .withRetry(retry)
//                .withCircuitBreaker(circuitBreaker)
//                .withBulkhead(bulkhead)
//                .withRateLimiter(rateLimiter)
//                .withFallback(Arrays.asList(ChaosEngineeringException.class), fallback)
//                .get();
//
//    }


    private <T> T executeWithRetryAndCircuitBreaker(Supplier<T> supplier, Function<Throwable, T> fallback) {
        this.retry = Retry.of(DATA_SERVICE, this::createRetryConfig);
        circuitBreaker.getEventPublisher()
                .onCallNotPermitted(event -> LOGGER.info(" onCallNotPermitted {}", event))
                .onError(event -> LOGGER.error(" onError {}", event))
                .onFailureRateExceeded(event -> LOGGER.info(" onFailureRateExceeded {}", event))
                .onIgnoredError(event -> LOGGER.info(" onIgnoredError {}", event))
                .onReset(event -> LOGGER.info(" onReset {}", event))
                .onStateTransition(event -> LOGGER.info(" onStateTransition {}", event))
                .onSuccess(event -> LOGGER.info(" onSuccess {}", event));
        Supplier<T> decoratedSupplier = CircuitBreaker.decorateSupplier(this.circuitBreaker, supplier);
        return Try.ofSupplier(decoratedSupplier)
                .onFailure(throwable -> fallback(throwable)).get();

//        return Decorators.ofSupplier(supplier)
//                .withCircuitBreaker(this.circuitBreaker)
//                .withRetry(retry)
//                .get();
    }

    private CircuitBreaker createCircuitBreaker() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(25)
                .waitDurationInOpenState(Duration.ofMillis(10000))
                .permittedNumberOfCallsInHalfOpenState(2)
                .slidingWindowSize(4)
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
                .maxAttempts(4)
                .retryExceptions(ConnectException.class, ResourceAccessException.class)
                .build();
    }

    private MockClientServiceResponse fallback(Throwable ex) {
        return resiliencyDataService.fallbackOnFailure();
    }
}
