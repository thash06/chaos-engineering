package com.fidelity.fbt.resiliency.refapp.controller;

import com.fidelity.fbt.resiliency.refapp.service.ResiliencyDataService;
import com.fidelity.fbt.resiliency.refapp.util.DecoratorUtil;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@RestController
@RequestMapping("resiliency-pattern/annotated")
public class AnnotatedController<T,R> {
    private static Logger LOGGER = LoggerFactory.getLogger(AnnotatedController.class);
    private static final String DATA_SERVICE = "data-service";
    private AtomicInteger atomicInteger = new AtomicInteger(0);

    /**
     * Data layer dependency for invoking data methods
     */
    private final ResiliencyDataService resiliencyDataService;
    private final DecoratorUtil<T,R> decoratorUtil;

    public AnnotatedController(ResiliencyDataService resiliencyDataService, DecoratorUtil<T, R> decoratorUtil) {
        this.resiliencyDataService = resiliencyDataService;
        this.decoratorUtil = decoratorUtil;
    }

    @GetMapping("/retry")
    @Retry(name = DATA_SERVICE)
    public Object getMockOfferingsWithRetry(@RequestParam Boolean throwException) throws Exception {
        LOGGER.info("Invoking with Retry annotation {} ", atomicInteger.incrementAndGet());
        return callRemoteService(throwException);
    }

    @GetMapping("/circuit-breaker")
    //@Retry(name = DATA_SERVICE)
    @CircuitBreaker(name = DATA_SERVICE)
    public Object getMockOfferingsWithCircuitBreaker(@RequestParam Boolean throwException) throws Exception {
//        this.getClass().getMethod("getMockOfferingsWithRetry").getAnnotation(CircuitBreaker.class)
        LOGGER.info("Invoking with CircuitBreaker annotation {} ", atomicInteger.incrementAndGet());
        try {
            return callRemoteService(throwException);
        } catch (Exception e) {
            LOGGER.error(" Consuming exception {}", e.getMessage());
            throw e;
        }
        //return null;
    }

    private <T> T callRemoteService(String offerId) throws Exception {
        Supplier<Object> supplier = (Supplier<Object>) resiliencyDataService.getDatafromRemoteService(offerId, false);
        return (T) supplier;

    }

    private <T> T callRemoteService(Boolean throwException) throws Exception {
        Decorators.DecorateFunction<T, R> decoratedFunction =
                (Decorators.DecorateFunction<T, R>) decoratorUtil.decorateFunction(param -> (R) resiliencyDataService.getDatafromRemoteService(param));
        return (T) decoratedFunction.apply((T) throwException);

    }

}
