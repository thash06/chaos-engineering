package com.fidelity.fbt.chaos.refapp.decorators;

import com.fidelity.fbt.chaos.refapp.exception.ChaosEngineeringException;
import com.fidelity.fbt.chaos.refapp.model.MockDataServiceResponse;
import com.fidelity.fbt.chaos.refapp.service.ChaosEngineeringDataService;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.vavr.CheckedFunction0;
import io.vavr.CheckedFunction1;
import io.vavr.CheckedFunction2;
import io.vavr.Function0;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Component
public class DecoratedSupplier {
    private static Logger LOGGER = LoggerFactory.getLogger(DecoratedSupplier.class);


    private final ChaosEngineeringDataService chaosEngineeringDataService;
    private final DecoratorFactory decoratorFactory;

    private AtomicInteger atomicInteger = new AtomicInteger(0);

    public DecoratedSupplier(ChaosEngineeringDataService chaosEngineeringDataService, DecoratorFactory decoratorFactory) {
        this.chaosEngineeringDataService = chaosEngineeringDataService;
        this.decoratorFactory = decoratorFactory;
    }


    /**
     * @param throwException
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     * @throws ChaosEngineeringException
     */
    public MockDataServiceResponse callThreadPoolBulkheadAndTimeLimiterDecoratedService(boolean throwException)
            throws ExecutionException, InterruptedException, ChaosEngineeringException {
        handlePublishedEvents(decoratorFactory.threadPoolBulkhead);
//        Supplier<MockDataServiceResponse> serviceAsSupplier = createServiceAsSupplier();
//        Supplier<CompletionStage<MockDataServiceResponse>> decorate = Decorators.ofSupplier(serviceAsSupplier)
//                .withThreadPoolBulkhead(threadPoolBulkhead)
//                .decorate();
        //return decorate.get().toCompletableFuture().get();
        CompletableFuture<MockDataServiceResponse> future = Decorators
                .ofSupplier(() -> chaosEngineeringDataService.getMockOfferingsDataFromService(throwException))
                .withThreadPoolBulkhead(decoratorFactory.threadPoolBulkhead)
                .withTimeLimiter(decoratorFactory.timeLimiter, Executors.newSingleThreadScheduledExecutor())
                .withFallback(BulkheadFullException.class, (e) -> fallbackResponse(
                        String.format("Request failed due to bulkheadName {%s} BulkheadFullException", e.getMessage())))
                .withFallback(TimeoutException.class, (e) -> fallbackResponse(
                        String.format("Request failed due to TimeLimiter {%s} with duration {%s} due to TimeoutException",
                                decoratorFactory.timeLimiter.getName(), decoratorFactory.timeLimiter.getTimeLimiterConfig().getTimeoutDuration())))
                .get().toCompletableFuture();
        return future.get();
    }


    /**
     * @param throwException
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     * @throws ChaosEngineeringException
     */
    public MockDataServiceResponse callBulkheadAndRetryDecoratedService(boolean throwException) throws ExecutionException, InterruptedException, ChaosEngineeringException {
        handlePublishedEvents(decoratorFactory.threadPoolBulkhead);
        Retry retryContext = Retry.of("retry-for-bulkhead", RetryConfig.ofDefaults());
        handlePublishedEvents(retryContext);
        Supplier<MockDataServiceResponse> serviceAsSupplier = createServiceAsSupplier(throwException);

        Supplier<CompletionStage<MockDataServiceResponse>> decorate = Decorators.ofSupplier(serviceAsSupplier)
                .withThreadPoolBulkhead(decoratorFactory.threadPoolBulkhead)
                .withRetry(retryContext, Executors.newSingleThreadScheduledExecutor())
                .decorate();

        CompletableFuture<MockDataServiceResponse> mockDataServiceResponseCompletionStage =
                decorate.get().toCompletableFuture();
        //return mockDataServiceResponseCompletionStage.getNow( getFallbackMockDataServiceResponse("Failed with Bulkhead and Retry"));
        return mockDataServiceResponseCompletionStage.get();
    }

