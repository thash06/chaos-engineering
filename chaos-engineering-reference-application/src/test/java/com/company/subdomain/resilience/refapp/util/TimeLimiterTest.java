package com.company.subdomain.resilience.refapp.util;

import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.vavr.control.Try;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class TimeLimiterTest {
    private static TimeLimiter timeLimiter;

    @BeforeAll
    public static void setUp() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(500))
                .cancelRunningFuture(true)
                .build();
        timeLimiter = TimeLimiter.of(config);

    }

    @Test
    public void testTimelimiter_WithFuture() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Supplier<Future<String>> futureSupplier = () -> executorService.submit(this::doSomethingThrowException);
        Callable<String> restrictedCall = TimeLimiter.decorateFutureSupplier(timeLimiter, futureSupplier);
        Try.of(restrictedCall::call)
                .onFailure(throwable -> {
                    System.out.println(throwable);
                    System.out.println("onFailure. {Future} might have timed out.");
                });
    }

    @Test
    public void testTimelimiter_WithCompletableFuture() {
        Supplier<String> supplier = this::doSomethingThrowException;
        Supplier<CompletableFuture<String>> futureSupplier = () -> CompletableFuture.supplyAsync(supplier);
        Callable<String> restrictedCall = TimeLimiter.decorateFutureSupplier(timeLimiter, futureSupplier);
        Try.of(restrictedCall::call)
                .onFailure(throwable -> {
                    System.out.println(throwable);
                    System.out.println("onFailure. {CompletableFuture} might have timed out.");
                });
    }

    private String doSomethingThrowException() {
        try {
            System.out.println(Thread.currentThread().getName() + " : " + "Method running and now going to sleep");
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            System.err.println(Thread.currentThread().getName() + " : " + e.getMessage());
            throw new RuntimeException("Interrupted Exception!!!!");
        }
        System.out.println(Thread.currentThread().getName() + " : " + "Method completed");
        throw new RuntimeException("What happened!!!!");
    }
}
