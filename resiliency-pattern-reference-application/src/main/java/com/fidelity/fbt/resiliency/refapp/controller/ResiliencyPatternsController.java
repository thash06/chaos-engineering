package com.fidelity.fbt.resiliency.refapp.controller;

import com.fidelity.fbt.resiliency.refapp.exception.ChaosEngineeringException;
import com.fidelity.fbt.resiliency.refapp.model.MockClientServiceResponse;
import com.fidelity.fbt.resiliency.refapp.service.ResiliencyDataService;
import io.github.resilience4j.bulkhead.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.vavr.CheckedRunnable;
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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author souadhik
 * Controller class for resilient client application
 */
@RestController
@RequestMapping("resiliency-pattern")
public class ResiliencyPatternsController {
    private static Logger LOGGER = LoggerFactory.getLogger(ResiliencyPatternsController.class);
    private AtomicInteger atomicInteger = new AtomicInteger(0);
    private static final String DATA_SERVICE = "data-service";
    /**
     * Data layer dependency for invoking data methods
     */
    private final ResiliencyDataService resiliencyDataService;
    private CircuitBreaker circuitBreaker;
    private Bulkhead bulkhead;
    private final ThreadPoolBulkhead threadPoolBulkhead;
    private Retry retry;
    private RetryRegistry retryRegistry;
    private RateLimiter rateLimiter;
    private final TimeLimiter timeLimiter;
    private final ScheduledExecutorService scheduledExecutorService;

    public ResiliencyPatternsController(
            ResiliencyDataService resiliencyDataService,
            ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            TimeLimiterRegistry timeLimiterRegistry) {
        //this.chaosEngineeringDataService = chaosEngineeringDataService;
        this.resiliencyDataService = resiliencyDataService;
        this.threadPoolBulkhead = threadPoolBulkheadRegistry.bulkhead(DATA_SERVICE);
        this.timeLimiter = timeLimiterRegistry.timeLimiter(DATA_SERVICE);
        this.scheduledExecutorService = Executors.newScheduledThreadPool(3);
        this.circuitBreaker = createCircuitBreaker();
        this.rateLimiter = createRateLimiter();
    }


