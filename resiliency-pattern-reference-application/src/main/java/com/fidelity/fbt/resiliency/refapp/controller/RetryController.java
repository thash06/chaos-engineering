package com.fidelity.fbt.resiliency.refapp.controller;

import com.fidelity.fbt.resiliency.refapp.model.MockClientServiceResponse;
import com.fidelity.fbt.resiliency.refapp.service.ResiliencyDataService;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.util.Arrays;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author souadhik
 * Controller class for resilient client application
 */
@RestController
@RequestMapping("resiliency-pattern")
public class RetryController {
    private static Logger LOGGER = LoggerFactory.getLogger(RetryController.class);
    private AtomicInteger atomicInteger = new AtomicInteger(0);
    private static final String DATA_SERVICE = "data-service";
    /**
     * Data layer dependency for invoking data methods
     */
    private final ResiliencyDataService resiliencyDataService;
    private Retry retry;
    private RetryRegistry retryRegistry;

    public RetryController(
            ResiliencyDataService resiliencyDataService,
            ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            TimeLimiterRegistry timeLimiterRegistry) {
        this.resiliencyDataService = resiliencyDataService;
    }


    /**
     * @return This endpoint returns mock response for demonstrating fallback resiliency pattern
     */
    @GetMapping("/retry")
    public Object getMockOfferings(int maxSize) {
        LOGGER.info("Invoking RetryController count {} ", atomicInteger.incrementAndGet());
        return executeWithRetry(resiliencyDataService::getDatafromRemoteServiceForFallbackPattern, this::fallback);
    }

    private <T> T executeWithRetry(Supplier<T> supplier, Function<Throwable, T> fallback) {
        RetryConfig retryConfig = createRetryConfig();
        retryRegistry = RetryRegistry.of(retryConfig);
        retry = Retry.of(DATA_SERVICE, retryConfig);
        // Create a RetryRegistry with a custom global configuration

        retry.getEventPublisher()
                .onError(event -> LOGGER.error(" Event on Error {}", event))
                .onRetry(event -> LOGGER.info(" Event on Retry {}", event))
                .onSuccess(event -> LOGGER.info(" Event on Success {}", event))
                .onEvent(event -> LOGGER.debug(" Event occurred records all events Retry, error and success {}", event));
        return Decorators.ofSupplier(supplier)
                .withRetry(retry)
                //.withFallback(Arrays.asList(ConnectException.class, ResourceAccessException.class), fallback)
                .get();

    }

    private RetryConfig createRetryConfig() {
        IntervalFunction intervalWithCustomExponentialBackoff = IntervalFunction
                .ofExponentialBackoff(500l, 5d);
        return RetryConfig.custom()
                .intervalFunction(intervalWithCustomExponentialBackoff)
                .maxAttempts(5)
                .retryExceptions(ConnectException.class, ResourceAccessException.class)
                .build();
    }

    private MockClientServiceResponse fallback(Throwable ex) {
        return resiliencyDataService.fallbackOnFailure();
    }
}
