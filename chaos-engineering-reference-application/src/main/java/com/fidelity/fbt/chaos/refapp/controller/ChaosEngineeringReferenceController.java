package com.fidelity.fbt.chaos.refapp.controller;

import com.fidelity.fbt.chaos.refapp.exception.ChaosEngineeringException;
import com.fidelity.fbt.chaos.refapp.model.MockDataServiceResponse;
import com.fidelity.fbt.chaos.refapp.service.ChaosEngineeringDataService;
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

	private final ChaosEngineeringDataService chaosEngineeringDataService;

    public ChaosEngineeringReferenceController(
            @Qualifier("chaosEngineeringDataService") ChaosEngineeringDataService chaosEngineeringDataService) {
        this.chaosEngineeringDataService = chaosEngineeringDataService;
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
        return chaosEngineeringDataService.getMockOfferingsDataFromService();
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
        return chaosEngineeringDataService.getMockOfferingsDataFromService(offerId);
	}

}
