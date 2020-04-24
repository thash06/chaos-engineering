package com.fidelity.fbt.chaos.refapp.decorators;

import com.fidelity.fbt.chaos.refapp.exception.ChaosEngineeringException;
import io.github.resilience4j.bulkhead.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Component
public class DecoratorFactory {
    private static Logger LOGGER = LoggerFactory.getLogger(DecoratorFactory.class);
    public static final String THREAD_POOL_BULKHEAD_TIME_LIMITER = "time-limiter";
    public static final String SEMAPHORE_BULKHEAD = "semaphore-bulkhead";
    public static final String THREAD_POOL_BULKHEAD = "thread-pool-bulkhead";
    public static final String RETRY_SERVICE = "retry-for-bulkhead";
    public static final String CIRCUIT_BREAKER = "circuit-breaker";

    public final ThreadPoolBulkhead threadPoolBulkhead;
    public final Bulkhead bulkhead;
    public final Retry retry;
    public final TimeLimiter timeLimiter;
    public final CircuitBreaker circuitBreaker;

    public DecoratorFactory() {
        int availableProcessors = Runtime.getRuntime()
                .availableProcessors() - 4;
        this.threadPoolBulkhead = createThreadPoolBulkhead(availableProcessors);
        this.bulkhead = createBulkhead(availableProcessors);
        RetryConfig retryConfig = createRetryConfig();
        retry = Retry.of(RETRY_SERVICE, retryConfig);
        timeLimiter = createTimeLimiter(1000);
        circuitBreaker = createCircuitBreaker();
    }

    private RetryConfig createRetryConfig() {
        IntervalFunction intervalWithCustomExponentialBackoff = IntervalFunction
                .ofExponentialBackoff(500l, 5d);
        return RetryConfig.custom()
                .intervalFunction(intervalWithCustomExponentialBackoff)
                .maxAttempts(5)
                .retryExceptions(ConnectException.class,
                        ResourceAccessException.class,
                        HttpServerErrorException.class,
                        ExecutionException.class,
                        WebClientResponseException.class)
                .build();
    }

    private TimeLimiter createTimeLimiter(int waitTimeForThread) {
        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .cancelRunningFuture(true)
                .timeoutDuration(Duration.ofMillis(waitTimeForThread))
                .build();
        TimeLimiterRegistry timeLimiterRegistry = TimeLimiterRegistry.of(timeLimiterConfig);
        return timeLimiterRegistry.timeLimiter(THREAD_POOL_BULKHEAD_TIME_LIMITER, timeLimiterConfig);
    }

    private ThreadPoolBulkhead createThreadPoolBulkhead(int availableProcessors) {
        int coreThreadPoolSizeFactor = availableProcessors >= 8 ? 4 : 1;
        int coreThreadPoolSize = availableProcessors - coreThreadPoolSizeFactor;
        ThreadPoolBulkheadConfig threadPoolBulkheadConfig = ThreadPoolBulkheadConfig.custom()
                .maxThreadPoolSize(availableProcessors)
                .coreThreadPoolSize(coreThreadPoolSize)
                .queueCapacity(4)
                .keepAliveDuration(Duration.ofSeconds(2))
                .build();
        LOGGER.info("ThreadPoolBulkheadConfig created with maxThreadPoolSize {} : coreThreadPoolSize {}",
                availableProcessors, coreThreadPoolSize);
        ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry = ThreadPoolBulkheadRegistry.of(threadPoolBulkheadConfig);
        return threadPoolBulkheadRegistry.bulkhead(THREAD_POOL_BULKHEAD, threadPoolBulkheadConfig);
    }

    private Bulkhead createBulkhead(int availableProcessors) {
        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(availableProcessors)
                .maxWaitDuration(Duration.ofMillis(100))
                .writableStackTraceEnabled(true)
                .build();

        BulkheadRegistry bulkheadRegistry = BulkheadRegistry.of(bulkheadConfig);
        return bulkheadRegistry.bulkhead(SEMAPHORE_BULKHEAD, bulkheadConfig);
    }

    private CircuitBreaker createCircuitBreaker() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(25)
                .waitDurationInOpenState(Duration.ofSeconds(2))
                .permittedNumberOfCallsInHalfOpenState(2)
                .slidingWindowSize(8)
                .recordExceptions(ChaosEngineeringException.class, TimeoutException.class, BulkheadFullException.class)
                .ignoreExceptions(IOException.class)
                .build();
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        return circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER, circuitBreakerConfig);
    }

}
