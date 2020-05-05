package com.company.subdomain.resiliency.refapp.controller;

import com.company.subdomain.resiliency.refapp.service.ResiliencyDataService;
import com.company.subdomain.resiliency.refapp.util.DecoratorUtil;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Function;

@RestController
@RequestMapping("resiliency-pattern")
public class CacheController<T, R> {
    private static Logger LOGGER = LoggerFactory.getLogger(CacheController.class);
    private static final String DATA_SERVICE = "data-service";
    private CacheManager cacheManager;
    private Cache<Object, Object> cache;
    /**
     * Data layer dependency for invoking data methods
     */
    private final ResiliencyDataService resiliencyDataService;
    private final DecoratorUtil<T, R> decoratorUtil;

    public CacheController(ResiliencyDataService resiliencyDataService, DecoratorUtil<T, R> decoratorUtil) {
        this.resiliencyDataService = resiliencyDataService;
        this.decoratorUtil = decoratorUtil;
        this.cache = createCache();
    }

    /**
     * @return This endpoint returns mock response for demonstrating fallback resiliency pattern
     */
    @GetMapping("/cache")
    public Object getMockOfferings(@RequestParam String offerId, @RequestParam Boolean throwException) throws Throwable {
        LOGGER.info("Invoking CacheController with param {} ", offerId);
        return executeWithCache(offerId, throwException);

    }

    private <T> T executeWithCache(String offerId, Boolean throwException) throws Throwable {
        return callRemoteService(offerId, throwException);

    }

    private <T> T callRemoteService(String offerId, Boolean throwException) throws Throwable {
        handlePublishedEvents(cache);
        Function<Object, Object> decorateSupplier = Cache.decorateSupplier(cache,
                () -> (T) resiliencyDataService.getDatafromRemoteService(offerId, throwException));

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

//    EHCache config
//    private Cache<Object, Object> createCache() {
//        this.cacheManager = Caching.getCachingProvider().getCacheManager();
//        return Cache.of(cacheManager).;
//    }
}
