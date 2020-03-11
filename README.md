###### Chaos-engineering
This project consists of implementations of a few patterns that allow remote or local services achieve fault tolerance 
(i.e resiliency) in the face of events such as service failure, too many concurrent requests etc. 
The frameworks of choice is resilience4j which provides higher-order functions (decorators) to enhance any functional interface,
lambda expression or method reference with a Circuit Breaker, Rate Limiter, Retry or Bulkhead. We can choose to use one or more
of these "Decorators" to meet our objective.

# Retry with exponential backoff.
In the even of failure due to unavailability or any of the Exceptions listed in retryExceptions() method listed below, 
applications can choose to return a fallback/default return value or choose to keep the connection open and retry the endpoint which threw the error.
The retry logic can make use of a feature called exponential backoff. 

The code snippet below creates a retry config which allows a maximum of 5 retries where the first retry will be after 
5000 milliseconds and each subsequent retry will be a multiple(2 in this case) of the previous. 

    private <T> T executeWithRetry(Supplier<T> supplier, Function<Throwable, T> fallback) {
        Retry retry = Retry.of(DATA_SERVICE, this::createRetryConfig);
        // Create a RetryRegistry with a custom global configuration
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
    
# CircuitBreaker
In cases where default value is not an option and the remote system does not "heal" or respond even after repeated retries 
we can prevent further calls to the downstream system. The Circuit Breaker is one such method which helps us in preventing a 
cascade of failures when a remote service is down.
CircuitBreaker has 3 states
**OPEN** -  Rejects calls to remote service with a CallNotPermittedException when it is OPEN.
**HALF_OPEN** - Permits a configurable number of calls to see if the backend is still unavailable or has become available again.
**CLOSED** - Calls can be made to the remote system. This happens when the failure rate and slow call rate is below the threshold.

Two other states are also supported
**DISABLED** - always allow access.
**FORCED_OPEN** - always deny access

The transition happens from CLOSED to OPEN state when based upon 
1. How many of the last N calls have failed(Count based sliding window) or  
2. How many failures did we have in the last N minutes(or any other duration) called Time based sliding window.


A few settings can be configured for a Circuit Breaker:

1. The failure rate threshold above which the CircuitBreaker opens and starts short-circuiting calls
2. The wait duration which defines how long the CircuitBreaker should stay open before it switches to HALF_OPEN.
3. A custom CircuitBreakerEventListener which handles CircuitBreaker events
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

    private <T> T executeWithRetryAndCircuitBreaker(Supplier<T> supplier, Function<Throwable, T> fallback) {
        return Decorators.ofSupplier(supplier)
                .withCircuitBreaker(circuitBreaker)
                .get();
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

# Rate Limiting
Rate limiting is an imperative technique to prepare your API for scale and establish high availability and reliability of 
your service.

# Bulkhead
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
    private Bulkhead createBulkhead(int maxConcurrentCalls, int maxWaitDuration) {
           BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                   .maxConcurrentCalls(maxConcurrentCalls)
                   .maxWaitDuration(Duration.ofMillis(maxWaitDuration))
                   .build();
   
           BulkheadRegistry bulkheadRegistry = BulkheadRegistry.of(bulkheadConfig);
           return bulkheadRegistry.bulkhead(DATA_SERVICE);
       }
2.  Decorate the service using the Bulkhead created above.
        private <T> void callRemoteService(Bulkhead bulkhead, List<Object> returnValues, List<Exception> failedRequests, Set<String> successfulRemoteCalls, Set<String> rejectedRemoteCalls) {
            try {
                Callable<T> callable = () -> (T) resiliencyDataService.getDatafromRemoteServiceForFallbackPattern();
                T returnValue = bulkhead.executeCallable(callable);
                bulkhead.getEventPublisher()
                        .onCallPermitted(event -> {
                            successfulRemoteCalls.add(Thread.currentThread().getName());
                            //LOGGER.info("Successful remote call {} ", Thread.currentThread().getName());
                        })
                        .onCallRejected(event -> {
                            rejectedRemoteCalls.add(Thread.currentThread().getName());
                            //LOGGER.error("Rejected remote call {} ", Thread.currentThread().getName());
                        })
                        .onCallFinished(event -> LOGGER.debug("Call Finished {} ", event));
                LOGGER.debug(Thread.currentThread().getName() + " successful. Return value = " + returnValue.getClass());
                returnValues.add(returnValue);
    
            } catch (Exception e) {
                //LOGGER.error(Thread.currentThread().getName() + " threw exception " + e.getMessage());
                failedRequests.add(e);
            }
        }


The eventPublisher retrieved from the bulkhead gives the event details of the successful, rejected and finished events. Using which the user 
could determine how best to handle each of these events.
 