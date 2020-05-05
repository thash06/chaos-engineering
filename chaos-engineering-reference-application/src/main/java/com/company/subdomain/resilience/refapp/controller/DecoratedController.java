package com.company.subdomain.resilience.refapp.controller;

import com.company.subdomain.resilience.refapp.decorators.DecoratedSupplier;
import com.company.subdomain.resilience.refapp.exception.ChaosEngineeringException;
import com.company.subdomain.resilience.refapp.model.MockDataServiceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("decorated-services")
public class DecoratedController<T, R> {
    private static Logger LOGGER = LoggerFactory.getLogger(DecoratedController.class);
    private final DecoratedSupplier decoratedSupplier;

    public DecoratedController(DecoratedSupplier decoratedSupplier) {
        this.decoratedSupplier = decoratedSupplier;

    }

    @GetMapping("/offerings")
    public MockDataServiceResponse offerings(@RequestParam Boolean throwException) throws ChaosEngineeringException, ExecutionException, InterruptedException {
        return decoratedSupplier.callThreadPoolBulkheadAndTimeLimiterDecoratedService(throwException);
    }

    @GetMapping("/offeringsWithRetry")
    public MockDataServiceResponse offeringsWithRetry(@RequestParam Boolean throwException) throws ChaosEngineeringException, ExecutionException, InterruptedException {
        return decoratedSupplier.callBulkheadAndRetryDecoratedService(throwException);
    }

    @GetMapping("/simpleRetry")
    public MockDataServiceResponse offeringsWithSimpleRetry(@RequestParam Boolean throwException) throws ChaosEngineeringException, ExecutionException, InterruptedException {
        return decoratedSupplier.callRetryDecoratedService(throwException);
    }


    @GetMapping("/simpleCircuitBreaker")
    public MockDataServiceResponse offeringsWithSimpleCircuitBreaker(@RequestParam Boolean throwException) throws ChaosEngineeringException, ExecutionException, InterruptedException {
        return decoratedSupplier.callCircuitBreakerDecoratedService(throwException);
    }

    @GetMapping("/simpleBulkhead")
    public MockDataServiceResponse offeringsWithSimpleBulkhead(@RequestParam Boolean throwException)
            throws Throwable {
        return decoratedSupplier.callBulkheadDecoratedService(throwException);
    }

    @GetMapping("/simpleRateLimiter")
    public MockDataServiceResponse offeringsWithSimpleRateLimiter(@RequestParam Boolean throwException) throws ChaosEngineeringException, ExecutionException, InterruptedException {
        return decoratedSupplier.callRateLimiterDecoratedService(throwException);
    }

    @GetMapping("/simpleTimeLimiter")
    public MockDataServiceResponse offeringsWithSimpleTimeLimiter(@RequestParam Boolean throwException) throws ChaosEngineeringException, ExecutionException, InterruptedException {
        return decoratedSupplier.callTimeLimiterDecoratedService(throwException);
    }

    /**
     * @return
     * @throws RuntimeException
     */
    @GetMapping("/offeringsById")
    public MockDataServiceResponse offeringsById(@RequestParam String offerId, @RequestParam Boolean throwException) throws ChaosEngineeringException {
        try {
            return decoratedSupplier.callSemaphoreBulkheadDecoratedService(offerId, throwException);
        } catch (ChaosEngineeringException e) {
            LOGGER.error("Caught error in controller {}", e.getMessage());
            throw e;
        }
    }


    /**
     * @return
     * @throws RuntimeException
     */
    @GetMapping("/degradingService")
    public MockDataServiceResponse degradingOfferings(@RequestParam Boolean throwException) throws ChaosEngineeringException,
            InterruptedException, ExecutionException {
        try {
            return decoratedSupplier.callDegradingOfferingsUsingSemaphoreBulkheadDecoratedService(throwException);
        } catch (ChaosEngineeringException e) {
            LOGGER.error("Caught error in controller {}", e.getMessage());
            throw e;
        }
    }
}
