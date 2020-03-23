package com.fidelity.fbt.resiliency.refapp.service;

import com.fidelity.fbt.resiliency.refapp.exception.ChaosEngineeringException;
import com.fidelity.fbt.resiliency.refapp.model.MockClientServiceResponse;

public interface ResiliencyDataService<T> {
    T getDatafromRemoteService(T throwException) throws ChaosEngineeringException;

    T getDatafromRemoteService(String offerId, T throwException) throws ChaosEngineeringException;

    MockClientServiceResponse fallbackOnFailure();

}
