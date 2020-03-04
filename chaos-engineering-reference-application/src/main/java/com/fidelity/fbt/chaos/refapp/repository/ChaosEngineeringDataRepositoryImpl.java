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
package com.fidelity.fbt.chaos.refapp.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.fidelity.fbt.chaos.refapp.enums.CouponType;
import com.fidelity.fbt.chaos.refapp.enums.MarketType;
import com.fidelity.fbt.chaos.refapp.enums.OfferType;
import com.fidelity.fbt.chaos.refapp.enums.ProductType;
import com.fidelity.fbt.chaos.refapp.model.Offer;
import com.fidelity.fbt.chaos.refapp.model.Offering;

/**
 * @author souadhik
 * This class mimics a repository layer for data service. All responses are generated on the fly in this class.
 * For actual implementation, replace the mock response with database calls
 */

@Repository
public class ChaosEngineeringDataRepositoryImpl implements ChaosEngineeringDataRepository {

	/**
	 * This method returns a list of sample data to service layer,mimicking a database call.
	 */
	@Override
	public List<Offering> getSampleDataFromRepository() {
		// Ideally here we connect to database and fetch offerings data, for this POC, we will return some dummy offerings
		return getDummyOfferings();
	}

	/**
	 * This method returns a list of sample data after filtering by Id attribute
	 */
	@Override
	public List<Offering> getSampleDataFromRepositoryById(String Id) {
		List<Offering> dummyOfferings = getDummyOfferings();
		
		return dummyOfferings
					.stream()
					.filter(ofr->ofr.getOfferId().equals(Id))
							.collect(Collectors.toList());
	}
	
	/**
	 * This method generates a list of dummy offering data
	 */
	private List<Offering> getDummyOfferings()
	{
		Random random = new Random();
		List<Offering> offerings = new ArrayList<Offering>();
		
		String[] descriptions = {"OXNARD CALIF SCH DIST", "MORGAN STANLEY MTN", "FED Treasury Bond", "MANUFACTURER AND TRADERS NOTE"};
		ProductType[] types = {ProductType.CORPORATE, ProductType.MBS, ProductType.MUNICIPAL, ProductType.TREASURY};
		String[] states = {"MA", "NY"};
				
		
		for (int i = 0; i < 100; i++)
		{
			Offer bid = new Offer(OfferType.BID, random.nextDouble() * 25, random.nextInt(100) + 50, random.nextInt(100) * 8);
			Offer ask = new Offer(OfferType.ASK, random.nextDouble() * 25, random.nextInt(100) + 50, random.nextInt(100) * 8);
			
			offerings.add(addMockOfferings(generateRandomCusip(), descriptions[random.nextInt(descriptions.length)], new BigDecimal(10 * random.nextDouble()), LocalDate.parse("2019-10-09"), "AAA+",
					types[random.nextInt(types.length)], true, states[random.nextInt(states.length)], bid, ask));
		}
		
		return offerings;
	}
	
	/**
	 * This method generates a list of random sample Cusip data for dummy offerings list
	 */
	private String generateRandomCusip()
	{
		StringBuilder randomCusip = new StringBuilder();
		Random random = new Random();
		
		char[] alphabet = {'A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z'};
		
		randomCusip.append(random.nextInt(10));
		randomCusip.append(random.nextInt(10));
		randomCusip.append(random.nextInt(10));
		randomCusip.append(random.nextInt(10));
		randomCusip.append(random.nextInt(10));

		randomCusip.append(alphabet[random.nextInt(26)]);
		randomCusip.append(alphabet[random.nextInt(26)]);
		randomCusip.append(alphabet[random.nextInt(26)]);
		
		randomCusip.append(random.nextInt(10));
		
		return randomCusip.toString();
	}
	/**
	 * This method maps offerings attributes "Offering" Pojo
	 * @param cusip
	 * @param description
	 * @param coupon
	 * @param localDate
	 * @param snpRating
	 * @param offeringClass
	 * @param callable
	 * @param state
	 * @param bid
	 * @param ask
	 * @return
	 */
	private Offering addMockOfferings(String cusip, String description, BigDecimal coupon, LocalDate localDate,
			String snpRating, ProductType offeringClass, boolean callable, String state, Offer bid, Offer ask) {

		Offering offering = new Offering();
		offering.setCusip(cusip);
		offering.setDescription(description);
		offering.setProductType(offeringClass);
		offering.setMaturityDate(localDate);
		offering.setSnpRating(snpRating);
		offering.setCoupon(coupon);
		offering.setCallable(callable);
		offering.setState(state);

		offering.setBidQty(bid.getQuantity());
		offering.setBidPrice(new BigDecimal(bid.getPrice()));
		offering.setBidYtw(new BigDecimal(bid.getYield()));
		
		offering.setAskQty(ask.getQuantity());
		offering.setAskPrice(new BigDecimal(ask.getPrice()));
		offering.setAskYtw(new BigDecimal(ask.getYield()));
		
		offering.setCouponType(CouponType.NONZERO);
		offering.setMarketType(MarketType.SECONDARY);
		offering.setDuration(new BigDecimal("0.05"));
		offering.setConvexity(new BigDecimal("0.02"));
		
		return offering;
	}

}
