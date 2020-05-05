package com.company.subdomain.resilience.refapp.model;

import com.company.subdomain.resilience.refapp.enums.CouponType;
import com.company.subdomain.resilience.refapp.enums.ProductType;
import lombok.Data;

@Data
public class Instrument {
	private ProductType productType;
	private String cusip;
	private String description;
	private String state;
	private String ticker;
	private CouponType couponType;

}
