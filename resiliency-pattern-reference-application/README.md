##CircuitBreaker
http://localhost:9000/resiliency-pattern/circuit-breaker?throwException=true

##Retry
http://localhost:9000/resiliency-pattern/retry?throwException=true

##TimeLimiter
#### Failure
http://localhost:9000/resiliency-pattern/time-limiter?waitTimeForThread=55
##### Pass
http://localhost:9000/resiliency-pattern/time-limiter?waitTimeForThread=550

##RateLimiter
##### Failure
http://localhost:9000/resiliency-pattern/rate-limiter?limitForPeriod=5&windowInSeconds=5&waitTimeForThread=5&numOfTestRequests=10
 ##### Pass
http://localhost:9000/resiliency-pattern/rate-limiter?limitForPeriod=5&windowInSeconds=5&waitTimeForThread=5&numOfTestRequests=5


##Bulkhead
http://localhost:9000/resiliency-pattern/bulkhead?maxConcurrentCalls=4&maxWaitDuration=100

##Cache
http://localhost:9000/resiliency-pattern/cache?offerId=1001&throwException=false


##Annotated Controller
##Retry
