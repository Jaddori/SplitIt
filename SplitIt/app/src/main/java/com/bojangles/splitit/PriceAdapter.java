package com.bojangles.splitit;

import android.content.Context;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by Tunder on 2018-06-07.
 */

public class PriceAdapter extends BaseAdapter
{
	public interface IncludeListener
	{
		void onInclude( PricePart part );
	}

	private Context _context;
	private LayoutInflater _inflater;
	private ArrayList<PricePart> _pricesParts;
	private IncludeListener _includeListener;

	public void setOnIncludeListener( IncludeListener listener )
	{
		_includeListener = listener;
	}

	public PriceAdapter( Context context, ArrayList<PricePart> priceParts )
	{
		_context = context;
		_pricesParts = priceParts;
		_inflater = (LayoutInflater)_context.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
	}

	@Override
	public int getCount()
	{
		return _pricesParts.size();
	}

	@Override
	public Object getItem( int index )
	{
		return _pricesParts.get( index );
	}

	@Override
	public long getItemId( int index )
	{
		return index;
	}

	@Override
	public View getView( int index, View convertView, ViewGroup parent )
	{
		View rowView = _inflater.inflate( R.layout.list_item_pricepart, parent, false );

		final TextView lbl_price = (TextView)rowView.findViewById( R.id.lbl_price );
		final Spinner spn_split = (Spinner)rowView.findViewById( R.id.spn_split );
		final CheckBox cb_include = (CheckBox)rowView.findViewById( R.id.cb_include );

		final PricePart part = (PricePart)getItem( index );

		lbl_price.setText( part.getPrice().toString() );

		spn_split.setSelection( part.getSplit()-1 );
		spn_split.setOnItemSelectedListener( new AdapterView.OnItemSelectedListener()
		{
			@Override
			public void onItemSelected( AdapterView<?> parent, View view, int position, long id )
			{
				HandleItemSelected( position, part, lbl_price );
			}

			@Override
			public void onNothingSelected( AdapterView<?> parent )
			{
			}
		} );

		cb_include.setChecked( part.getIncluded() );
		cb_include.setOnCheckedChangeListener( new CompoundButton.OnCheckedChangeListener()
		{
			@Override
			public void onCheckedChanged( CompoundButton buttonView, boolean isChecked )
			{
				HandleCheckboxChanged( isChecked, part );
			}
		} );

		return rowView;
	}

	private void HandleCheckboxChanged( boolean isChecked, PricePart part )
	{
		part.setIncluded( isChecked );

		if( _includeListener != null )
			_includeListener.onInclude( part );
	}

	private void HandleItemSelected( int index, PricePart part, TextView label )
	{
		part.setSplit( index+1 );

		if( _includeListener != null )
			_includeListener.onInclude( part );

		if( index > 0 )
		{
			Money price = new Money( part.getPrice() );
			price.div( index + 1 );

			label.setText( price.toString() + " (" + part.getPrice().toString() + " / " + Integer.toString( index + 1 ) + ")" );
		}
	}
}
