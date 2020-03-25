package com.fidelity.fbt.chaos.refapp.controller;

import com.fidelity.fbt.chaos.refapp.exception.ChaosEngineeringException;
import com.fidelity.fbt.chaos.refapp.model.MockDataServiceResponse;
import com.fidelity.fbt.chaos.refapp.service.ChaosEngineeringDataService;
import io.github.resilience4j.bulkhead.*;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.vavr.CheckedFunction1;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@RestController
@RequestMapping("decorated-services")
public class DecoratedController<T, R> {
    private static Logger LOGGER = LoggerFactory.getLogger(DecoratedController.class);
    public static final String THREAD_POOL_BULKHEAD_TIME_LIMITER = "time-limiter";
    private static final String SEMAPHORE_BULKHEAD = "semaphore-bulkhead";
    private static final String THREAD_POOL_BULKHEAD = "thread-pool-bulkhead";
    private static final String RETRY_SERVICE = "retry-for-bulkhead";

    private final ChaosEngineeringDataService chaosEngineeringDataService;
    private final ThreadPoolBulkhead threadPoolBulkhead;
    private final Bulkhead bulkhead;
    private final Retry retry;
    private final TimeLimiter timeLimiter;
    private AtomicInteger atomicInteger = new AtomicInteger(0);

    public DecoratedController(ChaosEngineeringDataService chaosEngineeringDataService) {
        this.chaosEngineeringDataService = chaosEngineeringDataService;
        int availableProcessors = Runtime.getRuntime()
                .availableProcessors();
        this.threadPoolBulkhead = createThreadPoolBulkhead(availableProcessors);
        this.bulkhead = createBulkhead(availableProcessors);
        RetryConfig retryConfig = createRetryConfig();
        retry = Retry.of(RETRY_SERVICE, retryConfig);
        timeLimiter = createTimeLimiter(500);
    }

    @GetMapping("/offerings")
    public MockDataServiceResponse offerings(@RequestParam Boolean throwException) throws ChaosEngineeringException, ExecutionException, InterruptedException {
        //LOGGER.debug("Invoking DecoratedController with Bulkhead count {} ", atomicInteger.incrementAndGet());
        if (throwException) {
            throw new ChaosEngineeringException("Something went wrong!!");
        }
        return callBulkheadDecoratedService();
    }

    @GetMapping("/offeringsWithRetry")
    public MockDataServiceResponse offeringsWithRetry(@RequestParam Boolean throwException) throws ChaosEngineeringException, ExecutionException, InterruptedException {
        //LOGGER.debug("Invoking DecoratedController with Bulkhead count {} ", atomicInteger.incrementAndGet());
        if (throwException) {
            throw new ChaosEngineeringException("Something went wrong!!");
        }
        return callBulkheadAndRetryDecoratedService();
    }

    /**
     * @return
     * @throws RuntimeException
     */
    @GetMapping("/offeringsById")
    public MockDataServiceResponse offeringsById(@RequestParam String offerId, @RequestParam Boolean throwException)
            throws Throwable {
        if (throwException) {
            throw new ChaosEngineeringException("Something went wrong!!");
        }
        return callBulkheadDecoratedService(offerId);
    }

    private MockDataServiceResponse callBulkheadDecoratedService() throws ExecutionException, InterruptedException {
        handlePublisherEvents(threadPoolBulkhead);
        Supplier<MockDataServiceResponse> serviceAsSupplier = createServiceAsSupplier();
//        Supplier<CompletionStage<MockDataServiceResponse>> decorate = Decorators.ofSupplier(serviceAsSupplier)
//                .withThreadPoolBulkhead(threadPoolBulkhead)
//                .decorate();
        //return decorate.get().toCompletableFuture().get();
        CompletableFuture<MockDataServiceResponse> future = Decorators
                .ofSupplier(() -> chaosEngineeringDataService.getMockOfferingsDataFromService())
                .withThreadPoolBulkhead(threadPoolBulkhead)
                .withTimeLimiter(timeLimiter, Executors.newSingleThreadScheduledExecutor())
                .withFallback(BulkheadFullException.class, (e) -> {
                    MockDataServiceResponse mockDataServiceResponse = new MockDataServiceResponse();
                    mockDataServiceResponse.setHostedRegion(String.format("Request failed due to bulkheadName {%s} BulkheadFullException", e.getMessage()));
                    return mockDataServiceResponse;

                })
                .withFallback(TimeoutException.class, (e) -> {
                    MockDataServiceResponse mockDataServiceResponse = new MockDataServiceResponse();
                    mockDataServiceResponse.setHostedRegion(String.format("Request failed due to TimeLimiter {%s} with duration {%s} due to TimeoutException",
                            timeLimiter.getName(),
                            timeLimiter.getTimeLimiterConfig().getTimeoutDuration()));
                    return mockDataServiceResponse;

                })
                .get().toCompletableFuture();
        return future.get();
    }

    private MockDataServiceResponse callBulkheadAndRetryDecoratedService() throws ExecutionException, InterruptedException {
        handlePublisherEvents(threadPoolBulkhead);
        Retry retryContext = Retry.ofDefaults("retry-for-bulkhead");
        handlePublishedEvents(retryContext);
        Supplier<MockDataServiceResponse> serviceAsSupplier = createServiceAsSupplier();

        Supplier<CompletionStage<MockDataServiceResponse>> decorate = Decorators.ofSupplier(serviceAsSupplier)
                .withThreadPoolBulkhead(threadPoolBulkhead)
                .withRetry(retryContext, Executors.newSingleThreadScheduledExecutor())
                .decorate();
        return decorate.get().toCompletableFuture().get();
    }

