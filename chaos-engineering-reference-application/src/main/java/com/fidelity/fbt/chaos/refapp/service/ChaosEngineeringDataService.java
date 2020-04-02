package com.fidelity.fbt.chaos.refapp.service;

import com.fidelity.fbt.chaos.refapp.exception.ChaosEngineeringException;
import com.fidelity.fbt.chaos.refapp.model.MockDataServiceResponse;

public interface ChaosEngineeringDataService {
	MockDataServiceResponse getMockOfferingsDataFromService(boolean throwException) throws ChaosEngineeringException;

	MockDataServiceResponse getMockOfferingsDataFromService(String id, boolean throwException) throws ChaosEngineeringException;

	MockDataServiceResponse getDegradedMockOfferings(boolean throwException) throws ChaosEngineeringException;
}
