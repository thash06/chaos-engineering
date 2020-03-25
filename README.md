# Chaos-engineering
This project consists of implementations of a few patterns that allow remote or local services achieve fault tolerance 
(i.e resiliency) in the face of events such as service failure, too many concurrent requests etc. 
Failures cannot be prevented but the goal should be to build adaptive solution and prevent cascading failures from 
bringing down a system built upon microservices.

Hystrix one of the pioneer frameworks providing such functionality has been in maintenance mode since 2018 and Resiliency4j 
has been filing the void.

Resilience4j is a framework that provides higher-order functions (decorators) and/or annotationsto enhance any method call, 
functional interface, lambda expression or method reference with a Circuit Breaker, Rate Limiter, Retry or Bulkhead. 
We can choose to use one or more of these "Decorators" to meet our resiliency objective.

## Retry with exponential backoff
In the even of failure due to unavailability or any of the Exceptions listed in retryExceptions() method listed below, 
applications can choose to return a fallback/default return value or choose to keep the connection open and retry the endpoint which threw the error.
The retry logic can make use of a feature called exponential backoff. 

The code snippet below creates a retry config which allows a maximum of 5 retries where the first retry will be after 
5000 milliseconds and each subsequent retry will be a multiple(2 in this case) of the previous. 

    private <T> T executeWithRetry(Supplier<T> supplier, Function<Throwable, T> fallback) {
        Retry retry = Retry.of(DATA_SERVICE, this::createRetryConfig);
        retryRegistry = RetryRegistry.of(createRetryConfig());
        return Retry.decorateSupplier(retry, supplier).get();
    }

    private RetryConfig createRetryConfig() {
        IntervalFunction intervalWithCustomExponentialBackoff = IntervalFunction
                .ofExponentialBackoff(5000l, 2d);
        return RetryConfig.custom()
                .intervalFunction(intervalWithCustomExponentialBackoff)
                .maxAttempts(5)
                .retryExceptions(ConnectException.class, ResourceAccessException.class)
                .build();
    }
 The example above could very well be tuned to return a default cached/default response rather than retry with a single 
 line code change.
 
    private <T> T executeWithRetry(Supplier<T> supplier, Function<Throwable, T> fallback) {
        retry = Retry.of(DATA_SERVICE, this::createRetryConfig);
        retryRegistry = RetryRegistry.of(createRetryConfig());
        return Decorators.ofSupplier(supplier)
                .withFallback(Arrays.asList(ConnectException.class, ResourceAccessException.class), fallback)
                .get();
    }
    
## CircuitBreaker
In cases where default value is not an option and the remote system does not "heal" or respond even after repeated retries 
we can prevent further calls to the downstream system. The Circuit Breaker is one such method which helps us in preventing a 
cascade of failures when a remote service is down.
CircuitBreaker has 3 states- 
- **OPEN** -  Rejects calls to remote service with a CallNotPermittedException when it is OPEN.
- **HALF_OPEN** - Permits a configurable number of calls to see if the backend is still unavailable or has become available again.
- **CLOSED** - Calls can be made to the remote system. This happens when the failure rate and slow call rate is below the threshold.

Two other states are also supported
- **DISABLED** - always allow access.
- **FORCED_OPEN** - always deny access

The transition happens from CLOSED to OPEN state based upon 
1. How many of the last N calls have failed(Count based sliding window) or  
2. How many failures did we have in the last N minutes(or any other duration) called Time based sliding window.


A few settings can be configured for a Circuit Breaker:

1. The failure rate threshold above which the CircuitBreaker opens and starts short-circuiting calls
2.  The wait duration which defines how long the CircuitBreaker should stay open before it switches to HALF_OPEN.
3.  A custom CircuitBreakerEventListener which handles CircuitBreaker events
4. A Predicate which evaluates if an exception should count as a failure.


