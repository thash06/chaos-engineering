package com.fidelity.fbt.resiliency.refapp.controller;

import com.fidelity.fbt.resiliency.refapp.model.MockClientServiceResponse;
import com.fidelity.fbt.resiliency.refapp.service.ResiliencyDataService;
import com.fidelity.fbt.resiliency.refapp.util.DecoratorUtil;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@RestController
@RequestMapping("resiliency-pattern")
public class RetryController<T, R> {
    private static Logger LOGGER = LoggerFactory.getLogger(RetryController.class);
    private AtomicInteger atomicInteger = new AtomicInteger(0);
    private static final String DATA_SERVICE = "data-service";
    /**
     * Data layer dependency for invoking data methods
     */
    private final ResiliencyDataService resiliencyDataService;
    private final DecoratorUtil<T, R> decoratorUtil;
    private Retry retry;
    private RetryRegistry retryRegistry;

    public RetryController(ResiliencyDataService resiliencyDataService, DecoratorUtil<T, R> decoratorUtil) {
        this.resiliencyDataService = resiliencyDataService;
        this.decoratorUtil = decoratorUtil;
    }


    /**
     * @return This endpoint returns mock response for demonstrating fallback resiliency pattern
     */
    @GetMapping("/retry")
    public Object getMockOfferings(@RequestParam Boolean throwException) {
        LOGGER.info("Invoking RetryController count {} ", atomicInteger.incrementAndGet());
        Decorators.DecorateFunction<T, R> decoratedFunction =
                decoratorUtil.decorateFunction(throwException1 -> (R) resiliencyDataService.getDatafromRemoteService(throwException1));
        return executeWithRetry(decoratedFunction, throwException);
    }

    private <R> R executeWithRetry(Decorators.DecorateFunction<T,R> checkedFunction, Boolean throwException) {
        RetryConfig retryConfig = createRetryConfig();
        retryRegistry = RetryRegistry.of(retryConfig);
        retry = Retry.of(DATA_SERVICE, retryConfig);
        handlePublishedEvents();
        Function<T,R> booleanObjectDecorateFunction = checkedFunction.withRetry(retry).decorate();
        return (R) booleanObjectDecorateFunction.apply((T) throwException);
    }

    private void handlePublishedEvents() {
        retry.getEventPublisher()
                .onError(event -> LOGGER.error(" Event on Error {}", event))
                .onRetry(event -> LOGGER.info(" Event on Retry {}", event))
                .onSuccess(event -> LOGGER.info(" Event on Success {}", event))
                .onEvent(event -> LOGGER.debug(" Event occurred records all events Retry, error and success {}", event));
    }

    private RetryConfig createRetryConfig() {
        IntervalFunction intervalWithCustomExponentialBackoff = IntervalFunction
                .ofExponentialBackoff(500l, 5d);
        return RetryConfig.custom()
                .intervalFunction(intervalWithCustomExponentialBackoff)
                .maxAttempts(5)
                .retryExceptions(ConnectException.class, ResourceAccessException.class, HttpServerErrorException.class)
                .retryOnResult(response -> {
                    LOGGER.info(" Retry if was null {}", response);
                    return response == null;
                })
                .build();
    }
}
