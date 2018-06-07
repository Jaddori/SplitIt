package com.bojangles.splitit;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

public class EditActivity extends AppCompatActivity
{
	private ListView list_prices;
	private TextView lbl_total;
	private Button btn_back;
	private Button btn_reset;

	private PriceAdapter _priceAdapter;

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		setContentView( R.layout.activity_edit );

		_priceAdapter = new PriceAdapter( this, PricePart.GlobalPriceParts );
		_priceAdapter.setOnIncludeListener( new PriceAdapter.IncludeListener()
		{
			@Override
			public void onInclude( PricePart part )
			{
				updateTotal();
			}
		} );

		list_prices = (ListView)findViewById( R.id.list_prices );
		list_prices.setAdapter( _priceAdapter );

		lbl_total = (TextView)findViewById( R.id.lbl_total );

		btn_back = (Button)findViewById( R.id.btn_back );
		btn_back.setOnClickListener( new View.OnClickListener()
		{
			@Override
			public void onClick( View v )
			{
				finish();
			}
		} );

		btn_reset = (Button)findViewById( R.id.btn_reset );
		btn_reset.setOnClickListener( new View.OnClickListener()
		{
			@Override
			public void onClick( View v )
			{
				PricePart.GlobalPriceParts.clear();
				_priceAdapter.notifyDataSetChanged();
				lbl_total.setText( "0" );
			}
		} );

		updateTotal();
	}

	private void updateTotal()
	{
		Money total = new Money();

		for( PricePart part : PricePart.GlobalPriceParts )
		{
			if( part.getIncluded() )
			{
				Money price = new Money( part.getPrice() );
				if( part.getSplit() != 1 )
					price.div( part.getSplit() );

				total.add( price );
			}
		}

		lbl_total.setText( total.toString() );
	}
}
