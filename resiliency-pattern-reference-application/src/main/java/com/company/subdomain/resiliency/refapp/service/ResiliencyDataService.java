package com.company.subdomain.resiliency.refapp.service;

import com.company.subdomain.resiliency.refapp.exception.ChaosEngineeringException;
import com.company.subdomain.resiliency.refapp.model.MockClientServiceResponse;

public interface ResiliencyDataService<T> {
    T getDatafromRemoteService(T throwException) throws ChaosEngineeringException;

    T getDatafromRemoteService(String offerId, T throwException) throws ChaosEngineeringException;

    MockClientServiceResponse fallbackOnFailure();

}
