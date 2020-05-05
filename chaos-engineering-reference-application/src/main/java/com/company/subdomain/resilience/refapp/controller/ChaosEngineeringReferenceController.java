package com.company.subdomain.resilience.refapp.controller;

import com.company.subdomain.resilience.refapp.exception.ChaosEngineeringException;
import com.company.subdomain.resilience.refapp.model.MockDataServiceResponse;
import com.company.subdomain.resilience.refapp.service.OfferingsDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/data-service")
public class ChaosEngineeringReferenceController {

    private static Logger LOGGER = LoggerFactory.getLogger(ChaosEngineeringReferenceController.class);

    private final OfferingsDataService offeringsDataService;

    public ChaosEngineeringReferenceController(
            @Qualifier("offeringsDataService") OfferingsDataService offeringsDataService) {
        this.offeringsDataService = offeringsDataService;
    }

    /**
     * @return
     * @throws RuntimeException
     */
    @GetMapping("/vanillaOfferings")
    public MockDataServiceResponse offerings() throws ChaosEngineeringException {

        return offeringsDataService.getMockOfferingsDataFromService(false);
    }

    /**
     * @return
     * @throws RuntimeException
     */
    @GetMapping("/offerings")
    public MockDataServiceResponse offerings(@RequestParam Boolean throwException) throws ChaosEngineeringException {
        if (throwException) {
            throw new ChaosEngineeringException("Something went wrong!!");
        }
        return offeringsDataService.getMockOfferingsDataFromService(throwException);
    }

    /**
     * @return
     * @throws RuntimeException
     */
    @GetMapping("/offerings/cache")
    public MockDataServiceResponse offerings(@RequestParam String offerId, @RequestParam Boolean throwException) throws ChaosEngineeringException {
        if (throwException) {
            throw new ChaosEngineeringException("Something went wrong!!");
        }
        return offeringsDataService.getMockOfferingsDataFromService(offerId, throwException);
	}

}
