# chaos-engineering

#Retry with exponential backoff.
In the even of failure due to unavailability or any of the Exceptions listed in retryExceptions() menthod listed below, applications can choose to return a fallback/default return value or choose to keep the connection open and retry the endpoint which threw the error.
The retry logic can make use of a feature called exponential backoff. 

The code snippet below creates a retry config which allows a maximum of 5 retries where the first retry will be after 5000 miliseconds and each subsequent retry will be a multiple(2 in this case) of the previous. 

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
 The example above could very well be tuned to return a default cached/default reponse rather than retry with a single line code change.
 
    private <T> T executeWithRetry(Supplier<T> supplier, Function<Throwable, T> fallback) {
        retry = Retry.of(DATA_SERVICE, this::createRetryConfig);
        // Create a RetryRegistry with a custom global configuration
        retryRegistry = RetryRegistry.of(createRetryConfig());
        return Decorators.ofSupplier(supplier)
                .withFallback(Arrays.asList(ConnectException.class, ResourceAccessException.class), fallback)
                .get();
    }
    
#CircuitBreaker
In cases where default value is not an option and the remote system does not "heal" or respond even after repeated retries we can prevent furthur calls to the downstream system. The Circuit Breaker is one such method which helps us in preventing a cascade of failures when a remote service is down.
CircuitBreaker has 3 states
OPEN -  Rejects calls to remote service with a CallNotPermittedException when it is OPEN.
HALF_OPEN - Permits a configurable number of calls to see if the backend is still unavailable or has become available again.
CLOSED - Calls can be made to the remote system. This happens when the failure rate and slow call rate is below the threshold.

Two other states are also supported
DISABLED - always allow access.
FORCED_OPEN - always deny access

The transition happens from CLOSED to OPEN state when based upon 
1. How many of the last N calls have failed(Count based sliding window) or  
2. How many failures did we have in the last N minutes(or any other duration) called Time based sliding window.


A few settings can be configured for a Circuit Breaker:

1. The failure rate threshold above which the CircuitBreaker opens and starts short-circuiting calls
2. The wait duration which defines how long the CircuitBreaker should stay open before it switches to HALF_OPEN.
3. A custom CircuitBreakerEventListener which handles CircuitBreaker events
4. A Predicate which evaluates if an exception should count as a failure.


