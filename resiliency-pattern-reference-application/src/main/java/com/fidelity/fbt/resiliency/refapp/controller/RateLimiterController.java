package com.fidelity.fbt.resiliency.refapp.controller;

import com.fidelity.fbt.resiliency.refapp.model.MockClientServiceResponse;
import com.fidelity.fbt.resiliency.refapp.service.ResiliencyDataService;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.ConnectException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("resiliency-pattern")
public class RateLimiterController {
    private static Logger LOGGER = LoggerFactory.getLogger(RateLimiterController.class);
    private AtomicInteger atomicInteger = new AtomicInteger(0);
    private static final String DATA_SERVICE = "data-service";
    /**
     * Data layer dependency for invoking data methods
     */
    private final ResiliencyDataService resiliencyDataService;
    private RateLimiter rateLimiter;

    public RateLimiterController(
            ResiliencyDataService resiliencyDataService) {
        //this.chaosEngineeringDataService = chaosEngineeringDataService;
        this.resiliencyDataService = resiliencyDataService;
    }


    /**
     * @return This endpoint returns mock response for demonstrating fallback resiliency pattern
     */
    @GetMapping("/rate-limiter")
    public Object getMockOfferings(@RequestParam int limitForPeriod, @RequestParam int windowInSeconds,
                                   @RequestParam int waitTimeForThread, @RequestParam int numOfTestRequests) {
        LOGGER.info("Invoking RateLimiterController count {} ", atomicInteger.incrementAndGet());
        return executeWithRateLimiter(createRateLimiter(limitForPeriod, windowInSeconds, waitTimeForThread), numOfTestRequests);

    }


    private <T> T executeWithRateLimiter(RateLimiter rateLimiter, int numOfTestRequests) {
        List<Object> returnValues = new ArrayList<>();
        Set<String> successfulRemoteCalls = new HashSet<>();
        Set<String> rejectedRemoteCalls = new HashSet<>();
        for (int i = 0; i < numOfTestRequests; i++) {
            callRemoteService(rateLimiter, returnValues, successfulRemoteCalls, rejectedRemoteCalls);
        }
        LOGGER.info("Number of successful requests {} number of rejected requests {}", successfulRemoteCalls, rejectedRemoteCalls);

        if (!rejectedRemoteCalls.isEmpty()) {
            String message = "Following calls failed: " + rejectedRemoteCalls.stream().reduce((s, s2) -> String.join(", ", s, s2)).get();
            //String message = e.getMessage() + ". No. of concurrent requests sent " + noOfRequests + " Successful:  " + returnValues.size();
            Exception wrappedException = new Exception(message);
            return (T) wrappedException;
        }
        return (T) returnValues.get(returnValues.size() - 1);
    }

    private <T> void callRemoteService(RateLimiter rateLimiter, List<Object> returnValues, Set<String> successfulRemoteCalls, Set<String> rejectedRemoteCalls) {
        try {
            Callable<T> callable = () -> (T) resiliencyDataService.getDatafromRemoteService();
            Callable<T> decoratedCallable = Decorators.ofCallable(callable)
                    .withFallback(Arrays.asList(ConnectException.class), throwable -> (T) fallback(throwable))
                    .withRateLimiter(rateLimiter)
                    .decorate();

            Try.ofCallable(decoratedCallable)
                    .onFailure(throwable -> rejectedRemoteCalls.add(throwable.toString()))
                    .onSuccess(t -> returnValues.add(t));
            rateLimiter.getEventPublisher()
                    .onSuccess(event -> {
                        successfulRemoteCalls.add(Thread.currentThread().getName());
                        LOGGER.debug("Successful remote call {} ", Thread.currentThread().getName());
                    })
                    .onFailure(event -> {
                        rejectedRemoteCalls.add(Thread.currentThread().getName());
                        LOGGER.error("Rejected remote call {} ", Thread.currentThread().getName());
                    });
        } catch (Exception e) {
            LOGGER.error(Thread.currentThread().getName() + " threw exception " + e.getMessage());
        }
    }

    private RateLimiter createRateLimiter(int limitForPeriod, int windowInSeconds, int waitTimeForThread) {
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(windowInSeconds))
                .limitForPeriod(limitForPeriod)
                .timeoutDuration(Duration.ofMillis(waitTimeForThread))
                .build();

        RateLimiterRegistry rateLimiterRegistry = RateLimiterRegistry.of(rateLimiterConfig);


        return rateLimiterRegistry.rateLimiter(DATA_SERVICE, rateLimiterConfig);


    }

    private MockClientServiceResponse fallback(Throwable ex) {
        return resiliencyDataService.fallbackOnFailure();
    }
}
