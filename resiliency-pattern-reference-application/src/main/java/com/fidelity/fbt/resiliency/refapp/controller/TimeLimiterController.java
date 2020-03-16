package com.fidelity.fbt.resiliency.refapp.controller;

import com.fidelity.fbt.resiliency.refapp.exception.ChaosEngineeringException;
import com.fidelity.fbt.resiliency.refapp.model.MockClientServiceResponse;
import com.fidelity.fbt.resiliency.refapp.service.ResiliencyDataService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * @author souadhik
 * Controller class for resilient client application
 */
@RestController
@RequestMapping("resiliency-pattern")
public class TimeLimiterController {
    private static Logger LOGGER = LoggerFactory.getLogger(TimeLimiterController.class);
    private AtomicInteger atomicInteger = new AtomicInteger(0);
    private static final String DATA_SERVICE = "data-service";


    /**
     * Data layer dependency for invoking data methods
     */
    private final ResiliencyDataService resiliencyDataService;
    private final CircuitBreaker circuitBreaker;

    public TimeLimiterController(
            ResiliencyDataService resiliencyDataService) {
        this.resiliencyDataService = resiliencyDataService;
        this.circuitBreaker = createCircuitBreaker();
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

    /**
     * @return This endpoint returns mock response for demonstrating fallback resiliency pattern
     */
    @GetMapping("/time-limiter")
    public Object getMockOfferings(@RequestParam int waitTimeForThread) throws Exception {
        LOGGER.info("Invoking TimeLimiterController count {} ", atomicInteger.incrementAndGet());
        return executeWithTimeLimiter(createTimeLimiter(waitTimeForThread));

    }

    private <T> T executeWithTimeLimiter(TimeLimiter timeLimiter) throws Exception {
        return callRemoteService1(timeLimiter);

    }

    private <T> T callRemoteService(TimeLimiter timeLimiter) throws Exception {
        handlePublishedEvents(timeLimiter);
        Supplier<CompletableFuture<Object>> futureSupplier = () ->
                CompletableFuture.supplyAsync(resiliencyDataService::getDatafromRemoteServiceForFallbackPattern);
        Callable<Object> decorateFutureSupplier = TimeLimiter.decorateFutureSupplier(timeLimiter, futureSupplier);

        Object returnValue = Try.of(decorateFutureSupplier::call).getOrElse(this::fallback);
        //.getOrElseThrow(throwable -> new Exception("Request timed out: " + throwable.getMessage()));
        return (T) returnValue;
    }

    private <T> T callRemoteService1(TimeLimiter timeLimiter) throws Exception {

        handlePublishedEvents(timeLimiter);
        handlePublishedEvents(circuitBreaker);
        Supplier<CompletableFuture<Object>> futureSupplier = () ->
                CompletableFuture.supplyAsync(resiliencyDataService::getDatafromRemoteServiceForFallbackPattern);
        Callable<Object> decorateFutureSupplier = TimeLimiter.decorateFutureSupplier(timeLimiter, futureSupplier);
        Callable<Object> callableDecoratedWithCircuitBreakerAndTimeLimiter =
                CircuitBreaker.decorateCallable(circuitBreaker, decorateFutureSupplier);
        Object returnValue = Try.of(callableDecoratedWithCircuitBreakerAndTimeLimiter::call).getOrElse(this::fallback);
        //.getOrElseThrow(throwable -> new Exception("Request timed out: " + throwable.getMessage()));
        return (T) returnValue;
    }

    private void handlePublishedEvents(TimeLimiter timeLimiter) {
        timeLimiter.getEventPublisher()
                .onError(event -> LOGGER.error("Failed call due to {} ", event))
                .onTimeout(event -> LOGGER.error("Request timed out {} ", event))
                .onSuccess(event -> LOGGER.info("Successful request {} ", event));
    }
    private void handlePublishedEvents(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher()
                .onCallNotPermitted(event -> LOGGER.info(" onCallNotPermitted {}", event))
                .onError(event -> LOGGER.error(" onError {}", event))
                .onFailureRateExceeded(event -> LOGGER.debug(" onFailureRateExceeded {}", event))
                .onIgnoredError(event -> LOGGER.debug(" onIgnoredError {}", event))
                .onReset(event -> LOGGER.info(" onReset {}", event))
                .onStateTransition(event -> {
                    if(event.getStateTransition() == CircuitBreaker.StateTransition.CLOSED_TO_OPEN) {
                        LOGGER.info(" onStateTransition CLOSED_TO_OPEN {}", event.getStateTransition());
                    }
                    else if(event.getStateTransition() == CircuitBreaker.StateTransition.OPEN_TO_HALF_OPEN){
                        LOGGER.debug(" onStateTransition OPEN_TO_HALF_OPEN {}", event.getStateTransition());
                    }
                    else{
                        LOGGER.debug(" onStateTransition something else {}", event.getStateTransition());
                    }

                })
                .onSuccess(event -> LOGGER.debug(" onSuccess {}", event));
    }

    private TimeLimiter createTimeLimiter(int waitTimeForThread) {
        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .cancelRunningFuture(true)
                .timeoutDuration(Duration.ofMillis(waitTimeForThread))
                .build();

        TimeLimiterRegistry timeLimiterRegistry = TimeLimiterRegistry.of(timeLimiterConfig);


        return timeLimiterRegistry.timeLimiter(DATA_SERVICE, timeLimiterConfig);


    }

    private MockClientServiceResponse fallback() {
        return resiliencyDataService.fallbackOnFailure();
    }
}
