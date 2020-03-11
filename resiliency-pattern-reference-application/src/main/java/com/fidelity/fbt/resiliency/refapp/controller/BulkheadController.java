package com.fidelity.fbt.resiliency.refapp.controller;

import com.fidelity.fbt.resiliency.refapp.model.MockClientServiceResponse;
import com.fidelity.fbt.resiliency.refapp.service.ResiliencyDataService;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
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

/**
 * @author souadhik
 * Controller class for resilient client application
 */
@RestController
@RequestMapping("resiliency-pattern")
public class BulkheadController {
    private static Logger LOGGER = LoggerFactory.getLogger(BulkheadController.class);
    private AtomicInteger atomicInteger = new AtomicInteger(0);
    private static final String DATA_SERVICE = "data-service";
    /**
     * Data layer dependency for invoking data methods
     */
    private final ResiliencyDataService resiliencyDataService;
    private Bulkhead bulkhead;
    private Retry retry;

    public BulkheadController(
            ResiliencyDataService resiliencyDataService) {
        //this.chaosEngineeringDataService = chaosEngineeringDataService;
        this.resiliencyDataService = resiliencyDataService;
    }


    /**
     * @return This endpoint returns mock response for demonstrating fallback resiliency pattern
     */
    @GetMapping("/bulkhead")
    public Object getMockOfferings(@RequestParam int maxConcurrentCalls, @RequestParam int maxWaitDuration) {
        LOGGER.info("Invoking ResiliencyPatternsController count {} ", atomicInteger.incrementAndGet());
        //return executeWithBulkhead(maxConcurrentCalls, resiliencyDataService::getDatafromRemoteServiceForFallbackPattern, this::fallback);
        return executeWithBulkhead(createBulkhead(maxConcurrentCalls, maxWaitDuration));

    }


    private <T> T executeWithBulkhead(Bulkhead bulkhead) {
        LOGGER.info("Created Bulkhead with {} max concurrent calls maxWaitDuration {} ",
                bulkhead.getBulkheadConfig().getMaxConcurrentCalls(), bulkhead.getBulkheadConfig().getMaxWaitDuration());
        List<Object> returnValues = new ArrayList<>();
        Set<String> successfulRemoteCalls = new HashSet<>();
        Set<String> rejectedRemoteCalls = new HashSet<>();
        int noOfConcurrentReqSent = 20;
        for (int i = 0; i < noOfConcurrentReqSent; i++) {
            new Thread(() -> {
                callRemoteService(bulkhead, returnValues, successfulRemoteCalls, rejectedRemoteCalls);
            }, "Remote-Call-" + (i + 1)).start();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                LOGGER.error(Thread.currentThread().getName() + " threw InterruptedException " + e.getMessage());
            }
        }
        LOGGER.info("Number of successful requests {} number of rejected requests {}", successfulRemoteCalls, rejectedRemoteCalls);

        if (!rejectedRemoteCalls.isEmpty()) {
            String message = "Following calls failed: " + rejectedRemoteCalls.stream().reduce((s, s2) -> String.join(", ", s, s2)).get();
            //String message = e.getMessage() + ". No. of concurrent requests sent " + noOfConcurrentReqSent + " Successful:  " + returnValues.size();
            Exception wrappedException = new Exception(message);
            return (T) wrappedException;
        }
        return (T) returnValues.get(returnValues.size() - 1);
    }

    private <T> void callRemoteService(Bulkhead bulkhead, List<Object> returnValues, Set<String> successfulRemoteCalls, Set<String> rejectedRemoteCalls) {
        try {
            Callable<T> callable = () -> (T) resiliencyDataService.getDatafromRemoteServiceForFallbackPattern();
            Callable<T> decoratedCallable = Decorators.ofCallable(callable)
                    .withFallback(Arrays.asList(ConnectException.class), throwable -> (T) fallback(throwable))
                    .withBulkhead(bulkhead)
                    .decorate();

            Try.ofCallable(decoratedCallable)
                    .onFailure(throwable -> rejectedRemoteCalls.add(throwable.toString()))
                    .onSuccess(t -> returnValues.add(t));
//            T returnValue = bulkhead.executeCallable(decoratedCallable);
            bulkhead.getEventPublisher()
                    .onCallPermitted(event -> {
                        successfulRemoteCalls.add(Thread.currentThread().getName());
                        LOGGER.debug("Successful remote call {} ", Thread.currentThread().getName());
                    })
                    .onCallRejected(event -> {
                        rejectedRemoteCalls.add(Thread.currentThread().getName());
                        LOGGER.error("Rejected remote call {} ", Thread.currentThread().getName());
                    })
                    .onCallFinished(event -> {
                        LOGGER.debug("Call Finished {} ", event);
                    });
        } catch (Exception e) {
            LOGGER.error(Thread.currentThread().getName() + " threw exception " + e.getMessage());
        }
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

    private MockClientServiceResponse fallback(Throwable ex) {
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
