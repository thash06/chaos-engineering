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
