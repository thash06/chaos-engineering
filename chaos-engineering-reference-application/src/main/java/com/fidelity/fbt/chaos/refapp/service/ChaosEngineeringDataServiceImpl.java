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
package com.fidelity.fbt.chaos.refapp.service;

import com.fidelity.fbt.chaos.refapp.model.MockDataServiceResponse;
import com.fidelity.fbt.chaos.refapp.model.Offering;
import com.fidelity.fbt.chaos.refapp.repository.ChaosEngineeringDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

//import com.amazonaws.regions.Region;
//import com.amazonaws.regions.Regions;

/**
 * @author souadhik
 *
 */
@Service
@Component(value = "chaosEngineeringDataService")
public class ChaosEngineeringDataServiceImpl implements ChaosEngineeringDataService {
	private static final String SERVICE = "data-service";

	private AtomicInteger atomicInteger = new AtomicInteger(0);
	/**
	 *  Dependency for repository layer
	 */
	@Autowired
	private ChaosEngineeringDataRepository chaosEngineeringDataRepository;

	/**
	 * This function returns sample data from the repository layer along
	 * with the hosted AWS region!
	 */
	@Override
	public MockDataServiceResponse getMockOfferingsDataFromService() {
		String hostedRegion = "";

//		Region region = Regions.getCurrentRegion();
//		if (region != null)
//		{
//			hostedRegion = region.getName();
//		}

		List<Offering> mockOffers = chaosEngineeringDataRepository.getSampleDataFromRepository();
		MockDataServiceResponse response = new MockDataServiceResponse();
		response.setData(mockOffers);
		response.setHostedRegion(hostedRegion);
		return response;
	}

}
