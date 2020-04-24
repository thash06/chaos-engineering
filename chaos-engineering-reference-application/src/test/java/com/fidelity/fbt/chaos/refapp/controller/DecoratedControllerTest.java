package com.fidelity.fbt.chaos.refapp.controller;

import com.fidelity.fbt.chaos.refapp.ChaosEngineeringReferenceApplication;
import com.fidelity.fbt.chaos.refapp.model.MockDataServiceResponse;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = ChaosEngineeringReferenceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DecoratedControllerTest {
    private static Logger LOGGER = LoggerFactory.getLogger(DecoratedControllerTest.class);
    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @Order(1)
    void testSemaphoreBulkhead() throws ExecutionException, InterruptedException {
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
        List<MockDataServiceResponse> successfulRequests = listCompletableFuture.get().stream()
                .filter(val -> val.getData() != null)
                .collect(Collectors.toList());
        List<String> failedRequests = listCompletableFuture.get().stream()
                .filter(val -> val.getData() == null || val.getData().isEmpty())
                .map(val -> val.getHostedRegion())
                .collect(Collectors.toList());
        assertEquals(8, successfulRequests.size());
        assertEquals(2, failedRequests.size());
    }

    @Test
    @Order(2)
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
        List<MockDataServiceResponse> successfulRequests = listCompletableFuture.get().stream()
                .filter(val -> val.getData() != null)
                .collect(Collectors.toList());
        List<String> failedRequests = listCompletableFuture.get().stream()
                .filter(val -> val.getData() == null || val.getData().isEmpty())
                .map(val -> val.getHostedRegion())
                .collect(Collectors.toList());
        assertEquals(0, successfulRequests.size());
        assertEquals(10, failedRequests.size());
        assertEquals(1,
                failedRequests.stream().filter(response -> response.contains("thread-pool-bulkhead")).collect(Collectors.toList()).size());
        assertEquals(9,
                failedRequests.stream().filter(response -> response.contains("time-limiter")).collect(Collectors.toList()).size());
    }

    @Test
    @Order(3)
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
        //Thread.sleep(1000);
        List<MockDataServiceResponse> successfulRequests = listCompletableFuture.get().stream()
                .filter(val -> val.getData() != null)
                .collect(Collectors.toList());
        List<String> failedRequests = listCompletableFuture.get().stream()
                .filter(val -> val.getData() == null || val.getData().isEmpty())
                .map(val -> val.getHostedRegion())
                .collect(Collectors.toList());
        assertEquals(10, successfulRequests.size());
        assertEquals(0, failedRequests.size());
    }

    @Test
    @Order(4)
    void testSemaphoreBulkhead_MethodThrowsException() throws ExecutionException, InterruptedException {
        String url = String.format("http://localhost:%d/decorated-services/offeringsById", port);
        WebClient webClient = WebClient.create(url);
        Boolean throwException = Boolean.TRUE;
        Mono<MockDataServiceResponse> one = bulkheadDecoratedRestCall(webClient, 1010, throwException);
        Mono<MockDataServiceResponse> two = bulkheadDecoratedRestCall(webClient, 1011, !throwException);
        Mono<MockDataServiceResponse> three = bulkheadDecoratedRestCall(webClient, 1012, throwException);
        Mono<MockDataServiceResponse> four = bulkheadDecoratedRestCall(webClient, 1013, !throwException);
        Mono<MockDataServiceResponse> five = bulkheadDecoratedRestCall(webClient, 1014, throwException);
        Mono<MockDataServiceResponse> six = bulkheadDecoratedRestCall(webClient, 1015, !throwException);
        Mono<MockDataServiceResponse> seven = bulkheadDecoratedRestCall(webClient, 1016, throwException);
        Mono<MockDataServiceResponse> eight = bulkheadDecoratedRestCall(webClient, 1017, !throwException);
        Mono<MockDataServiceResponse> nine = bulkheadDecoratedRestCall(webClient, 1018, throwException);
        Mono<MockDataServiceResponse> ten = bulkheadDecoratedRestCall(webClient, 1019, !throwException);
        Mono<List<MockDataServiceResponse>> listMono = Flux.merge(one, two, three, four, five, six, seven, eight, nine, ten)
                .map(result -> {
                    LOGGER.info(" Flux.merge for offerId : {} was {} ", result.getData().get(0).getOfferId(), result);
                    return result;
                })
                .doOnError(throwable -> LOGGER.error("Flux Merge Error type {} and cause {}", throwable.getClass(), throwable.getCause()))
                .collectList();
        CompletableFuture<List<MockDataServiceResponse>> listCompletableFuture = listMono
                .doOnError(throwable -> LOGGER.error("ToFuture Error type {} and cause {}", throwable.getClass(), throwable.getCause()))
                .toFuture();
        List<MockDataServiceResponse> successfulRequests = null;
        List<String> failedRequests = null;
        try {

            //MockDataServiceResponse response1 = one.toFuture().get();
            //MockDataServiceResponse response2 = two.toFuture().get();
            successfulRequests = listCompletableFuture.get().stream()
                    .filter(val -> val.getData() != null)
                    .collect(Collectors.toList());
            failedRequests = listCompletableFuture.get().stream()
                    .filter(val -> val.getData() == null || val.getData().isEmpty())
                    .map(val -> val.getHostedRegion())
                    .collect(Collectors.toList());
        } catch (Throwable e) {
            assertEquals("java.lang.Throwable: Internal Server Error", e.getMessage());
        }

    }

    @Test
    @Order(5)
    void testDegradingService() throws Exception {
        String url = String.format("http://localhost:%d/decorated-services/degradingService", port);
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
                .collectList();
        CompletableFuture<List<MockDataServiceResponse>> listCompletableFuture = listMono
                .toFuture();
        List<MockDataServiceResponse> successfulRequests = Collections.emptyList();
        List<String> failedRequests = Collections.emptyList();
        try {
            MockDataServiceResponse response1 = one.toFuture().get();
            MockDataServiceResponse response2 = two.toFuture().get();
            MockDataServiceResponse response3 = three.toFuture().get();
            MockDataServiceResponse response4 = four.toFuture().get();
            MockDataServiceResponse response5 = five.toFuture().get();
            MockDataServiceResponse response6 = six.toFuture().get();
            MockDataServiceResponse response7 = seven.toFuture().get();
            MockDataServiceResponse response8 = eight.toFuture().get();
            MockDataServiceResponse response9 = nine.toFuture().get();
            MockDataServiceResponse response10 = ten.toFuture().get();
            boolean resultFound = false;
            while (!resultFound) {
                if (listCompletableFuture.isDone()) {
                    successfulRequests = listCompletableFuture.get().stream()
                            .filter(val -> val.getData() != null)
                            .collect(Collectors.toList());
                    failedRequests = listCompletableFuture.get().stream()
                            .filter(val -> val.getData() == null || val.getData().isEmpty())
                            .map(val -> val.getHostedRegion())
                            .collect(Collectors.toList());
                    resultFound = true;
                }
                Thread.sleep(1000);
            }
        } catch (Throwable e) {
            e.printStackTrace();
            assertEquals("java.lang.Throwable: Internal Server Error", e.getMessage());
        }
//        assertEquals(9, successfulRequests.size());
//        assertEquals(1, failedRequests.size());
    }
    /////////       Private methods

    private Mono<MockDataServiceResponse> bulkheadDecoratedRestCall(WebClient webClient, int offerId, Boolean throwException) {
        return webClient.get().uri("?offerId={offerId}&throwException={throwException}", offerId, throwException)
                .retrieve()
                .onStatus(HttpStatus::is4xxClientError, clientResponse ->
                        Mono.error(new Throwable(clientResponse.statusCode().getReasonPhrase()))
                )
                .onStatus(HttpStatus::is5xxServerError, clientResponse ->
                        Mono.error(new Throwable(clientResponse.statusCode().getReasonPhrase()))
                )
                .bodyToMono(MockDataServiceResponse.class);
    }

    private Mono<MockDataServiceResponse> bulkheadDecoratedRestCall(WebClient webClient, Boolean throwException) {
        return webClient.get().uri("?throwException={throwException}", throwException)
                .retrieve()
                .onStatus(HttpStatus::is4xxClientError, clientResponse ->
                        Mono.error(new Throwable(clientResponse.statusCode().getReasonPhrase()))
                )
                .onStatus(HttpStatus::is5xxServerError, clientResponse ->
                        Mono.error(new Throwable(clientResponse.statusCode().getReasonPhrase()))
                )
                .bodyToMono(MockDataServiceResponse.class);
    }
}