The code snippet below configures the CircuitBreaker and registers it against a global name and then attaches it to the 
method supplier. 
A few important settings are discussed below and the rest are self explanatory.
The failureRateThreshold value specifies what percentage of remote calls should fail for the state to change from CLOSED to OPEN. 
The slidingWindowSize() property specifies the number of calls which will be used to determine the failure threshold percentage.
Eg: If in the last 5 remote calls 20% or 1 call failed due to  ConnectException.class, ResourceAccessException.class then the 
CircuitBreaker status changes to OPEN.
It stays in Open state for waitDurationInOpenState() milliseconds then then allows the number  specified in
permittedNumberOfCallsInHalfOpenState() to go through to determine if the status can go back to CLOSED or stay in OPEN.

    private <T> T executeWithCircuitBreaker(Supplier<T> supplier){
        Supplier<T> decoratedSupplier = Decorators.ofSupplier(supplier)
                .withCircuitBreaker(circuitBreaker)
                .decorate();
        handlePublisherEvents();

        return Try.ofSupplier(decoratedSupplier).getOrElseGet(throwable -> {
            String response = "{CircuitBreaker State is : " + circuitBreaker.getState().name() + ", \n Returning cached response: " + fallback() + "}";
            return (T) response;
        });
    }

    private CircuitBreaker createCircuitBreaker() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(20)
                .waitDurationInOpenState(Duration.ofMillis(10000))
                .permittedNumberOfCallsInHalfOpenState(2)
                .slidingWindowSize(5)
                .recordExceptions(ConnectException.class, ResourceAccessException.class)
                .ignoreExceptions(ChaosEngineeringException.class)
                .build();
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        return circuitBreakerRegistry.circuitBreaker(DATA_SERVICE);
    }
## Time Limiter

```
    private TimeLimiter createTimeLimiter(int waitTimeForThread) {
        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .cancelRunningFuture(true)
                .timeoutDuration(Duration.ofMillis(waitTimeForThread))
                .build();
        TimeLimiterRegistry timeLimiterRegistry = TimeLimiterRegistry.of(timeLimiterConfig);
        return timeLimiterRegistry.timeLimiter(DATA_SERVICE, timeLimiterConfig);
    }
    private <T> T callRemoteService(TimeLimiter timeLimiter) throws Exception {
        handlePublishedEvents(timeLimiter);
        Supplier<CompletableFuture<Object>> futureSupplier = () ->
                CompletableFuture.supplyAsync(resiliencyDataService::getDatafromRemoteService);
        Callable<Object> decorateFutureSupplier = TimeLimiter.decorateFutureSupplier(timeLimiter, futureSupplier);

        Object returnValue = Try.of(decorateFutureSupplier::call).getOrElse(this::fallback);
        //.getOrElseThrow(throwable -> new Exception("Request timed out: " + throwable.getMessage()));
        return (T) returnValue;
    }
```

The code snippet above creates a TimeLimiter with a configurableTimeout duration which states that if the remote call execution 
takes longer than the timeoutDuration() the call is terminated and an exception or a cached/fallback value returned to caller.

## Rate Limiter
```
    private RateLimiter createRateLimiter(int limitForPeriod, int windowInSeconds, int waitTimeForThread) {
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(windowInSeconds))
                .limitForPeriod(limitForPeriod)
                .timeoutDuration(Duration.ofMillis(waitTimeForThread))
                .build();
        RateLimiterRegistry rateLimiterRegistry = RateLimiterRegistry.of(rateLimiterConfig);
        return rateLimiterRegistry.rateLimiter(DATA_SERVICE, rateLimiterConfig);
    }
    private <T> void callRemoteService(RateLimiter rateLimiter, List<Object> returnValues, Set<String> successfulRemoteCalls, Set<String> rejectedRemoteCalls) {
        try {
            Callable<T> callable = () -> (T) resiliencyDataService.getDatafromRemoteService();
            Callable<T> decoratedCallable = Decorators.ofCallable(callable)
                    .withFallback(Arrays.asList(ConnectException.class), throwable -> (T) fallback(throwable))
                    .withRateLimiter(rateLimiter)
                    .decorate();
            handlePublisherEvents(rateLimiter, successfulRemoteCalls, rejectedRemoteCalls);
            Try.ofCallable(decoratedCallable)
                    .onFailure(throwable -> rejectedRemoteCalls.add(throwable.toString()))
                    .onSuccess(t -> returnValues.add(t));

        } catch (Exception e) {
            LOGGER.error(Thread.currentThread().getName() + " threw exception " + e.getMessage());
        }
    }
```
Rate limiting is an imperative technique to prepare your API for scale and establish high availability and reliability of 
your service.
The code snippet above creats a RateLimiter instance which allows only a specified number of calls (_limitForPeriod(limitForPeriod)_)
in a time window (_limitRefreshPeriod(Duration.ofSeconds(windowInSeconds))_). Calls that exceed the limit can wait for
the duration specified in (_timeoutDuration(Duration.ofMillis(waitTimeForThread))_). 
Requests that do  not get processed within the _limitRefreshPeriod + timeoutDuration_ are rejected.

## Bulkhead
Used to limit the number of concurrent calls to a service. If clients send more than the number of concurrent calls 
(**referred to as the saturation point and configured using the maxConcurrentCalls()**) than the service is configured to handle, 
a Bulkhead decorated service protects it from getting overwhelmed by keeping the additional calls waiting for a preconfigured time 
(**configured through maxWaitTime()**). 
If during this wait time any of the threads handing the existing concurrent calls becomes available the waiting calls get their turn 
to execute else these calls are rejected by the Bulkhead decorator by throwing a BulkheadFullException stating that  
"Bulkhead _<bulkhead-name>_ is full and does not permit further calls".

