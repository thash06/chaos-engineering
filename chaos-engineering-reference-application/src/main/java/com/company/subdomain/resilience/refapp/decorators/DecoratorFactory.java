package com.company.subdomain.resilience.refapp.decorators;

import com.company.subdomain.resilience.refapp.exception.ChaosEngineeringException;
import io.github.resilience4j.bulkhead.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
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
    public static final String TIME_LIMITER = "time-limiter";
    public static final String SEMAPHORE_BULKHEAD = "semaphore-bulkhead";
    public static final String THREAD_POOL_BULKHEAD = "thread-pool-bulkhead";
    public static final String RETRY_SERVICE = "retry-for-bulkhead";
    public static final String CIRCUIT_BREAKER = "circuit-breaker";
    public static final String RATE_LIMITER = "rate-limiter";

    public final ThreadPoolBulkhead threadPoolBulkhead;
    public final Bulkhead bulkhead;
    public final Retry retry;
    public final TimeLimiter timeLimiter;
    public final CircuitBreaker circuitBreaker;
    public final RateLimiter rateLimiter;

    public DecoratorFactory() {
        int availableProcessors = 4;
        this.threadPoolBulkhead = createThreadPoolBulkhead(availableProcessors);
        this.bulkhead = createBulkhead(availableProcessors);
        RetryConfig retryConfig = createRetryConfig();
        retry = Retry.of(RETRY_SERVICE, retryConfig);
        timeLimiter = createTimeLimiter(3000);
        circuitBreaker = createCircuitBreaker();
        this.rateLimiter = createRateLimiter(4, 10000, 0);
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
                        WebClientResponseException.class,
                        ChaosEngineeringException.class)
                .build();
    }

    private TimeLimiter createTimeLimiter(int waitTimeForThread) {
        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .cancelRunningFuture(true)
                .timeoutDuration(Duration.ofMillis(waitTimeForThread))
                .build();
        TimeLimiterRegistry timeLimiterRegistry = TimeLimiterRegistry.of(timeLimiterConfig);
        return timeLimiterRegistry.timeLimiter(TIME_LIMITER, timeLimiterConfig);
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
        LOGGER.info("Semaphore bulkhead with maxConcurrentCalls {}", availableProcessors);
        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(availableProcessors)
                .maxWaitDuration(Duration.ofMillis(0))
                .writableStackTraceEnabled(true)
                .build();

        BulkheadRegistry bulkheadRegistry = BulkheadRegistry.of(bulkheadConfig);
        return bulkheadRegistry.bulkhead(SEMAPHORE_BULKHEAD, bulkheadConfig);
    }

    private CircuitBreaker createCircuitBreaker() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(25)
                .waitDurationInOpenState(Duration.ofMillis(100))
                .permittedNumberOfCallsInHalfOpenState(1)
                .slidingWindowSize(4)
                .recordExceptions(ChaosEngineeringException.class, TimeoutException.class, BulkheadFullException.class)
                .ignoreExceptions(IOException.class)
                .build();
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        return circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER, circuitBreakerConfig);
    }

    private RateLimiter createRateLimiter(int limitForPeriod, int windowInMilliseconds, int waitTimeForThread) {
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofMillis(windowInMilliseconds))
                .limitForPeriod(limitForPeriod)
                .timeoutDuration(Duration.ofMillis(waitTimeForThread))

                .build();
        RateLimiterRegistry rateLimiterRegistry = RateLimiterRegistry.of(rateLimiterConfig);
        return rateLimiterRegistry.rateLimiter(RATE_LIMITER, rateLimiterConfig);
    }
}
