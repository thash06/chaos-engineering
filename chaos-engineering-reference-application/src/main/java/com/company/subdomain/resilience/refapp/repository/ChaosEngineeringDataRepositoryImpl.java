package com.company.subdomain.resilience.refapp.repository;

import com.company.subdomain.resilience.refapp.enums.CouponType;
import com.company.subdomain.resilience.refapp.enums.MarketType;
import com.company.subdomain.resilience.refapp.enums.OfferType;
import com.company.subdomain.resilience.refapp.enums.ProductType;
import com.company.subdomain.resilience.refapp.model.Offer;
import com.company.subdomain.resilience.refapp.model.Offering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;


@Repository
public class ChaosEngineeringDataRepositoryImpl implements ChaosEngineeringDataRepository {
	private static Logger LOGGER = LoggerFactory.getLogger(ChaosEngineeringDataRepositoryImpl.class);
	/**
	 * This method returns a list of sample data to service layer,mimicking a database call.
	 */
	@Override
	public List<Offering> getSampleDataFromRepository() {
		// Ideally here we connect to database and fetch offerings data, for this POC, we will return some dummy offerings
		LOGGER.debug("getSampleDataFromRepository going to sleep");
		try {
			Thread.sleep(500);
		} catch (Exception e) {
			e.printStackTrace();
		}
		LOGGER.debug("getSampleDataFromRepository waking up");
		return getDummyOfferings();
	}

	/**
	 * @param offerId
	 * @return
	 */
	@Override
	public List<Offering> getSampleDataFromRepositoryById(String offerId) {
		List<Offering> dummyOfferings = getDummyOfferings();
		try {
			Thread.sleep(500);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return dummyOfferings
				.stream()
				.filter(ofr -> offerId.equals(ofr.getOfferId()))
				.collect(Collectors.toList());
	}

    /**
	 * This method generates a list of dummy offering data
	 */
	private List<Offering> getDummyOfferings() {
		Random random = new Random();
		List<Offering> offerings = new ArrayList<Offering>();

		String[] descriptions = {"OXNARD CALIF SCH DIST", "MORGAN STANLEY MTN", "FED Treasury Bond", "MANUFACTURER AND TRADERS NOTE"};
		ProductType[] types = {ProductType.CORPORATE, ProductType.MBS, ProductType.MUNICIPAL, ProductType.TREASURY};
		String[] states = {"MA", "NY"};

		int offerId = 1000;
		for (int i = 0; i < 100; i++) {
			Offer bid = new Offer(OfferType.BID, random.nextDouble() * 25, random.nextInt(100) + 50, random.nextInt(100) * 8);
			Offer ask = new Offer(OfferType.ASK, random.nextDouble() * 25, random.nextInt(100) + 50, random.nextInt(100) * 8);

			offerings.add(addMockOfferings(generateRandomCusip(), descriptions[random.nextInt(descriptions.length)], new BigDecimal(10 * random.nextDouble()), LocalDate.parse("2019-10-09"), "AAA+",
					types[random.nextInt(types.length)], true, states[random.nextInt(states.length)], bid, ask, ++offerId));
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
									  String snpRating, ProductType offeringClass, boolean callable, String state, Offer bid, Offer ask, int offerId) {

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
		offering.setOfferId(String.valueOf(offerId));
		return offering;
	}

}
