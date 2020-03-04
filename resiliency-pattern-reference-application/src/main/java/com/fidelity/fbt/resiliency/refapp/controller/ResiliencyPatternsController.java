package com.fidelity.fbt.resiliency.refapp.controller;

import com.fidelity.fbt.resiliency.refapp.service.ResiliencyDataService;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

/**
 * @author souadhik
 * Controller class for resilient client application
 */
@RestController
@RequestMapping("resiliency-pattern")
public class ResiliencyPatternsController {

	private static final String DATA_SERVICE = "data-service";
	/**
	 * Data layer dependency for invoking data methods
	 */
	private final ResiliencyDataService resiliencyDataService;
	private final CircuitBreaker circuitBreaker;
	private final Bulkhead bulkhead;
	private final ThreadPoolBulkhead threadPoolBulkhead;
	private final Retry retry;
	private final RateLimiter rateLimiter;
	private final TimeLimiter timeLimiter;
	private final ScheduledExecutorService scheduledExecutorService;

	public ResiliencyPatternsController(
			ResiliencyDataService resiliencyDataService,
			CircuitBreakerRegistry circuitBreakerRegistry,
			ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry,
			BulkheadRegistry bulkheadRegistry,
			RetryRegistry retryRegistry,
			RateLimiterRegistry rateLimiterRegistry,
			TimeLimiterRegistry timeLimiterRegistry) {
		//this.chaosEngineeringDataService = chaosEngineeringDataService;
		this.resiliencyDataService = resiliencyDataService;
		this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(DATA_SERVICE);
		this.bulkhead = bulkheadRegistry.bulkhead(DATA_SERVICE);
		this.threadPoolBulkhead = threadPoolBulkheadRegistry.bulkhead(DATA_SERVICE);
		this.retry = retryRegistry.retry(DATA_SERVICE);
		this.rateLimiter = rateLimiterRegistry.rateLimiter(DATA_SERVICE);
		this.timeLimiter = timeLimiterRegistry.timeLimiter(DATA_SERVICE);
		this.scheduledExecutorService = Executors.newScheduledThreadPool(3);
	}

	/**
	 * @return This endpoint returns mock response for demonstrating fallback resiliency pattern
	 */
	@GetMapping("/fallback")
	public Object getMockOfferings() {
		return execute(resiliencyDataService::getDatafromRemoteServiceForFallbackPattern);
	}

	private <T> T execute(Supplier<T> supplier/*, Function<Throwable, T> fallback*/) {
		return Decorators.ofSupplier(supplier)
				.withCircuitBreaker(circuitBreaker)
				.withBulkhead(bulkhead)
				.withRetry(retry)
				.withRateLimiter(rateLimiter)
//				.withFallback(asList(RuntimeException.class, TimeoutException.class, CallNotPermittedException.class, BulkheadFullException.class),
//						fallback)
				.get();
	}
/*	private MockDataServiceResponse fallback(Throwable ex) {
		MockDataServiceResponse response = new MockDataServiceResponse();
		response.setData(Collections.emptyList());
		response.setHostedRegion("us-east-1");
		return response;
	}*/
}
