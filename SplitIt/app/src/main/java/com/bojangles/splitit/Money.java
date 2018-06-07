package com.bojangles.splitit;

/**
 * Created by Tunder on 2018-06-07.
 */

public class Money
{
	private int _whole;
	private int _fractions;

	public Money setWhole( int whole )
	{
		_whole = whole;
		return this;
	}
	public Money setFractions( int fractions )
	{
		_fractions = fractions;
		validateFractions();

		return this;
	}

	public int getWhole()
	{
		return _whole;
	}
	public int getFractions()
	{
		return _fractions;
	}

	public Money()
	{
		_whole = 0;
		_fractions = 0;
	}

	public Money( int whole, int fractions )
	{
		_whole = whole;
		_fractions = fractions;

		validateFractions();
	}

	public Money( Money ref )
	{
		_whole = ref._whole;
		_fractions = ref._fractions;

		validateFractions();
	}

	public Money add( Money ref )
	{
		_whole += ref._whole;
		_fractions += ref._fractions;

		validateFractions();

		return this;
	}

	public Money sub( Money ref )
	{
		_whole -= ref._whole;
		_fractions -= ref._fractions;

		validateFractions();

		return this;
	}

	public Money mul( double amount )
	{
		double total = (double)_whole + ((double)_fractions)*0.01;
		total *= amount;

		_whole = (int)Math.round( total );
		_fractions = (int)Math.round( (total - _whole)*100 );

		validateFractions();

		return this;
	}

	public Money div( double amount )
	{
		double total = (double)_whole + ((double)_fractions)*0.01;
		total /= amount;

		_whole = (int)Math.round( total );
		_fractions = (int)Math.round( (total - _whole)*100 );

		validateFractions();

		return this;
	}

	public void validateFractions()
	{
		if( _fractions >= 100 )
		{
			int overflow = _fractions / 100;
			_whole += overflow;
			_fractions = _fractions % 100;
		}
		else if( _fractions < 0 )
		{
			while( _fractions < 0 )
			{
				_whole--;
				_fractions += 100;
			}
		}
	}

	public void parse( String str )
	{
		boolean isNegative = false;
		boolean reachedDigit = false;
		boolean reachedDelimiter = false;

		String wholePart = "0";
		String fractionsPart = "0";

		for( int i=0; i<str.length(); i++ )
		{
			if( !reachedDigit )
			{
				if( str.charAt( i ) == '-' )
					isNegative = true;
			}

			char c = str.charAt( i );
			if( c >= '0' && c <= '9' )
			{
				reachedDigit = true;

				if( reachedDelimiter )
					fractionsPart += c;
				else
					wholePart += c;
			}
			else if( c == '.' || c == ',' )
			{
				reachedDelimiter = true;
			}
		}

		_whole = Integer.parseInt( wholePart );
		if( isNegative )
			_whole = -_whole;
		_fractions = Integer.parseInt( fractionsPart );

		validateFractions();
	}

	@Override
	public String toString()
	{
		return Integer.toString( _whole ) + "." + Integer.toString( _fractions );
	}
}
