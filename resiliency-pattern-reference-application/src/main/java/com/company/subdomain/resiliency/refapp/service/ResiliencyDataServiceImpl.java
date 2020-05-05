package com.company.subdomain.resiliency.refapp.service;

import com.company.subdomain.resiliency.refapp.enums.CouponType;
import com.company.subdomain.resiliency.refapp.enums.MarketType;
import com.company.subdomain.resiliency.refapp.enums.ProductType;
import com.company.subdomain.resiliency.refapp.exception.ChaosEngineeringException;
import com.company.subdomain.resiliency.refapp.model.MockClientServiceResponse;
import com.company.subdomain.resiliency.refapp.model.Offering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author souadhik
 * This class mimics a remote service delegate service implementation using rest template
 */
@Service
public class ResiliencyDataServiceImpl implements ResiliencyDataService {
    private static Logger LOGGER = LoggerFactory.getLogger(ResiliencyDataServiceImpl.class);
    private AtomicInteger atomicInteger = new AtomicInteger(0);

    /**
     * Configuration for remote service Url, specified in properties file
     */
    @Value("${remote.server.url}")
    private String remoteServerUrl;

    /**
     * Rest template for calling remote service
     */
    @Autowired
    private RestTemplate restTemplate;

    @Override
    public Object getDatafromRemoteService(Object throwException) throws ChaosEngineeringException {
        LOGGER.info(" Call got past decorator and now invoking Remote Endpoint throwException {} ", throwException);
        Object responseEntity = this.restTemplate.getForObject(String.format("%s?throwException=%s", remoteServerUrl, throwException), Object.class);
        return responseEntity;
    }

    @Override
    public Object getDatafromRemoteService(String offerId, Object throwException) throws ChaosEngineeringException {
        LOGGER.info(" Call got past decorator and now invoking Remote Endpoint for request with params");
        Object responseEntity =
                this.restTemplate.getForObject(String.format("%s/cache?offerId=%s&throwException=%s",
                        remoteServerUrl, offerId, throwException),
                        Object.class);
        return responseEntity;
    }

    /**
     * This is a fallback method is case of actual service call failure.
     * Suppress false warnings for the fallback method
     */
    @SuppressWarnings("unused")
    @Override
    public MockClientServiceResponse fallbackOnFailure() {

        List<Offering> cachedMockedOfferings = new ArrayList<Offering>();

        Offering offering = new Offering();
        offering.setCusip("90867KZL0");
        offering.setDescription("CAHCHED - OXNARD CALIF SCH DIST");
        offering.setProductType(ProductType.MUNICIPAL);
        //offering.setMaturityDate("2019-10-09");
        offering.setSnpRating("AAA+");
        offering.setCoupon(new BigDecimal("9.492520792897693"));
        offering.setCallable(true);
        offering.setState("MA");

        offering.setBidQty(368);
        offering.setBidPrice(new BigDecimal("90"));
        offering.setBidYtw(new BigDecimal("19.094702772764695"));

        offering.setAskQty(672);
        offering.setAskPrice(new BigDecimal("112"));
        offering.setAskYtw(new BigDecimal("18.376496375801576"));

        offering.setCouponType(CouponType.NONZERO);
        offering.setMarketType(MarketType.SECONDARY);
        offering.setDuration(new BigDecimal("0.05"));
        offering.setConvexity(new BigDecimal("0.02"));

        cachedMockedOfferings.add(offering);

        MockClientServiceResponse response = new MockClientServiceResponse();
        response.setData(cachedMockedOfferings);
        response.setMessage("Remote service not available, serving from cached data!!");

        String hostedRegion = "";
        response.setHostedRegion(hostedRegion);
        return response;
    }


    /**
     * Bean instantiation for RestTemplate.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
