/*******************************************************************************
 * Copyright 2020 souadhik
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package com.fidelity.fbt.chaos.refapp.controller;

import com.fidelity.fbt.chaos.refapp.model.MockDataServiceResponse;
import com.fidelity.fbt.chaos.refapp.service.ChaosEngineeringDataService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author souadhik
 * Controller class for mock data service for Chaos management
 */
@RestController
@RequestMapping("/data-service")
public class ChaosEngineeringReferenceController {


	/**
	 * Data layer dependency for invoking data layer methods
	 */
	private final ChaosEngineeringDataService chaosEngineeringDataService;

	public ChaosEngineeringReferenceController(
			@Qualifier("chaosEngineeringDataService")ChaosEngineeringDataService chaosEngineeringDataService
			){
		this.chaosEngineeringDataService = chaosEngineeringDataService;
	}

	/**
	 * @return
	 * @throws RuntimeException
	 */
	@GetMapping("/offerings")
	public MockDataServiceResponse offerings() {
		//return execute(chaosEngineeringDataService::getMockOfferingsDataFromService, this::fallback);
		return chaosEngineeringDataService.getMockOfferingsDataFromService();
	}

}
