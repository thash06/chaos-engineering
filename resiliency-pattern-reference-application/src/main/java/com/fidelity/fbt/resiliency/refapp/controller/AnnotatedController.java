package com.fidelity.fbt.resiliency.refapp.controller;

import com.fidelity.fbt.resiliency.refapp.service.ResiliencyDataService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("resiliency-pattern/annotated")
public class AnnotatedController {
    private static Logger LOGGER = LoggerFactory.getLogger(AnnotatedController.class);
    private static final String DATA_SERVICE = "data-service";
    private AtomicInteger atomicInteger = new AtomicInteger(0);

    /**
     * Data layer dependency for invoking data methods
     */
    private final ResiliencyDataService resiliencyDataService;

    public AnnotatedController(ResiliencyDataService resiliencyDataService) {
        this.resiliencyDataService = resiliencyDataService;
    }

    @GetMapping("/retry")
    @Retry(name = DATA_SERVICE)
    public Object getMockOfferingsWithRetry() throws Exception {
        LOGGER.info("Invoking with Retry annotation {} ", atomicInteger.incrementAndGet());
        return callRemoteService();
    }

    @GetMapping("/circuit-breaker")
    //@Retry(name = DATA_SERVICE)
    @CircuitBreaker(name = DATA_SERVICE)
    public Object getMockOfferingsWithCircuitBreaker() throws Exception {
//        this.getClass().getMethod("getMockOfferingsWithRetry").getAnnotation(CircuitBreaker.class)
        LOGGER.info("Invoking with CircuitBreaker annotation {} ", atomicInteger.incrementAndGet());
        try {
            return callRemoteService();
        } catch (Exception e) {
            LOGGER.error(" Consuming exception {}", e.getMessage());
            throw e;
        }
        //return null;
    }

    private <T> T callRemoteService(String offerId) throws Exception {
        return (T) resiliencyDataService.getDatafromRemoteService(offerId);

    }

    private <T> T callRemoteService() throws Exception {
        return (T) resiliencyDataService.getDatafromRemoteService();

    }

}
