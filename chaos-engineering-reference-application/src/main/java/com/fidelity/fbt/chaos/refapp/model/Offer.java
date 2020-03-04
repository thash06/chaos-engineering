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

import com.fidelity.fbt.chaos.refapp.enums.OfferType;

import lombok.Data;

/**
 * @author souadhik
 * Model for mock offer
 */
@Data
public class Offer {
	
	public Offer(OfferType type, double yield, double price, int quantity) {
		super();
		this.yield = yield;
		this.price = price;
		this.quantity = quantity;
		this.type = type;
	}

	private double yield;
	private double price;
	private int quantity;

	private OfferType type;
}