    /**
     * @param throwException
     * @return
     * @throws ChaosEngineeringException
     */
    public MockDataServiceResponse callDegradingOfferingsUsingSemaphoreBulkheadDecoratedService(boolean throwException)
            throws ChaosEngineeringException, ExecutionException, InterruptedException {
        handlePublishedEvents(decoratorFactory.circuitBreaker);
        handlePublishedEvents(decoratorFactory.bulkhead);
        CheckedFunction0<MockDataServiceResponse> checkedFunction0 =
                CheckedFunction0.of(() -> chaosEngineeringDataService.getDegradedMockOfferings(throwException));
        Function0<MockDataServiceResponse> unchecked = checkedFunction0.unchecked();
//        Supplier<MockDataServiceResponse> timeLimiterDecoratedSupplier =
//                Bulkhead.decorateSupplier(decoratorFactory.bulkhead, unchecked);
        Supplier<CompletableFuture<MockDataServiceResponse>> futureSupplier = () -> CompletableFuture.supplyAsync(unchecked);
        Callable<MockDataServiceResponse> timeLimiterDecoratedSupplier =
                TimeLimiter.decorateFutureSupplier(decoratorFactory.timeLimiter, futureSupplier);

        CompletableFuture<MockDataServiceResponse> completableFutureCompletionStage =
                Decorators.ofSupplier(() -> chaosEngineeringDataService.getDegradedMockOfferings(throwException))
                        .withThreadPoolBulkhead(decoratorFactory.threadPoolBulkhead)
                        .withTimeLimiter(decoratorFactory.timeLimiter, Executors.newSingleThreadScheduledExecutor())
                        .withCircuitBreaker(decoratorFactory.circuitBreaker)
                        .withRetry(decoratorFactory.retry, Executors.newSingleThreadScheduledExecutor())
                        .withFallback(BulkheadFullException.class, (e) -> {
                            LOGGER.info(" Recovering from BulkheadFullException {} ", e.getMessage());
                            return fallbackResponse(
                                    String.format("Request failed due to bulkheadName {%s} BulkheadFullException", e.getMessage()));
                        })
                        .withFallback(CallNotPermittedException.class, (e) -> {
                            LOGGER.info(" Recovering from CallNotPermittedException {} ", e.getMessage());
                            return fallbackResponse(
                                    String.format("Request failed due to circuitbreaker {%s} CallNotPermitted", e.getMessage()));
                        })
                        .withFallback(TimeoutException.class, (e) ->
                                {
                                    LOGGER.info(" Recovering from TimeoutException {} ", e.getMessage());
                                    return fallbackResponse(
                                            String.format("Request failed due to TimeLimiter {%s} with duration {%s} due to TimeoutException",
                                                    decoratorFactory.timeLimiter.getName(), decoratorFactory.timeLimiter.getTimeLimiterConfig().getTimeoutDuration()));
                                }
                        )
                        .get().toCompletableFuture();
        return completableFutureCompletionStage.get();
//        return Try.ofCallable(decoratedCallable)
//                .onFailure(throwable -> LOGGER.error(" Failure reason {} ", throwable.getMessage()))
//
//                .get();

//        Callable<MockDataServiceResponse> decoratedCallable = Decorators.ofCallable(
//                () -> chaosEngineeringDataService.getDegradedMockOfferings(throwException))
//                .withCircuitBreaker(decoratorFactory.circuitBreaker)
//                .decorate();
//        return Try.ofCallable(decoratedCallable)
//                .onFailure(throwable -> LOGGER.error(" Failure reason {} ", throwable.getMessage(), throwable))
//                .recoverWith(throwable -> Try.success(fallbackResponse(
//                        String.format("Request failed due to circuit-breaker {%s}", decoratorFactory.circuitBreaker.getName()))))
//                .get();
    }

    public MockDataServiceResponse callSemaphoreBulkheadDecoratedService(String offerId, boolean throwException) throws ChaosEngineeringException {
        handlePublishedEvents(decoratorFactory.bulkhead);
        if (throwException) {
            return checkedFunctionWithBulkheadDecorator(offerId, throwException);
        } else {
            return callableWithBulkheadDecorator(offerId, throwException);
        }
    }

    private MockDataServiceResponse callableWithBulkheadDecorator(String offerId, boolean throwException) {
        Callable<MockDataServiceResponse> callable = () -> {
            LOGGER.info("Invoking DecoratedController with Bulkhead offerId: {} count {} ", offerId, atomicInteger.incrementAndGet());
            return chaosEngineeringDataService.getMockOfferingsDataFromService(offerId, throwException);
        };
        Callable<MockDataServiceResponse> decoratedCallable = Decorators.ofCallable(callable)
                .withBulkhead(decoratorFactory.bulkhead)
                .decorate();
        return Try.ofCallable(decoratedCallable)
                .onFailure(throwable -> LOGGER.error(" Failure reason {} ", throwable.getMessage(), throwable))
                .recoverWith(throwable -> Try.success(fallbackResponse(
                        String.format("Request with OfferId {%s} failed due to bulkhead {%s} full", offerId, decoratorFactory.bulkhead.getName()))))
                .get();
    }

