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
import java.util.*;
import java.util.concurrent.Callable;
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


    public CircuitBreakerController(
            ResiliencyDataService resiliencyDataService,
            ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            TimeLimiterRegistry timeLimiterRegistry) {
        this.resiliencyDataService = resiliencyDataService;
        this.circuitBreaker = createCircuitBreaker();

    }


    /**
     * @return This endpoint returns mock response for demonstrating fallback resiliency pattern
     */
    @GetMapping("/circuit-breaker")
    public Object getMockOfferings() {
        LOGGER.info("Invoking CircuitBreakerController {}", atomicInteger.incrementAndGet());
        return executeWithRetryAndCircuitBreaker(resiliencyDataService::getDatafromRemoteServiceForFallbackPattern);
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


    private <T> T executeWithRetryAndCircuitBreaker(Supplier<T> supplier){
        List<Object> returnValues = new ArrayList<>();
        Set<String> successfulRemoteCalls = new HashSet<>();
        Set<String> rejectedRemoteCalls = new HashSet<>();

//        Supplier<T> decoratedSupplier = CircuitBreaker.decorateSupplier(this.circuitBreaker, supplier);
//        return Try.ofSupplier(decoratedSupplier)
//                .recover(throwable -> (T) fallback(throwable)).get();
        if (!rejectedRemoteCalls.isEmpty()) {
            String message = "CircuitBreaker state is: " + rejectedRemoteCalls.stream().reduce((s, s2) -> String.join(", ", s, s2)).get();
            Exception wrappedException = new Exception(message);
            return (T) wrappedException;
        }
//        return Decorators.ofSupplier(supplier)
//                .withCircuitBreaker(this.circuitBreaker)
//                .get();
        Supplier<T> decoratedSupplier = Decorators.ofSupplier(supplier)
                .withFallback(Arrays.asList(ConnectException.class), throwable -> (T) fallback(throwable))
                .withCircuitBreaker(circuitBreaker)
                .decorate();
        circuitBreaker.getEventPublisher()
                .onCallNotPermitted(event -> LOGGER.debug(" onCallNotPermitted {}", event))
                .onError(event -> LOGGER.debug(" onError {}", event))
                .onFailureRateExceeded(event -> LOGGER.debug(" onFailureRateExceeded {}", event))
                .onIgnoredError(event -> LOGGER.debug(" onIgnoredError {}", event))
                .onReset(event -> LOGGER.info(" onReset {}", event))
                .onStateTransition(event -> {
                    if(event.getStateTransition() == CircuitBreaker.StateTransition.OPEN_TO_HALF_OPEN) {
                        LOGGER.debug(" onStateTransition OPEN_TO_HALF_OPEN {}", event.getStateTransition());
                        rejectedRemoteCalls.add(event.getStateTransition().toString());
                    }
                    else if(event.getStateTransition() == CircuitBreaker.StateTransition.HALF_OPEN_TO_CLOSED){
                        LOGGER.debug(" onStateTransition HALF_OPEN_TO_CLOSED {}", event.getStateTransition());
                        rejectedRemoteCalls.add(event.getStateTransition().toString());
                    }
                    else{
                        rejectedRemoteCalls.add(event.getStateTransition().toString());
                        LOGGER.debug(" onStateTransition {}", event.getStateTransition());
                    }

                })
                .onSuccess(event -> LOGGER.debug(" onSuccess {}", event))
        ;

        return Try.ofSupplier(decoratedSupplier).getOrElseGet(throwable -> (T) circuitBreaker.getState().name());
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

//    private RetryConfig createRetryConfig() {
//        IntervalFunction intervalWithCustomExponentialBackoff = IntervalFunction
//                .ofExponentialBackoff(500l, 2d);
//        return RetryConfig.custom()
//                .intervalFunction(intervalWithCustomExponentialBackoff)
//                .maxAttempts(4)
//                .retryExceptions(ConnectException.class, ResourceAccessException.class)
//                .build();
//    }

    private MockClientServiceResponse fallback(Throwable ex) {
        return resiliencyDataService.fallbackOnFailure();
    }
}
