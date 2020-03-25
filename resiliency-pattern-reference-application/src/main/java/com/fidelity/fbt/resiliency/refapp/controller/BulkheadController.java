package com.fidelity.fbt.resiliency.refapp.controller;

import com.amazonaws.services.simpleworkflow.flow.core.TryCatch;
import com.fidelity.fbt.resiliency.refapp.model.MockClientServiceResponse;
import com.fidelity.fbt.resiliency.refapp.service.ResiliencyDataService;
import com.fidelity.fbt.resiliency.refapp.util.DecoratorUtil;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.decorators.Decorators;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpServerErrorException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@RestController
@RequestMapping("resiliency-pattern")
public class BulkheadController<T, R> {
    private static Logger LOGGER = LoggerFactory.getLogger(BulkheadController.class);
    private AtomicInteger atomicInteger = new AtomicInteger(0);
    private static final String DATA_SERVICE = "data-service";
    /**
     * Data layer dependency for invoking data methods
     */
    private final ResiliencyDataService resiliencyDataService;
    private final DecoratorUtil<T, R> decoratorUtil;

    public BulkheadController(ResiliencyDataService resiliencyDataService, DecoratorUtil<T, R> decoratorUtil) {
        this.resiliencyDataService = resiliencyDataService;
        this.decoratorUtil = decoratorUtil;
    }


    /**
     * @return This endpoint returns mock response for demonstrating fallback resiliency pattern
     */
    @GetMapping("/bulkhead")
    public Object getMockOfferings(@RequestParam int maxConcurrentCalls, @RequestParam int maxWaitDuration,@RequestParam Boolean throwException) {
        LOGGER.info("Invoking BulkheadController count {} ", atomicInteger.incrementAndGet());
        //return executeWithBulkhead(maxConcurrentCalls, resiliencyDataService::getDatafromRemoteServiceForFallbackPattern, this::fallback);
        return executeWithBulkhead(createBulkhead(maxConcurrentCalls, maxWaitDuration), throwException);

    }


    private <T> T executeWithBulkhead(Bulkhead bulkhead, boolean throwException) {
        LOGGER.info("Created Bulkhead with {} max concurrent calls maxWaitDuration {} ",
                bulkhead.getBulkheadConfig().getMaxConcurrentCalls(), bulkhead.getBulkheadConfig().getMaxWaitDuration());
        List<Object> returnValues = new ArrayList<>();
        Set<String> rejectedRemoteCalls = new HashSet<>();
        int noOfConcurrentReqSent = 20;
        for (int i = 0; i < noOfConcurrentReqSent; i++) {
            new Thread(() -> {
                try {
                    T returnValue = callRemoteService(bulkhead, throwException);
                    returnValues.add(returnValue);
                } catch (Exception e) {
                    rejectedRemoteCalls.add(Thread.currentThread().getName() + " due to " + e.getMessage());
                }
            }, "Remote-Call-" + (i + 1)).start();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                LOGGER.error(Thread.currentThread().getName() + " threw InterruptedException " + e.getMessage());
            }
        }
        LOGGER.info("Number of successful requests {} number of rejected requests {}", returnValues.size(), rejectedRemoteCalls.size());

        if (!rejectedRemoteCalls.isEmpty()) {
            String message = "Following calls failed: " + rejectedRemoteCalls.stream().reduce((s, s2) -> String.join("\n ", s, s2)).get();
            Exception wrappedException = new Exception(message);
            return (T) wrappedException;
        }
        return (T) returnValues.get(returnValues.size() - 1);
    }

    private <T> T callRemoteService(Bulkhead bulkhead, boolean throwException) throws Exception {
        Callable<T> callable = () -> (T) resiliencyDataService.getDatafromRemoteService(throwException);
        Callable<T> decoratedCallable = Decorators.ofCallable(callable)
                .withBulkhead(bulkhead)
                .decorate();
        handlePublisherEvents(bulkhead);
        return Try.ofCallable(decoratedCallable)
                .onFailure(throwable -> {
                    LOGGER.error(" Failure reason {} ", throwable.getMessage(), throwable);
                    if(HttpServerErrorException.class.isInstance(throwable)){
                        if(((HttpServerErrorException) throwable).getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR){
                            LOGGER.error(" Failure reason -> Server threw exception {} ", throwable.getMessage());
                        }
                    }
                    else{
                        LOGGER.error(" Failure reason -> Bulkhead exception {} ", throwable.getMessage());
                    }
                })
                .get()
                //.getOrElseThrow(() ->new Exception("{Bulkhead full : " + bulkhead.getName() + ". Could return a cached response here}")
        ;
    }

    private void handlePublisherEvents(Bulkhead bulkhead) {
        bulkhead.getEventPublisher()
                .onCallPermitted(event -> LOGGER.debug("Successful remote call {} ", Thread.currentThread().getName()))
                .onCallRejected(event -> LOGGER.warn("Rejected remote call {} ", Thread.currentThread().getName()))
                .onCallFinished(event -> LOGGER.debug("Call Finished {} ", event));
    }

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

    private MockClientServiceResponse fallback() {
        return resiliencyDataService.fallbackOnFailure();
    }
//    private <T> T execute(Supplier<T> supplier, Function<Throwable, T> fallback) {
//        return Decorators.ofSupplier(supplier)
//                .withRetry(retry)
//                .withCircuitBreaker(circuitBreaker)
//                .withBulkhead(bulkhead)
//                .withRateLimiter(rateLimiter)
//                .withFallback(Arrays.asList(ChaosEngineeringException.class), fallback)
//                .get();
//
//    }

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


}
