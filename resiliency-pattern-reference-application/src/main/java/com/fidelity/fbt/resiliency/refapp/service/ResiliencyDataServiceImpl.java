package com.fidelity.fbt.resiliency.refapp.service;

import com.fidelity.fbt.resiliency.refapp.enums.CouponType;
import com.fidelity.fbt.resiliency.refapp.enums.MarketType;
import com.fidelity.fbt.resiliency.refapp.enums.ProductType;
import com.fidelity.fbt.resiliency.refapp.model.MockClientServiceResponse;
import com.fidelity.fbt.resiliency.refapp.model.Offering;
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


    /**
     * TODO - Need to see if this annotation can be controlled through the interface, rather than impl class
     * This method returns mock response from a remote data service using rest template
     */

    //@HystrixCommand(fallbackMethod ="fallbackOnFailure")
    public Object getDatafromRemoteServiceForFallbackPattern() {

        int count = atomicInteger.incrementAndGet();
//        if(count % 3 == 0){
//            throw new ChaosEngineeringException("This exception is ignored by the CircuitBreaker of ChaosEngineeringDataService: Count of int " + atomicInteger.get());
//        }
//        else if(count % 5 == 0){
//            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "This is a remote client exception: Count of int " + atomicInteger.get());
//        }
//        else if(count % 7 == 0){
//            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "This is a remote exception: Count of int " + atomicInteger.get());
//        }
//        else if(count % 11 == 0){
//            throw new RuntimeException("This is a runtime exception: Count of int " + atomicInteger.get());
//        }

        Object responseEntity = this.restTemplate.getForObject(remoteServerUrl, Object.class);


        //MockClientServiceResponse response = responseEntity.getBody();
        //response.setMessage("Remote service is online!");
        return responseEntity;
    }

    /**
     * This is a fallback method is case of actual service call failure.
     * Suppress false warnings for the fallback method
     */
    @SuppressWarnings("unused")
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
