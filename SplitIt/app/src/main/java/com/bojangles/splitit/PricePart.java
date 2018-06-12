package com.bojangles.splitit;

import java.util.ArrayList;

/**
 * Created by Tunder on 2018-06-07.
 */

public class PricePart
{
	public static ArrayList<PricePart> GlobalPriceParts = new ArrayList<>();

	private Money _price;
	private int _split;
	private boolean _included;

	public PricePart setPrices( Money price )
	{
		_price = price;
		return this;
	}
	public PricePart setSplit( int split )
	{
		_split = split;
		return this;
	}
	public PricePart setIncluded( boolean included )
	{
		_included = included;
		return this;
	}

	public Money getPrice()
	{
		return _price;
	}
	public int getSplit()
	{
		return _split;
	}
	public boolean getIncluded()
	{
		return _included;
	}

	public PricePart()
	{
		_price = new Money();
		_split = 1;
		_included = true;
	}

	public PricePart( Money price, int split, boolean included )
	{
		_price = price;
		_split = split;
		_included = included;
	}
}
