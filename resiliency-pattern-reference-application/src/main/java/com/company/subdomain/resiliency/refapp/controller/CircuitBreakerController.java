package com.company.subdomain.resiliency.refapp.controller;

import com.company.subdomain.resiliency.refapp.exception.ChaosEngineeringException;
import com.company.subdomain.resiliency.refapp.model.MockClientServiceResponse;
import com.company.subdomain.resiliency.refapp.service.ResiliencyDataService;
import com.company.subdomain.resiliency.refapp.util.DecoratorUtil;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.decorators.Decorators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@RestController
@RequestMapping("resiliency-pattern")
public class CircuitBreakerController<T, R> {
    private static Logger LOGGER = LoggerFactory.getLogger(CircuitBreakerController.class);
    private AtomicInteger atomicInteger = new AtomicInteger(0);
    private static final String DATA_SERVICE = "data-service";
    /**
     * Data layer dependency for invoking data methods
     */
    private final ResiliencyDataService resiliencyDataService;
    private CircuitBreaker circuitBreaker;
    private final DecoratorUtil<T, R> decoratorUtil;


    public CircuitBreakerController(ResiliencyDataService resiliencyDataService, DecoratorUtil<T, R> decoratorUtil) {
        this.resiliencyDataService = resiliencyDataService;
        this.decoratorUtil = decoratorUtil;
        this.circuitBreaker = createCircuitBreaker();

    }


    /**
     * @return This endpoint returns mock response for demonstrating fallback resiliency pattern
     */
    @GetMapping("/circuit-breaker")
    public Object getMockOfferings() {
        Supplier<MockClientServiceResponse> supplier = () ->
                (MockClientServiceResponse) resiliencyDataService.getDatafromRemoteService(true);

        return executeWithCircuitBreaker(supplier);
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


    private MockClientServiceResponse executeWithCircuitBreaker(Supplier<MockClientServiceResponse> supplier) {
        handlePublisherEvents();
        return Decorators.ofSupplier(supplier)
                .withCircuitBreaker(circuitBreaker).decorate().get();


//        return Try.ofSupplier(decoratedSupplier).getOrElseGet(throwable -> {
//            String response = "{CircuitBreaker State is : " + circuitBreaker.getState().name() + ", \n Returning cached response: " + fallback() + "}";
//            return (T) response;
//        });
    }

    private void handlePublisherEvents() {
        circuitBreaker.getEventPublisher()
                .onCallNotPermitted(event -> LOGGER.debug(" onCallNotPermitted {}", event))
                .onError(event -> LOGGER.debug(" onError {}", event))
                .onFailureRateExceeded(event -> LOGGER.debug(" onFailureRateExceeded {}", event))
                .onIgnoredError(event -> LOGGER.debug(" onIgnoredError {}", event))
                .onReset(event -> LOGGER.info(" onReset {}", event))
                .onStateTransition(event -> {
                    if (event.getStateTransition() == CircuitBreaker.StateTransition.OPEN_TO_HALF_OPEN) {
                        LOGGER.debug(" onStateTransition OPEN_TO_HALF_OPEN {}", event.getStateTransition());
                    } else if (event.getStateTransition() == CircuitBreaker.StateTransition.HALF_OPEN_TO_CLOSED) {
                        LOGGER.debug(" onStateTransition HALF_OPEN_TO_CLOSED {}", event.getStateTransition());
                    } else {
                        LOGGER.debug(" onStateTransition something else {}", event.getStateTransition());
                    }

                })
                .onSuccess(event -> LOGGER.debug(" onSuccess {}", event));
    }

    private CircuitBreaker createCircuitBreaker() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(25)
                .waitDurationInOpenState(Duration.ofMillis(10000))
                .permittedNumberOfCallsInHalfOpenState(2)
                .slidingWindowSize(4)
                .recordExceptions(ConnectException.class, ResourceAccessException.class, HttpServerErrorException.class)
                .ignoreExceptions(ChaosEngineeringException.class)
                .build();
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        return circuitBreakerRegistry.circuitBreaker(DATA_SERVICE);
    }

    private MockClientServiceResponse fallback() {
        return resiliencyDataService.fallbackOnFailure();
    }
}
