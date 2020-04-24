package com.fidelity.fbt.chaos.refapp.repository;

import com.fidelity.fbt.chaos.refapp.model.Offering;

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
