# Chaos-engineering
This project consists of implementations of a few patterns that allow remote or local services achieve fault tolerance 
(i.e resiliency) in the face of events such as service failure, too many concurrent requests etc. 
Failures cannot be prevented but the goal should be to build adaptive solution and prevent cascading failures from 
bringing down a system built upon microservices.

Hystrix one of the pioneer frameworks providing such functionality has been in maintenance mode since 2018 and Resiliency4j 
has been filing the void.

Resilience4j is a framework that provides higher-order functions (decorators) and/or annotations to enhance any method call, 
functional interface, lambda expression or method reference with a Circuit Breaker, Rate Limiter, Retry or Bulkhead. 
We can choose to use one or more of these "Decorators" to meet our resiliency objective.



## Resiliency Patterns using Resilience4j
This project is divided into 2 modules and each module demonstrates a unique approach of adding a resiliency layer
to an application.
- **resiliency-patterns-reference-application** - Is a standalone SpringBoot application acting as a Proxy 
meant to protect a business logic endpoint. There is  no business logic in this application and its sole purpose
is to act as a bridge providing controlled(resilient) access to the business logic endpoint.  
 
- **chaos-engineering-reference-application** - An application where the controller accesses the service containing 
the business logic through a layer called `DecoratedSupplier` which decorates the final business service endpoint
with resiliency pattern implementations.
The `DecoratedSupplier` has examples of chaining different patterns together as well as how to use `fallback`.

### Retry with exponential backoff
In the event of failure due to unavailability or any of the Exceptions listed in `retryExceptions()` method listed below, 
applications can choose to return a fallback/default return value or choose to keep the connection open and retry the endpoint which threw the error.
The retry logic can use simple bounded/timed retries or advanced retrying methods such as  exponential backoff. 

The code snippet below creates a retry config which allows a maximum of 5 retries where the first retry will be after 
5000 milliseconds and each subsequent retry will be a multiple (2 in this case) of the previous. 

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
 The example above could easily be tuned to return a default cached/default response rather than retry with a single 
 line code change.
 
    private <T> T executeWithRetry(Supplier<T> supplier, Function<Throwable, T> fallback) {
        retry = Retry.of(DATA_SERVICE, this::createRetryConfig);
        retryRegistry = RetryRegistry.of(createRetryConfig());
        return Decorators.ofSupplier(supplier)
                .withFallback(Arrays.asList(ConnectException.class, ResourceAccessException.class), fallback)
                .get();
    }
    
### CircuitBreaker
In cases where default value is not an option and the remote system does not "heal" or respond even after repeated retries 
we can prevent further calls to the downstream system. The Circuit Breaker is one such method which helps us in preventing a 
cascade of failures when a remote service is down.
CircuitBreaker has 3 states- 
- **OPEN** -  Rejects calls to remote service with a CallNotPermittedException when it is OPEN.
- **HALF_OPEN** - Permits a configurable number of calls to see if the backend is still unavailable or has become available again.
- **CLOSED** - Calls can be made to the remote system. This happens when the failure rate is below the threshold.

Two other states are also supported
- **DISABLED** - always allow access.
- **FORCED_OPEN** - always deny access

The transition happens from `CLOSED` to `OPEN` state based upon 
- **Count based sliding window** -  How many of the last N calls have failed. 
- **Time based sliding window** - How many failures occurred in the last N minutes (or any other duration).


A few settings can be configured for a Circuit Breaker:

1. The failure rate threshold above which the CircuitBreaker opens and starts short-circuiting calls.
2. The wait duration which defines how long the CircuitBreaker should stay open before it switches to HALF_OPEN.
3. A custom CircuitBreakerEventListener which handles CircuitBreaker events.
4. A Predicate which evaluates if an exception should count as a failure.


The code snippet below configures the CircuitBreaker and registers it against a global name and then attaches it to the 
method supplier. 
Two important settings are discussed below and the rest are self-explanatory.
- **The `failureRateThreshold`** - value specifies what percentage of remote calls should fail for the state to change from CLOSED to OPEN. 
- **The `slidingWindowSize()`** - property specifies the number of calls which will be used to determine the failure threshold percentage.

Eg: If in the last 5 remote calls 20% or 1 call failed due to  `ConnectException.class, ResourceAccessException.class` then the 
CircuitBreaker status changes to OPEN.
It stays in Open state for waitDurationInOpenState() milliseconds then allows the number  specified in
`permittedNumberOfCallsInHalfOpenState()` to go through to determine if the status can go back to CLOSED or stay in OPEN.


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
    
    
### Time Limiter
The code snippet below creates a TimeLimiter with a configurable `timeoutDuration()` which states that if the remote call execution 
takes longer than the `timeoutDuration()` the call is terminated and an exception or a cached/fallback value returned to caller.
One important caveat about the `cancelRunningFuture()` is that the value `true` only works when TimeLimiter is used to decorate 
a method which returns a Future. 
For a better understanding of how this property works differently in `Future` and `CompletableFuture` refer to 
the unit test `com.fidelity.fbt.chaos.refapp.utilTimeLimiterTest`. Run this test by switching the `cancelRunningFuture()`
to `false` and see how the output changes.
To gain a better understanding of why `CompletableFuture` cannot be interrupted refer to this fantastic tutorial.
https://www.nurkiewicz.com/2015/03/completablefuture-cant-be-interrupted.html?m=1

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