    /**
     * @return This endpoint returns mock response for demonstrating fallback resiliency pattern
     */
    @GetMapping("/fallback")
    public Object getMockOfferings(@RequestParam String feature, @RequestParam int maxConcurrentCalls, @RequestParam int maxWaitDuration) {
        LOGGER.info("Invoking ResiliencyPatternsController for feature {} count {} ", feature, atomicInteger.incrementAndGet());
        switch (feature) {
            case "retry":
                return executeWithRetry(resiliencyDataService::getDatafromRemoteServiceForFallbackPattern, this::fallback);
            case "circuit-breaker":
                return executeWithRetryAndCircuitBreaker(resiliencyDataService::getDatafromRemoteServiceForFallbackPattern, this::fallback);
            case "rate-limiter":
                return executeWithRateLimiter();
            case "bulkhead":
                //return executeWithBulkhead(maxConcurrentCalls, resiliencyDataService::getDatafromRemoteServiceForFallbackPattern, this::fallback);
                return executeWithBulkhead(createBulkhead(maxConcurrentCalls, maxWaitDuration));
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
                //.withRetry(retry)
                .withCircuitBreaker(circuitBreaker)
                .get();
    }

    private <T> T executeWithRateLimiter() {
        CheckedRunnable restrictedCall = RateLimiter
                .decorateCheckedRunnable(rateLimiter, resiliencyDataService::getDatafromRemoteServiceForFallbackPattern);

        Try.run(restrictedCall)
                .andThenTry(restrictedCall)
                .onFailure((throwable) -> LOGGER.info("Wait before call it again :)"));
        rateLimiter.getEventPublisher()
                .onSuccess(event -> LOGGER.info("Success"))
                .onFailure(event -> LOGGER.info("Failure"));
        //rateLimiter.
        return null;

    }

    private <T> T executeWithBulkhead(Bulkhead bulkhead) {
        LOGGER.info("Created Bulkhead with {} max concurrent calls maxWaitDuration {} ",
                bulkhead.getBulkheadConfig().getMaxConcurrentCalls(), bulkhead.getBulkheadConfig().getMaxWaitDuration());
        List<Object> returnValues = new ArrayList<>();
        List<Exception> failedRequests = new ArrayList<>();
        Set<String> successfulRemoteCalls = new HashSet<>();
        Set<String> rejectedRemoteCalls = new HashSet<>();
        int noOfConcurrentReqSent = 20;
        for (int i = 0; i < noOfConcurrentReqSent; i++) {
            new Thread(() -> {
                try {
//                    Object returnValue = callService(bulkhead);
                    Callable<T> callable = () -> (T) resiliencyDataService.getDatafromRemoteServiceForFallbackPattern();
                    T returnValue = bulkhead.executeCallable(callable);
                    bulkhead.getEventPublisher()
                            .onCallPermitted(event -> {
                                successfulRemoteCalls.add(Thread.currentThread().getName());
                                //LOGGER.info("Successful remote call {} ", Thread.currentThread().getName());
                            })
                            .onCallRejected(event -> {
                                rejectedRemoteCalls.add(Thread.currentThread().getName());
                                //LOGGER.error("Rejected remote call {} ", Thread.currentThread().getName());
                            })
                            .onCallFinished(event -> LOGGER.debug("Call Finished {} ", event));
                    LOGGER.debug(Thread.currentThread().getName() + " successful. Return value = " + returnValue.getClass());
                    returnValues.add(returnValue);

                } catch (Exception e) {
                    //LOGGER.error(Thread.currentThread().getName() + " threw exception " + e.getMessage());
                    failedRequests.add(e);
                }
            }, "Remote-Call-" + (i + 1)).start();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                LOGGER.error(Thread.currentThread().getName() + " threw InterruptedException " + e.getMessage());
                e.printStackTrace();
            }
        }
        LOGGER.info("Number of successful requests {} number of rejected requests {}", successfulRemoteCalls, rejectedRemoteCalls);

        if (!failedRequests.isEmpty()) {
            Exception e = failedRequests.get(0);
            String message = e.getMessage() + ". No. of concurrent requests sent " + noOfConcurrentReqSent + " Successful:  " + returnValues.size();
            Exception wrappedException = new Exception(message, e);
            return (T) wrappedException;
        }
        return (T) returnValues.get(returnValues.size() - 1);
    }

//    private <T> T executeWithBulkhead(int maxConcurrentCalls, int maxWaitDuration, Supplier<T> supplier, Function<Throwable, T> fallback) {
//        LOGGER.info("Creating Bulkhead with maxConcurrentCalls {} maxWaitDuration {} ", maxConcurrentCalls, maxWaitDuration);
//        this.bulkhead = createBulkhead(maxConcurrentCalls, maxWaitDuration);
//        //Number of threads to start simultaneously
//        final CyclicBarrier gate = new CyclicBarrier(6); // Number of concurrent threads +1
//        AtomicInteger integer = new AtomicInteger(0);
//        final Object[] t1 = {null, null, null, null, null};
//        for (int i = 0; i < 5; i++) {
//
//            Thread t = new Thread(() -> {
//
//                try {
//                    gate.await();//Waits until all parties have invoked await on this barrier.
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                } catch (BrokenBarrierException e) {
//                    e.printStackTrace();
//                }
//                t1[integer.get()] = Bulkhead.decorateSupplier(bulkhead, supplier).get();
//                LOGGER.info(" Got result from thread T{} -> {} ", integer.get(), t1[integer.get()]);
//                integer.incrementAndGet();
//
//            });
//            t.start();
//        }
//
//        try {
//            gate.await();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        } catch (BrokenBarrierException e) {
//            e.printStackTrace();
//        }
//        return (T) t1[t1.length - 1];
    //return Bulkhead.decorateSupplier(bulkhead, supplier).get();
//        Runnable runnable = () -> supplier.get();
//        return bulkhead.executeRunnable(runnable);

//        ExecutorCompletionService completionService = new ExecutorCompletionService(Executors.newFixedThreadPool(4));
//        T returnValue = null;
//        try {
//            for (int i = 0; i < 5; i++) {
//                Future task = completionService.submit(() -> Bulkhead.decorateSupplier(bulkhead, supplier).get());
//                Object val = task.get();
//                LOGGER.info(" Got result from thread T{} -> {} ", i, val);
//                returnValue = (T) val;
//
//            }
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }  catch (ExecutionException e) {
//            e.printStackTrace();
//        }
//
//        return returnValue;

//    }

    // If bulkhead has space available, entry is guaranteed and immediate else maxWaitDuration
    // specifies the maximum amount of time which the calling thread will wait to enter the bulkhead.
    private Bulkhead createBulkhead(int maxConcurrentCalls, int maxWaitDuration) {
        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(maxConcurrentCalls)
                .maxWaitDuration(Duration.ofMillis(maxWaitDuration))
                .build();

        BulkheadRegistry bulkheadRegistry = BulkheadRegistry.of(bulkheadConfig);
        return bulkheadRegistry.bulkhead(DATA_SERVICE);
    }

    private RateLimiter createRateLimiter() {
        // Rate limiter to allow 5 calls every 5 seconds and keep other calls waiting until maximum of 10 seconds.
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofMillis(10000))
                .limitForPeriod(5)
                .timeoutDuration(Duration.ofMillis(2000))
                .build();
        RateLimiterRegistry rateLimiterRegistry = RateLimiterRegistry.of(config);
        return rateLimiterRegistry.rateLimiter(DATA_SERVICE);
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
                .maxAttempts(3)
                .retryExceptions(ConnectException.class, ResourceAccessException.class)
                .build();
    }

    private MockClientServiceResponse fallback(Throwable ex) {
        return resiliencyDataService.fallbackOnFailure();
    }
}