    private MockDataServiceResponse callBulkheadDecoratedService(String offerId) throws Throwable {
        handlePublisherEvents(bulkhead);
        Callable<MockDataServiceResponse> callable = () -> {
            LOGGER.info("Invoking DecoratedController with Bulkhead offerId: {} count {} ", offerId, atomicInteger.incrementAndGet());
            return chaosEngineeringDataService.getMockOfferingsDataFromService(offerId);
        };
        Callable<MockDataServiceResponse> decoratedCallable = Decorators.ofCallable(callable)
                .withBulkhead(bulkhead)
                .decorate();
        return Try.ofCallable(decoratedCallable)
                .onFailure(throwable -> {
                    LOGGER.error(" Failure reason {} ", throwable.getMessage(), throwable);
                    if (HttpServerErrorException.class.isInstance(throwable)) {
                        if (((HttpServerErrorException) throwable).getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR) {
                            LOGGER.error(" Failure reason -> Server threw exception {} ", throwable.getMessage());
                        }
                    } else {
                        LOGGER.error(" Failure reason -> Bulkhead exception {} ", throwable.getMessage());
                    }
                })
                .getOrElse(() -> {
                    MockDataServiceResponse mockResponse = new MockDataServiceResponse();
                    mockResponse.setHostedRegion(String.format("Request with OfferId {%s} failed due to bulkhead {%s} full", offerId, bulkhead.getName()));
                    return mockResponse;
                });
    }

    public Supplier<MockDataServiceResponse> createServiceAsSupplier() {
        handlePublisherEvents(bulkhead);
        Supplier<MockDataServiceResponse> mockDataServiceResponseSupplier = (() -> {
            LOGGER.info("Invoking DecoratedController with Bulkhead count {} ", atomicInteger.incrementAndGet());
            return chaosEngineeringDataService.getMockOfferingsDataFromService();
        });
        return mockDataServiceResponseSupplier;
    }

    private CheckedFunction1<String, MockDataServiceResponse> createServiceAsCheckedFunction() {
        CheckedFunction1<String, MockDataServiceResponse> stringMockDataServiceResponseFunction = ((offerId) -> {
            LOGGER.info("Invoking DecoratedController with Bulkhead offerId {} count {} ", offerId, atomicInteger.incrementAndGet());
            return chaosEngineeringDataService.getMockOfferingsDataFromService(offerId);
        });
        return stringMockDataServiceResponseFunction;
    }


    private void handlePublisherEvents(Bulkhead bulkhead) {
        bulkhead.getEventPublisher()
                .onCallPermitted(event -> LOGGER.debug("Bulkhead Successful remote call {} ", Thread.currentThread().getName()))
                .onCallRejected(event -> LOGGER.warn("Bulkhead Rejected remote call {} ", Thread.currentThread().getName()))
                .onCallFinished(event -> LOGGER.debug("Bulkhead Call Finished {} ", event));
    }

    private void handlePublisherEvents(ThreadPoolBulkhead threadPoolBulkhead) {
        threadPoolBulkhead.getEventPublisher()
                .onCallPermitted(event -> LOGGER.debug("ThreadPoolBulkhead Successful remote call {} ", Thread.currentThread().getName()))
                .onCallRejected(event -> LOGGER.warn("ThreadPoolBulkhead Rejected remote call {} ", Thread.currentThread().getName()))
                .onCallFinished(event -> LOGGER.debug("ThreadPoolBulkhead Call Finished {} ", event));
    }

    private void handlePublishedEvents(Retry retry) {
        retry.getEventPublisher()
                .onError(event -> LOGGER.error(" Retry Event on Error {}", event))
                .onRetry(event -> LOGGER.info(" Retry Event on Retry {}", event))
                .onSuccess(event -> LOGGER.info(" Retry Event on Success {}", event))
                .onEvent(event -> LOGGER.debug(" Retry Event occurred records all events Retry, error and success {}", event));
    }

    private RetryConfig createRetryConfig() {
        IntervalFunction intervalWithCustomExponentialBackoff = IntervalFunction
                .ofExponentialBackoff(500l, 5d);
        return RetryConfig.custom()
                .intervalFunction(intervalWithCustomExponentialBackoff)
                .maxAttempts(5)
                .retryExceptions(ConnectException.class, ResourceAccessException.class, HttpServerErrorException.class,
                        ExecutionException.class, WebClientResponseException.class)
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
                .queueCapacity(1)
                .keepAliveDuration(Duration.ofMillis(10))
                .build();
        LOGGER.info("ThreadPoolBulkheadConfig created with maxThreadPoolSize {} : coreThreadPoolSize {}",
                availableProcessors, coreThreadPoolSize);
        ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry = ThreadPoolBulkheadRegistry.of(threadPoolBulkheadConfig);
        return threadPoolBulkheadRegistry.bulkhead(THREAD_POOL_BULKHEAD);
    }

    private Bulkhead createBulkhead(int availableProcessors) {
        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(availableProcessors)
                .maxWaitDuration(Duration.ofMillis(100))
                .writableStackTraceEnabled(true)
                .build();

        BulkheadRegistry bulkheadRegistry = BulkheadRegistry.of(bulkheadConfig);
        return bulkheadRegistry.bulkhead(SEMAPHORE_BULKHEAD);
    }

}
