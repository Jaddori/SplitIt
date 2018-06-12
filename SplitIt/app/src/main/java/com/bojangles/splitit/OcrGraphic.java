package com.bojangles.splitit;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import com.google.android.gms.vision.text.Text;
import com.google.android.gms.vision.text.TextBlock;

import java.util.List;

/**
 * Created by Tunder on 2018-06-06.
 */

public class OcrGraphic extends GraphicOverlay.Graphic
{
	private int _id;

	private static Paint _rectPaint;
	private static Paint _selectedPaint;
	private static Paint _textPaint;
	//private final TextBlock _textBlock;
	private final Text _text;
	private boolean _selected;

	OcrGraphic( GraphicOverlay overlay, Text text )
	{
		super( overlay );

		//_textBlock = text;
		_text = text;

		if( _rectPaint == null )
		{
			_rectPaint = new Paint();
			//_rectPaint.setColor( Color.argb( 0.25f, 1.0f, 0.0f, 0.0f ) );
			_rectPaint.setColor( Color.argb( 64, 255, 0, 0 ) );
			_rectPaint.setStyle( Paint.Style.FILL );
		}

		if( _selectedPaint == null )
		{
			_selectedPaint = new Paint();
			//_selectedPaint.setColor( Color.argb( 0.25f, 0.0f, 1.0f, 0.0f ) );
			_selectedPaint.setColor( Color.argb( 64, 0, 255, 0 ) );
		}

		if( _textPaint == null )
		{
			_textPaint = new Paint();
			_textPaint.setColor( Color.WHITE );
			_textPaint.setTextSize( 32.0f );
		}

		postInvalidate();
	}

	public int getId() { return _id; }
	public void setId( int id ) { _id = id; }
	//public TextBlock getTextBlock() { return _textBlock; }
	//public String getText() { return _textBlock.getValue(); }
	public Text getText() { return _text; }
	public String getTextValue() { return _text.getValue(); }
	public boolean getSelected() { return _selected; }

	public boolean contains( float x, float y )
	{
		boolean result = false;

		//if( _textBlock != null )
		if( _text != null )
		{
			//RectF rect = new RectF( _textBlock.getBoundingBox() );
			RectF rect = new RectF( _text.getBoundingBox() );
			rect = translateRect( rect );

			result = rect.contains( x, y );
		}

		return result;
	}

	public boolean toggleSelection()
	{
		_selected = !_selected;
		postInvalidate();
		return _selected;
	}

	@Override
	public void draw( Canvas canvas )
	{
		//if( _textBlock != null )
		if( _text != null )
		{
			//RectF rect = new RectF( _textBlock.getBoundingBox() );
			RectF rect = new RectF( _text.getBoundingBox() );
			rect = translateRect( rect );

			canvas.drawRect( rect, (_selected ? _selectedPaint : _rectPaint) );

			float left = translateX( rect.left );
			float bottom = translateY( rect.bottom );

			canvas.drawText( _text.getValue(), left, bottom, _textPaint );
		}
	}
}
