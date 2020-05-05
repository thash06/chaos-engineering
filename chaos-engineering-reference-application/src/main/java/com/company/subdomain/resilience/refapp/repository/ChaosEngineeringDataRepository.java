package com.company.subdomain.resilience.refapp.repository;

import com.company.subdomain.resilience.refapp.model.Offering;

import java.util.List;

public interface ChaosEngineeringDataRepository {

	/**
	 * @return a list of dummy offering data
	 */
	List<Offering> getSampleDataFromRepository();

	/**
	 * @param offerId
	 * @returns a specific dummy offering by id
	 */
	List<Offering> getSampleDataFromRepositoryById(String offerId);
}