### Rate Limiter
Rate limiting is an imperative technique to prepare your API for scale and establish high availability and reliability of 
your service.
The code snippet below creates a RateLimiter instance which allows only a specified number of calls (`limitForPeriod(limitForPeriod)`)
in a time window `limitRefreshPeriod(Duration.ofSeconds(windowInSeconds))`. Calls that exceed the limit can wait for
the duration specified in `timeoutDuration(Duration.ofMillis(waitTimeForThread))`. 
Requests that do  not get processed within the `limitRefreshPeriod + timeoutDuration` are rejected.

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

### Bulkhead
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
**Note**: There are 2 Bulkhead implementations provided by Resilience4j and the decision of which one to choose can get a bit tricky.
Here are a few points which may help with that decision.
1. In SemaphoreBulkhead the number of concurrent threads to run is controlled by a Semaphore but the user code runs in the current Thread. 
   Also this bulkhead returns the actual response object without wrapping it in a Future.
2. In ThreadPoolBulkhead the number of concurrent threads to run is controlled by a ThreadPool size and the user code runs in a Thread from the ThreadPool. 
   Also this bulkhead returns the response object wrapped in a Future. 
   
### Caching using Redis
An effective 'recovery' option from any of the above failures could be a fallback response from a cache.
Resiliency4j allows chaining of Supplier with a cache. The code snippet below shows how to create
a cache using Redis as provider. When `callRemoteService` it first checks if the offerId is available in cache
and if it finds a match then it returns the value from cache rather than making a remote call. This pattern could easily
be used as a fallback option.


``

    private Cache<Object, Object> createCache() {
        MutableConfiguration<Object, Object> config = new MutableConfiguration<>();
        try {
            URI redissonConfigUri = getClass().getResource("/redisson-jcache.yml").toURI();
            cacheManager = Caching.getCachingProvider("org.redisson.jcache.JCachingProvider")
                    .getCacheManager(redissonConfigUri, null);
            Cache<Object, Object> cache = Cache.of(cacheManager.createCache(DATA_SERVICE, config));
            return cache;
        } catch (URISyntaxException e) {
            LOGGER.error("{}", e.getMessage(), e);
        }
        //Fix this
        return null;
    }
    
    private <T> T callRemoteService(String offerId, Boolean throwException) throws Throwable {
        handlePublishedEvents(cache);
        Function<Object, Object> decorateSupplier = Cache.decorateSupplier(cache,
                () -> (T) resiliencyDataService.getDatafromRemoteService(offerId, throwException));
        Object returnValue = decorateSupplier.apply(offerId);
        return (T) returnValue;
    }
``
## Demo of chaos-engineering-reference-application
 
 The producer application has decorated controller `DecoratedController` for use cases where the consumer does not want to 
 go through a proxy layer.
 It has 3 endpoints
 1. http://localhost:%d/decorated-services/offeringsById
 2. http://localhost:%d/decorated-services/offerings
 3. http://localhost:%d/decorated-services/offeringsWithRetry
 
There is a unit test `com.fidelity.fbt.chaos.refapp.controller.DecoratedControllerTest` which covers 3 scenarios. 
It uses WebClient to send 10 concurrent requests to each method and inspects the responses in each case.
Asserting the response is difficult in these test cases due to the nature of concurrent requests so some tests may fail
in different environments.


### How are methods chained in DecoratedController
The first end point is decorated by a just a Semaphore Bulkhead. The Bulkhead is configure with the number of available 
cores on the machine the application runs on. In my case it is 8 so when I send 10 concurrent requests 2 of the requests fail.

```
    private Bulkhead createBulkhead(int availableProcessors) {
        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(availableProcessors)
                .maxWaitDuration(Duration.ofMillis(100))
                .writableStackTraceEnabled(true)
                .build();

        BulkheadRegistry bulkheadRegistry = BulkheadRegistry.of(bulkheadConfig);
        return bulkheadRegistry.bulkhead(SEMAPHORE_BULKHEAD);
    }
```

The second endpoint is decorated with a ThreadPoolBulkhead and a Retry so even when 2 concurrent requests fail due 
BulkheadFullException the retry mechanism re-submits them and the requests get processed successfully.

```
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
```

The third method is protected by a ThreadPoolBulkhead and a TimeLimiter. The TimeLimiter is set to 400 milliseconds and 
the remote method takes 500 milliseconds to execute. This results in 2 of the requests to fail due to BulkheadFullException
and 8 requests to fail due to TimeoutException exception.

```
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
```



