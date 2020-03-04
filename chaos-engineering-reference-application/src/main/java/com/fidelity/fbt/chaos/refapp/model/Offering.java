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
package com.fidelity.fbt.chaos.refapp.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fidelity.fbt.chaos.refapp.enums.CouponType;
import com.fidelity.fbt.chaos.refapp.enums.MarketType;
import com.fidelity.fbt.chaos.refapp.enums.ProductType;

import lombok.Data;

/**
 * @author souadhik
 * Model for mock offerings
 */
@Data
public class Offering {

	private ProductType productType;
	private String cusip;
	private String description;
	private String state;
	private String ticker;
	private CouponType couponType;

	private String industry;
	private BigDecimal coupon;
	private LocalDate maturityDate;
	private boolean callable;
	private boolean taxable;
	private String moodyRating;
	private String snpRating;

	private String offerId;

	//added for calculation
	private BigDecimal duration;
	private BigDecimal convexity;
	private MarketType marketType;


	private int askQty;
	private int askMinQty;
	private int askMinIncrement;
	private BigDecimal askPrice;
	private BigDecimal askYtw;
	private BigDecimal askYtm;

	private int bidQty;
	private int bidMinQty;
	private int bidMinIncrement;
	private BigDecimal bidPrice;
	private BigDecimal bidYtw;
	private BigDecimal bidYtm;

	//attributes to capture mark up values
	private BigDecimal askDeltaPrice;
	private BigDecimal askDeltaYield;
	private BigDecimal bidDeltaPrice;
	private BigDecimal bidDeltaYield;
}