    private MockDataServiceResponse checkedFunctionWithBulkheadDecorator(String offerId, boolean throwException) throws ChaosEngineeringException {
//        CheckedFunction1<String, MockDataServiceResponse> checkedFunction1 = createServiceAsCheckedFunction(throwException);
//        CheckedFunction1<String, MockDataServiceResponse> checkedFunction11 = Bulkhead.decorateCheckedFunction(decoratorFactory.bulkhead, checkedFunction1);
        CheckedFunction0<MockDataServiceResponse> checkedFunction0 = CheckedFunction0.of(() -> chaosEngineeringDataService.getMockOfferingsDataFromService(offerId, throwException));
        Function0<MockDataServiceResponse> unchecked = checkedFunction0.unchecked();
        Supplier<MockDataServiceResponse> mockDataServiceResponseSupplier = Bulkhead.decorateSupplier(decoratorFactory.bulkhead, unchecked);
        return Try.ofSupplier(mockDataServiceResponseSupplier)
                .onFailure(throwable -> LOGGER.error(" Failure reason {} ", throwable.getMessage()))
//                .recoverWith(throwable -> Try.success(fallbackResponse(
//                        String.format("Request with OfferId {%s} failed due to bulkhead {%s} full", offerId, decoratorFactory.bulkhead.getName())))
//                )
                .get();
    }

    //////////////// Private Methods
    private Supplier<MockDataServiceResponse> createServiceAsSupplier(boolean throwException) {
        handlePublishedEvents(decoratorFactory.bulkhead);
        Supplier<MockDataServiceResponse> mockDataServiceResponseSupplier = (() -> {
            LOGGER.info("Invoking DecoratedController with Bulkhead count {} ", atomicInteger.incrementAndGet());
            return chaosEngineeringDataService.getMockOfferingsDataFromService(throwException);
        });
        return mockDataServiceResponseSupplier;
    }

    private CheckedFunction1<String, MockDataServiceResponse> createServiceAsCheckedFunction(boolean throwException) throws ChaosEngineeringException {
        CheckedFunction1<String, MockDataServiceResponse> stringMockDataServiceResponseFunction = ((offerId) -> {
            LOGGER.info("Invoking DecoratedController with Bulkhead offerId {} count {} ", offerId, atomicInteger.incrementAndGet());
            return chaosEngineeringDataService.getMockOfferingsDataFromService(offerId, throwException);
        });
        return stringMockDataServiceResponseFunction;
    }

    private CheckedFunction2<String, Boolean, MockDataServiceResponse> createServiceAsCheckedFunction(String offerId, boolean throwException)
            throws ChaosEngineeringException {
        CheckedFunction2<String, Boolean, MockDataServiceResponse> stringMockDataServiceResponseFunction = ((id, exception) -> {
            LOGGER.info("Invoking DecoratedController with Bulkhead offerId {} count {} ", id, atomicInteger.incrementAndGet());
            return chaosEngineeringDataService.getMockOfferingsDataFromService(id, exception);
        });
        return stringMockDataServiceResponseFunction;
    }

    private MockDataServiceResponse fallbackResponse(String message) {
        MockDataServiceResponse mockDataServiceResponse = new MockDataServiceResponse();
        mockDataServiceResponse.setHostedRegion(message);
        return mockDataServiceResponse;
    }

    //Monitoring by just logging
    private void handlePublishedEvents(Bulkhead bulkhead) {
        bulkhead.getEventPublisher()
                .onCallPermitted(event -> LOGGER.debug("Bulkhead Successful remote call {} ", Thread.currentThread().getName()))
                .onCallRejected(event -> LOGGER.debug("Bulkhead Rejected remote call {} ", Thread.currentThread().getName()))
                .onCallFinished(event -> LOGGER.debug("Bulkhead Call Finished {} ", event));
    }

    private void handlePublishedEvents(ThreadPoolBulkhead threadPoolBulkhead) {
        threadPoolBulkhead.getEventPublisher()
                .onCallPermitted(event -> LOGGER.debug("ThreadPoolBulkhead Successful remote call {} ", Thread.currentThread().getName()))
                .onCallRejected(event -> LOGGER.info("ThreadPoolBulkhead Rejected remote call {} ", Thread.currentThread().getName()))
                .onCallFinished(event -> LOGGER.debug("ThreadPoolBulkhead Call Finished {} ", event));
    }

    private void handlePublishedEvents(Retry retry) {
        retry.getEventPublisher()
                .onError(event -> LOGGER.error(" Retry Event on Error {}", event))
                .onRetry(event -> LOGGER.info(" Retry Event on Retry {}", event))
                .onSuccess(event -> LOGGER.info(" Retry Event on Success {}", event))
                .onEvent(event -> LOGGER.debug(" Retry Event occurred records all events Retry, error and success {}", event));
    }

    private void handlePublishedEvents(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher()
                .onCallNotPermitted(event -> LOGGER.debug(" onCallNotPermitted {}", event))
                .onError(event -> LOGGER.debug(" onError {}", event))
                .onFailureRateExceeded(event -> LOGGER.debug(" onFailureRateExceeded {}", event))
                .onIgnoredError(event -> LOGGER.debug(" onIgnoredError {}", event))
                .onReset(event -> LOGGER.debug(" onReset {}", event))
                .onStateTransition(event -> LOGGER.debug(" onStateTransition something else {}", event.getStateTransition()))
                .onSuccess(event -> LOGGER.debug(" onSuccess {}", event));
    }
}
