package com.fidelity.fbt.chaos.refapp.controller;

import com.fidelity.fbt.chaos.refapp.ChaosEngineeringReferenceApplication;
import com.fidelity.fbt.chaos.refapp.model.MockDataServiceResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = ChaosEngineeringReferenceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DecoratedControllerTest {
    private static Logger LOGGER = LoggerFactory.getLogger(DecoratedControllerTest.class);
    @LocalServerPort
    private int port;

//    @Autowired
//    private TestRestTemplate restTemplate;

    @Test
    void testSemaphoreBulkead() throws ExecutionException, InterruptedException {
        String url = String.format("http://localhost:%d/decorated-services/offeringsById", port);
        WebClient webClient = WebClient.create(url);
        Boolean throwException = Boolean.FALSE;
        Mono<MockDataServiceResponse> one = bulkheadDecoratedRestCall(webClient, 1010, throwException);
        Mono<MockDataServiceResponse> two = bulkheadDecoratedRestCall(webClient, 1011, throwException);
        Mono<MockDataServiceResponse> three = bulkheadDecoratedRestCall(webClient, 1012, throwException);
        Mono<MockDataServiceResponse> four = bulkheadDecoratedRestCall(webClient, 1013, throwException);
        Mono<MockDataServiceResponse> five = bulkheadDecoratedRestCall(webClient, 1014, throwException);
        Mono<MockDataServiceResponse> six = bulkheadDecoratedRestCall(webClient, 1015, throwException);
        Mono<MockDataServiceResponse> seven = bulkheadDecoratedRestCall(webClient, 1016, throwException);
        Mono<MockDataServiceResponse> eight = bulkheadDecoratedRestCall(webClient, 1017, throwException);
        Mono<MockDataServiceResponse> nine = bulkheadDecoratedRestCall(webClient, 1018, throwException);
        Mono<MockDataServiceResponse> ten = bulkheadDecoratedRestCall(webClient, 1019, throwException);
        Mono<List<MockDataServiceResponse>> listMono = Flux.merge(one, two, three, four, five, six, seven, eight, nine, ten)
                .map(result -> {
                    //LOGGER.info(" Flux.merge for offerId : {} was {} ", result.getData().get(0).getOfferId(), result);
                    return result;
                })
                .collectList();
        CompletableFuture<List<MockDataServiceResponse>> listCompletableFuture = listMono.toFuture();
        List<String> failedRequests = new ArrayList<>();
        List<MockDataServiceResponse> sucessfulRequests = new ArrayList<>();
        listCompletableFuture.get().forEach(
                val -> {
                    if (val.getData() == null || val.getData().isEmpty()) {
                        failedRequests.add(val.getHostedRegion());
                    } else {
                        sucessfulRequests.add(val);
                    }
                }
        );
        assertEquals(8, sucessfulRequests.size());
        assertEquals(2, failedRequests.size());
    }

    @Test
    void testThreadPoolBulkheadWithTimeLimiter() throws ExecutionException, InterruptedException {
        String url = String.format("http://localhost:%d/decorated-services/offerings", port);
        WebClient webClient = WebClient.create(url);
        Boolean throwException = Boolean.FALSE;
        Mono<MockDataServiceResponse> one = bulkheadDecoratedRestCall(webClient, throwException);
        Mono<MockDataServiceResponse> two = bulkheadDecoratedRestCall(webClient, throwException);
        Mono<MockDataServiceResponse> three = bulkheadDecoratedRestCall(webClient, throwException);
        Mono<MockDataServiceResponse> four = bulkheadDecoratedRestCall(webClient, throwException);
        Mono<MockDataServiceResponse> five = bulkheadDecoratedRestCall(webClient, throwException);
        Mono<MockDataServiceResponse> six = bulkheadDecoratedRestCall(webClient, throwException);
        Mono<MockDataServiceResponse> seven = bulkheadDecoratedRestCall(webClient, throwException);
        Mono<MockDataServiceResponse> eight = bulkheadDecoratedRestCall(webClient, throwException);
        Mono<MockDataServiceResponse> nine = bulkheadDecoratedRestCall(webClient, throwException);
        Mono<MockDataServiceResponse> ten = bulkheadDecoratedRestCall(webClient, throwException);
        Mono<List<MockDataServiceResponse>> listMono = Flux.merge(one, two, three, four, five, six, seven, eight, nine, ten)
                .map(result -> result)
                .collectList();
        CompletableFuture<List<MockDataServiceResponse>> listCompletableFuture = listMono.toFuture();
        List<String> failedRequests = new ArrayList<>();
        List<MockDataServiceResponse> sucessfulRequests = new ArrayList<>();
        listCompletableFuture.get().forEach(
                val -> {
                    if (val.getData() == null || val.getData().isEmpty()) {
                        failedRequests.add(val.getHostedRegion());
                    } else {
                        sucessfulRequests.add(val);
                    }
                }
        );
        assertEquals(0, sucessfulRequests.size());
        assertEquals(10, failedRequests.size());
        assertEquals(1,
                failedRequests.stream().filter(response -> response.contains("thread-pool-bulkhead")).collect(Collectors.toList()).size());
        assertEquals(9,
                failedRequests.stream().filter(response -> response.contains("time-limiter")).collect(Collectors.toList()).size());
    }

    @Test
    void testThreadPoolBulkheadWithRetry() throws ExecutionException, InterruptedException {
        String url = String.format("http://localhost:%d/decorated-services/offeringsWithRetry", port);
        WebClient webClient = WebClient.create(url);
        Boolean throwException = Boolean.FALSE;
        Mono<MockDataServiceResponse> one = bulkheadDecoratedRestCall(webClient, throwException);
        Mono<MockDataServiceResponse> two = bulkheadDecoratedRestCall(webClient, throwException);
        Mono<MockDataServiceResponse> three = bulkheadDecoratedRestCall(webClient, throwException);
        Mono<MockDataServiceResponse> four = bulkheadDecoratedRestCall(webClient, throwException);
        Mono<MockDataServiceResponse> five = bulkheadDecoratedRestCall(webClient, throwException);
        Mono<MockDataServiceResponse> six = bulkheadDecoratedRestCall(webClient, throwException);
        Mono<MockDataServiceResponse> seven = bulkheadDecoratedRestCall(webClient, throwException);
        Mono<MockDataServiceResponse> eight = bulkheadDecoratedRestCall(webClient, throwException);
        Mono<MockDataServiceResponse> nine = bulkheadDecoratedRestCall(webClient, throwException);
        Mono<MockDataServiceResponse> ten = bulkheadDecoratedRestCall(webClient, throwException);
        Mono<List<MockDataServiceResponse>> listMono = Flux.merge(one, two, three, four, five, six, seven, eight, nine, ten)
                .map(result -> {
                    //LOGGER.info(" Flux.merge for offerId : {} was {} ", result.getData().get(0).getOfferId(), result);
                    return result;
                })
                .collectList();
        CompletableFuture<List<MockDataServiceResponse>> listCompletableFuture = listMono.toFuture();
        List<String> failedRequests = new ArrayList<>();
        List<MockDataServiceResponse> successfulRequests = new ArrayList<>();
        listCompletableFuture.get().forEach(
                val -> {
                    if (val.getData() == null || val.getData().isEmpty()) {
                        failedRequests.add(val.getHostedRegion());
                    } else {
                        successfulRequests.add(val);
                    }
                }
        );
        assertEquals(10, successfulRequests.size());
        assertEquals(0, failedRequests.size());
    }

    private Mono<MockDataServiceResponse> bulkheadDecoratedRestCall(WebClient webClient, int offerId, Boolean throwException) {
        return webClient.get().uri("?offerId={offerId}&throwException={throwException}", offerId, throwException)
                .retrieve()
                .bodyToMono(MockDataServiceResponse.class);
    }

    private Mono<MockDataServiceResponse> bulkheadDecoratedRestCall(WebClient webClient, Boolean throwException) {
        return webClient.get().uri("?throwException={throwException}", throwException)
                .retrieve()
                .bodyToMono(MockDataServiceResponse.class);
    }
}