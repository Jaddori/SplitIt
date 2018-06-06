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

	private static final int RECT_COLOR = Color.GREEN;
	private static final int TEXT_COLOR = Color.WHITE;

	private static Paint _rectPaint;
	private static Paint _textPaint;
	private final TextBlock _textBlock;

	OcrGraphic( GraphicOverlay overlay, TextBlock text )
	{
		super( overlay );

		_textBlock = text;

		if( _rectPaint == null )
		{
			_rectPaint = new Paint();
			_rectPaint.setColor( RECT_COLOR );
			_rectPaint.setStyle( Paint.Style.FILL );
		}

		if( _textPaint == null )
		{
			_textPaint = new Paint();
			_textPaint.setColor( TEXT_COLOR );
			_textPaint.setTextSize( 32.0f );
		}

		postInvalidate();
	}

	public int getId() { return _id; }
	public void setId( int id ) { _id = id; }
	public TextBlock getTextBlock() { return _textBlock; }

	public boolean contains( float x, float y )
	{
		boolean result = false;

		if( _textBlock != null )
		{
			RectF rect = new RectF( _textBlock.getBoundingBox() );
			rect = translateRect( rect );

			result = rect.contains( x, y );
		}

		return result;
	}

	@Override
	public void draw( Canvas canvas )
	{
		if( _textBlock != null )
		{
			RectF rect = new RectF( _textBlock.getBoundingBox() );
			rect = translateRect( rect );

			canvas.drawRect( rect, _rectPaint );

			List<? extends Text> textComponents = _textBlock.getComponents();
			for( Text currentText : textComponents )
			{
				Rect boundingBox = currentText.getBoundingBox();
				float left = translateX( boundingBox.left );
				float bottom = translateY( boundingBox.bottom );

				canvas.drawText( currentText.getValue(), left, bottom, _textPaint );
			}
		}
	}
}
