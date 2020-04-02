package com.fidelity.fbt.chaos.refapp.model;

import lombok.Data;

import java.util.List;

@Data
public class MockDataServiceResponse {

    /**
     * Hosted region of the service
     */
    private String hostedRegion;

    /**
	 * List of mock offerings
	 */
	private List<Offering> data;
}
