package com.company.subdomain.resiliency.refapp.model;

import lombok.Data;

import java.util.List;

/**
 * @author souadhik
 * Model for mock service response
 */
@Data
public class MockClientServiceResponse {

	/**
	 * AWS Hosted region of the service. The value is only available when hosted on an EC2 instance
	 */
	private String hostedRegion;

	/**
	 * Addional message for the client along with
	 * service response.
	 */
	private String message;
	/**
	 * List of mock offerings
	 */
	private List<Offering> data;
}
