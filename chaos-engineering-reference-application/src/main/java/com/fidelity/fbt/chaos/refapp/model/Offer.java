package com.fidelity.fbt.chaos.refapp.model;

import com.fidelity.fbt.chaos.refapp.enums.OfferType;
import lombok.Data;

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
