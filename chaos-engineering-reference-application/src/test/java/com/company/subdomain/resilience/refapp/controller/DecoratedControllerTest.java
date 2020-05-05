package com.company.subdomain.resilience.refapp.controller;

import com.company.subdomain.resilience.refapp.ChaosEngineeringReferenceApplication;
import com.company.subdomain.resilience.refapp.model.MockDataServiceResponse;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

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
        Mono<MockDataServiceResponse> one = submitRESTRequest(webClient, 1010, throwException);
        Mono<MockDataServiceResponse> two = submitRESTRequest(webClient, 1011, throwException);
        Mono<MockDataServiceResponse> three = submitRESTRequest(webClient, 1012, throwException);
        Mono<MockDataServiceResponse> four = submitRESTRequest(webClient, 1013, throwException);
        Mono<MockDataServiceResponse> five = submitRESTRequest(webClient, 1014, throwException);
        Mono<MockDataServiceResponse> six = submitRESTRequest(webClient, 1015, throwException);
        Mono<MockDataServiceResponse> seven = submitRESTRequest(webClient, 1016, throwException);
        Mono<MockDataServiceResponse> eight = submitRESTRequest(webClient, 1017, throwException);
        Mono<MockDataServiceResponse> nine = submitRESTRequest(webClient, 1018, throwException);
        Mono<MockDataServiceResponse> ten = submitRESTRequest(webClient, 1019, throwException);
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
        Mono<MockDataServiceResponse> one = submitRESTRequest(webClient, throwException);
        Mono<MockDataServiceResponse> two = submitRESTRequest(webClient, throwException);
        Mono<MockDataServiceResponse> three = submitRESTRequest(webClient, throwException);
        Mono<MockDataServiceResponse> four = submitRESTRequest(webClient, throwException);
        Mono<MockDataServiceResponse> five = submitRESTRequest(webClient, throwException);
        Mono<MockDataServiceResponse> six = submitRESTRequest(webClient, throwException);
        Mono<MockDataServiceResponse> seven = submitRESTRequest(webClient, throwException);
        Mono<MockDataServiceResponse> eight = submitRESTRequest(webClient, throwException);
        Mono<MockDataServiceResponse> nine = submitRESTRequest(webClient, throwException);
        Mono<MockDataServiceResponse> ten = submitRESTRequest(webClient, throwException);
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
        Mono<MockDataServiceResponse> one = submitRESTRequest(webClient, throwException);
        Mono<MockDataServiceResponse> two = submitRESTRequest(webClient, throwException);
        Mono<MockDataServiceResponse> three = submitRESTRequest(webClient, throwException);
        Mono<MockDataServiceResponse> four = submitRESTRequest(webClient, throwException);
        Mono<MockDataServiceResponse> five = submitRESTRequest(webClient, throwException);
        Mono<MockDataServiceResponse> six = submitRESTRequest(webClient, throwException);
        Mono<MockDataServiceResponse> seven = submitRESTRequest(webClient, throwException);
        Mono<MockDataServiceResponse> eight = submitRESTRequest(webClient, throwException);
        Mono<MockDataServiceResponse> nine = submitRESTRequest(webClient, throwException);
        Mono<MockDataServiceResponse> ten = submitRESTRequest(webClient, throwException);
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
        Mono<MockDataServiceResponse> one = submitRESTRequest(webClient, 1010, throwException);
        Mono<MockDataServiceResponse> two = submitRESTRequest(webClient, 1011, !throwException);
        Mono<MockDataServiceResponse> three = submitRESTRequest(webClient, 1012, throwException);
        Mono<MockDataServiceResponse> four = submitRESTRequest(webClient, 1013, !throwException);
        Mono<MockDataServiceResponse> five = submitRESTRequest(webClient, 1014, throwException);
        Mono<MockDataServiceResponse> six = submitRESTRequest(webClient, 1015, !throwException);
        Mono<MockDataServiceResponse> seven = submitRESTRequest(webClient, 1016, throwException);
        Mono<MockDataServiceResponse> eight = submitRESTRequest(webClient, 1017, !throwException);
        Mono<MockDataServiceResponse> nine = submitRESTRequest(webClient, 1018, throwException);
        Mono<MockDataServiceResponse> ten = submitRESTRequest(webClient, 1019, !throwException);
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
        Mono<MockDataServiceResponse> one = submitRESTRequest(webClient, 1010, throwException);
        Mono<MockDataServiceResponse> two = submitRESTRequest(webClient, 1011, throwException);
        Mono<MockDataServiceResponse> three = submitRESTRequest(webClient, 1012, throwException);
        Mono<MockDataServiceResponse> four = submitRESTRequest(webClient, 1013, throwException);
        Mono<MockDataServiceResponse> five = submitRESTRequest(webClient, 1014, throwException);
        Mono<MockDataServiceResponse> six = submitRESTRequest(webClient, 1015, throwException);
        Mono<MockDataServiceResponse> seven = submitRESTRequest(webClient, 1016, throwException);
        Mono<MockDataServiceResponse> eight = submitRESTRequest(webClient, 1017, throwException);
        Mono<MockDataServiceResponse> nine = submitRESTRequest(webClient, 1018, throwException);
        Mono<MockDataServiceResponse> ten = submitRESTRequest(webClient, 1019, throwException);
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

    /**
     * 5 concurrent calls to a service which is protected by a Bulkhead which is able to handle 4 concurrent requests.
     * The result was 4 calls succeeded and 1 failed. This is because we have set the maxWaitDuration(Duration.ofMillis(0))
     * to zero which does not allow the request to retry after the first attempt even when a Thread becomes available for processing.
     *
     * @throws Exception
     */
    @Test
    @Order(6)
    void testSimpleSemaphoreBulkhead() throws Exception {
        String url = String.format("http://localhost:%d/decorated-services/simpleBulkhead", port);
        WebClient webClient = WebClient.create(url);
        Boolean throwException = Boolean.FALSE;
        Mono<MockDataServiceResponse> one = submitRESTRequest(webClient, 1010, throwException);
        Mono<MockDataServiceResponse> two = submitRESTRequest(webClient, 1011, throwException);
        Mono<MockDataServiceResponse> three = submitRESTRequest(webClient, 1012, throwException);
        Mono<MockDataServiceResponse> four = submitRESTRequest(webClient, 1013, throwException);
        Mono<MockDataServiceResponse> five = submitRESTRequest(webClient, 1014, throwException);
        Mono<List<MockDataServiceResponse>> listMono = Flux.merge(one, two, three, four, five)
                .map(result -> result)
                .collectList();
        CompletableFuture<List<MockDataServiceResponse>> listCompletableFuture = listMono.toFuture();
        try {
            boolean resultFound = false;
            while (!resultFound) {
                if (listCompletableFuture.isDone()) {
                    List<MockDataServiceResponse> successfulRequests = listCompletableFuture.get().stream()
                            .filter(val -> val.getData() != null)
                            .collect(Collectors.toList());
                    List<String> failedRequests = listCompletableFuture.get().stream()
                            .filter(val -> val.getData() == null || val.getData().isEmpty())
                            .map(val -> val.getHostedRegion())
                            .collect(Collectors.toList());
                    resultFound = true;
                    assertEquals(4, successfulRequests.size());
                    assertEquals(1, failedRequests.size());
                    assertEquals("This is a fallback response due to BulkheadFullException", failedRequests.get(0));
                }
                Thread.sleep(1000);
            }


        } catch (Throwable e) {
            e.printStackTrace();
            assertEquals("java.lang.Throwable: Internal Server Error", e.getMessage());
        }
    }

    /**
     * 5 concurrent calls to a service which is protected by a Bulkhead which is able to handle 4 concurrent requests.
     * The result was 4 calls succeeded and 1 failed. This is because we have set the maxWaitDuration(Duration.ofMillis(0))
     * to zero which does not allow the request to retry after the first attempt even when a Thread becomes available for processing.
     *
     * @throws Exception
     */
    @Test
    @Order(7)
    void testSimpleCircuitBreaker() throws Exception {
        String url = String.format("http://localhost:%d/decorated-services/simpleCircuitBreaker", port);
        WebClient webClient = WebClient.create(url);
        Boolean throwException = Boolean.FALSE;
        Mono<MockDataServiceResponse> one = submitRESTRequest(webClient, 1010, throwException);
        Mono<MockDataServiceResponse> two = submitRESTRequest(webClient, 1011, throwException);
        Mono<MockDataServiceResponse> three = submitRESTRequest(webClient, 1012, !throwException);
        Mono<MockDataServiceResponse> four = submitRESTRequest(webClient, 1013, !throwException);
        Mono<MockDataServiceResponse> five = submitRESTRequest(webClient, 1014, !throwException);
        Mono<MockDataServiceResponse> six = submitRESTRequest(webClient, 1015, !throwException);
        Mono<MockDataServiceResponse> seven = submitRESTRequest(webClient, 1016, throwException);
        Mono<MockDataServiceResponse> eight = submitRESTRequest(webClient, 1017, throwException);
        try {
            List<Mono<MockDataServiceResponse>> failedMonos = new ArrayList<>();
            List<MockDataServiceResponse> successfulMonos = new ArrayList<>();
            List<Mono<MockDataServiceResponse>> monos = Arrays.asList(one, two, three, four, five, six, seven, eight);
            for (Mono<MockDataServiceResponse> mono : monos) {
                MockDataServiceResponse mockDataServiceResponse = mono.toFuture().get();
                successfulMonos.add(mockDataServiceResponse);
            }
            List<MockDataServiceResponse> circuitBreakerFailures = successfulMonos.stream()
                    .filter(response -> response.getHostedRegion()
                            .equals("CallNotPermittedException thrown: {CircuitBreaker 'circuit-breaker' is OPEN and does not permit further calls}"))
                    .collect(Collectors.toList());
            List<MockDataServiceResponse> regularFailures = successfulMonos.stream()
                    .filter(response -> response.getHostedRegion()
                            .equals("Something went wrong!!"))
                    .collect(Collectors.toList());
            List<MockDataServiceResponse> successfulResponses = successfulMonos.stream()
                    .filter(response -> response.getData() != null)
                    .collect(Collectors.toList());
            assertEquals(regularFailures.size(), 2);
            assertEquals(circuitBreakerFailures.size(), 4);
            assertEquals(successfulResponses.size(), 2);

        } catch (Exception e) {
            fail();
        }
    }

    /**
     * RateLimiter has been configured to allow 4 requests every 10 seconds. When we send 8 sequential requests on my machine
     * 4 of them are successful and 4 fail due to RequestNotPermitted.
     *
     * @throws Exception
     */

    @Test
    @Order(8)
    void testRateLimiter() throws Exception {
        String url = String.format("http://localhost:%d/decorated-services/simpleRateLimiter", port);
        WebClient webClient = WebClient.create(url);
        Boolean throwException = Boolean.FALSE;
        Mono<MockDataServiceResponse> one = submitRESTRequest(webClient, 1010, throwException);
        Mono<MockDataServiceResponse> two = submitRESTRequest(webClient, 1011, throwException);
        Mono<MockDataServiceResponse> three = submitRESTRequest(webClient, 1012, throwException);
        Mono<MockDataServiceResponse> four = submitRESTRequest(webClient, 1013, throwException);
        Mono<MockDataServiceResponse> five = submitRESTRequest(webClient, 1014, throwException);
        Mono<MockDataServiceResponse> six = submitRESTRequest(webClient, 1015, throwException);
        Mono<MockDataServiceResponse> seven = submitRESTRequest(webClient, 1016, throwException);
        Mono<MockDataServiceResponse> eight = submitRESTRequest(webClient, 1017, throwException);
        try {
            List<MockDataServiceResponse> successfulMonos = new ArrayList<>();
            List<Mono<MockDataServiceResponse>> monos = Arrays.asList(one, two, three, four, five, six, seven, eight);
            for (Mono<MockDataServiceResponse> mono : monos) {
                MockDataServiceResponse mockDataServiceResponse = mono.toFuture().get();
                successfulMonos.add(mockDataServiceResponse);
            }
            List<MockDataServiceResponse> rateLimiterFailures = successfulMonos.stream()
                    .filter(response -> response.getHostedRegion()
                            .equals("RequestNotPermitted thrown: {RateLimiter 'rate-limiter' does not permit further calls}"))
                    .collect(Collectors.toList());
            List<MockDataServiceResponse> successfulResponses = successfulMonos.stream()
                    .filter(response -> response.getData() != null)
                    .collect(Collectors.toList());
            assertEquals(rateLimiterFailures.size(), 4);
            assertEquals(successfulResponses.size(), 4);

        } catch (Exception e) {
            fail();
        }
    }

    /**
     * This
     *
     * @throws Exception
     */
    @Test
    @Order(9)
    void testTimeLimiter() throws Exception {
        String url = String.format("http://localhost:%d/decorated-services/simpleTimeLimiter", port);
        WebClient webClient = WebClient.create(url);
        Boolean throwException = Boolean.FALSE;
        Mono<MockDataServiceResponse> one = submitRESTRequest(webClient, 1010, throwException);
        Mono<MockDataServiceResponse> two = submitRESTRequest(webClient, 1011, throwException);
        Mono<MockDataServiceResponse> three = submitRESTRequest(webClient, 1012, throwException);
        Mono<MockDataServiceResponse> four = submitRESTRequest(webClient, 1013, throwException);
        Mono<MockDataServiceResponse> five = submitRESTRequest(webClient, 1014, throwException);
        Mono<MockDataServiceResponse> six = submitRESTRequest(webClient, 1015, throwException);
        Mono<MockDataServiceResponse> seven = submitRESTRequest(webClient, 1016, throwException);
        Mono<MockDataServiceResponse> eight = submitRESTRequest(webClient, 1017, throwException);
        try {
            List<MockDataServiceResponse> successfulMonos = new ArrayList<>();
            List<Mono<MockDataServiceResponse>> monos = Arrays.asList(one, two, three, four, five, six, seven, eight);
            for (Mono<MockDataServiceResponse> mono : monos) {
                MockDataServiceResponse mockDataServiceResponse = mono.toFuture().get();
                successfulMonos.add(mockDataServiceResponse);
            }
            List<MockDataServiceResponse> timeLimiterFailures = successfulMonos.stream()
                    .filter(response -> response.getHostedRegion()
                            .equals("TimeLimiter does not set an exception message so creating one"))
                    .collect(Collectors.toList());
            List<MockDataServiceResponse> successfulResponses = successfulMonos.stream()
                    .filter(response -> response.getData() != null)
                    .collect(Collectors.toList());
            assertEquals(timeLimiterFailures.size(), 3);
            assertEquals(successfulResponses.size(), 5);

        } catch (Exception e) {
            fail();
        }
    }
    /////////       Private methods

    private Mono<MockDataServiceResponse> submitRESTRequest(WebClient webClient, int offerId, Boolean throwException) {
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

    private Mono<MockDataServiceResponse> submitRESTRequest(WebClient webClient, Boolean throwException) {
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
