package com.fidelity.fbt.resiliency.refapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.retry.annotation.CircuitBreaker;

/**
 * @author souadhik
 * Entry point for resilient client service application
 */
@SpringBootApplication
//@CircuitBreaker
//@EnableHystrix
//@EnableHystrixDashboard
public class ResiliencyPatternReferenceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ResiliencyPatternReferenceApplication.class, args);
	}

}