Applying a Bulkhead decorator to a service can be done in 2 easy steps.
1.  Create a Bulkhead using custom or default configuration. In the example below the Bulkhead will allow the
    service that it protected to accept _maxConcurrentCalls_ calls concurrently. While the request is being processed any new incoming request will be 
    queued for _maxWaitDuration_ and if during this time any of the concurrent calls complete and frees up the Thread the waiting request will get 
    forwarded to the service otherwise a BulkheadFullException is thrown.


```
    private Bulkhead createBulkhead(int maxConcurrentCalls, int maxWaitDuration) {
        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(maxConcurrentCalls)
                .maxWaitDuration(Duration.ofMillis(maxWaitDuration))
                .build();

        BulkheadRegistry bulkheadRegistry = BulkheadRegistry.of(bulkheadConfig);
        return bulkheadRegistry.bulkhead(DATA_SERVICE);
    }
```

2.  Decorate the service using the Bulkhead created above.

```
    private <T> T callRemoteService(Bulkhead bulkhead) throws Exception{
            Callable<T> callable = () -> (T) resiliencyDataService.getDatafromRemoteServiceForFallbackPattern();
            Callable<T> decoratedCallable = Decorators.ofCallable(callable)
                    .withBulkhead(bulkhead)
                    .decorate();
        handlePublisherEvents(bulkhead);
        return Try.ofCallable(decoratedCallable).getOrElseThrow(() ->
                    new Exception("{Bulkhead full : " + bulkhead.getName() + ". Could return a cached response here}")
            );
    }
```


The eventPublisher retrieved from the bulkhead gives the event details of the successful, rejected and finished events. Using which the user 
could determine how best to handle each of these events.

A small test stub that simulates sending 20 concurrent requests is shown below.
 ```
    private <T> T executeWithBulkhead(Bulkhead bulkhead) {
        LOGGER.info("Created Bulkhead with {} max concurrent calls maxWaitDuration {} ",
                bulkhead.getBulkheadConfig().getMaxConcurrentCalls(), bulkhead.getBulkheadConfig().getMaxWaitDuration());
        List<Object> returnValues = new ArrayList<>();
        Set<String> rejectedRemoteCalls = new HashSet<>();
        int noOfConcurrentReqSent = 20;
        for (int i = 0; i < noOfConcurrentReqSent; i++) {
            new Thread(() -> {
                try {
                    T returnValue = callRemoteService(bulkhead);
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
```
 
 ##Chaos-engineering-reference-application
 The producer application also has a decorated endpoint DecoratedController for use cases where the consumer does not want to 
 go through a proxy layer.
 It has 3 endpoints
 1. http://localhost:%d/decorated-services/offeringsById
 2. http://localhost:%d/decorated-services/offerings
 3. http://localhost:%d/decorated-services/offeringsWithRetry
 
    The first end point is decorated by a just a Semaphore Bulkhead. The Bulkhead is configure with the number of available 
cores on the machine the application runs on. In my case it is 8 so when I send 10 concurrent requests 2 of the requests fail.
'''
    private Bulkhead createBulkhead(int availableProcessors) {
        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(availableProcessors)
                .maxWaitDuration(Duration.ofMillis(100))
                .writableStackTraceEnabled(true)
                .build();

        BulkheadRegistry bulkheadRegistry = BulkheadRegistry.of(bulkheadConfig);
        return bulkheadRegistry.bulkhead(SEMAPHORE_BULKHEAD);
    }
''' 
    The second endpoint is decorated with a ThreadPoolBulkhead and a Retry so even when 2 concurrent requests fail due 
BulkheadFullException the retry mechanism re-submits them and the requests get processed successfully.
'''
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
'''

    The third method is protected by a ThreadPoolBulkhead and a TimeLimiter. The TimeLimiter is set to 400 milliseconds and 
the remote method takes 500 milliseconds to execute. This results in 2 of the requests to fail due to BulkheadFullException
and 8 requests to fail due to TimeoutException exception.

'''
    private MockDataServiceResponse callBulkheadDecoratedService() throws ExecutionException, InterruptedException {
        handlePublisherEvents(threadPoolBulkhead);
        Supplier<MockDataServiceResponse> serviceAsSupplier = createServiceAsSupplier();
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
'''


There is a unit test DecoratedControllerTest which covers these 3 methods. 
It uses WebClient to send 10 concurrent requests for each methods inspect the responses in each case.

 
 