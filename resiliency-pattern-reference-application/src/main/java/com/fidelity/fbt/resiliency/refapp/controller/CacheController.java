package com.fidelity.fbt.resiliency.refapp.controller;

import com.fidelity.fbt.resiliency.refapp.service.ResiliencyDataService;
import io.github.resilience4j.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import java.util.function.Function;

/**
 * @author souadhik
 * Controller class for resilient client application
 */
@RestController
@RequestMapping("resiliency-pattern")
public class CacheController {
    private static Logger LOGGER = LoggerFactory.getLogger(CacheController.class);
    private static final String DATA_SERVICE = "data-service";
    private CacheManager cacheManager;
    private final Cache<Object,Object> cache;
    /**
     * Data layer dependency for invoking data methods
     */
    private final ResiliencyDataService resiliencyDataService;

    public CacheController(
            ResiliencyDataService resiliencyDataService) {
        this.resiliencyDataService = resiliencyDataService;
        this.cache = createCache();
    }
    /**
     * @return This endpoint returns mock response for demonstrating fallback resiliency pattern
     */
    @GetMapping("/cache")
    public Object getMockOfferings(@RequestParam String offerId) throws Exception {
        LOGGER.info("Invoking CacheController with param {} ", offerId);
        return executeWithCache(offerId);

    }

    private <T> T executeWithCache(String offerId) throws Exception {
        return callRemoteService(offerId);

    }

    private <T> T callRemoteService(String offerId) throws Exception {
        handlePublishedEvents(cache);
        Function<Object, Object> decorateSupplier = Cache.decorateSupplier(cache,
                () -> (T) resiliencyDataService.getDatafromRemoteService(offerId));

        Object returnValue = decorateSupplier.apply(offerId);
        //.getOrElseThrow(throwable -> new Exception("Request timed out: " + throwable.getMessage()));
        return (T) returnValue;
    }

    private <T> void handlePublishedEvents(Cache<Object, Object> cache) {
        cache.getEventPublisher()
                .onError(event -> LOGGER.error("onError event {} ", event))
                .onCacheHit(event -> LOGGER.info("onCacheHit event {} ", event))
                .onCacheMiss(event -> LOGGER.warn("onCacheMiss event {} ", event));
    }

    private Cache<Object, Object> createCache() {
        this.cacheManager = Caching.getCachingProvider().getCacheManager();
        return Cache.of(cacheManager.createCache(DATA_SERVICE, new MutableConfiguration<>()));
    }

}
