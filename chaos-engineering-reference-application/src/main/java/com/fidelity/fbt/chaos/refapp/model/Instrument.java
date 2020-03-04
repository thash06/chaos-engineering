package com.fidelity.fbt.chaos.refapp.model;

import com.fidelity.fbt.chaos.refapp.enums.CouponType;
import com.fidelity.fbt.chaos.refapp.enums.ProductType;

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
