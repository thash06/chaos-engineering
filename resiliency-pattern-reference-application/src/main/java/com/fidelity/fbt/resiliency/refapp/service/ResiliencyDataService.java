package com.fidelity.fbt.resiliency.refapp.service;

/**
 * @author souadhik
 * Interface for delegate service(for remote data service calls)
 */
public interface ResiliencyDataService {
	/**
	 * @return This methods returns mock response from the remote data service
	 */
	Object getDatafromRemoteServiceForFallbackPattern();

}
