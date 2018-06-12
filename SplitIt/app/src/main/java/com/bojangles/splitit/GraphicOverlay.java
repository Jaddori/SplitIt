package com.bojangles.splitit;

import android.content.*;
import android.graphics.*;
import android.util.*;
import android.view.*;

import com.google.android.gms.vision.CameraSource;

import java.util.*;

/**
 * Created by Tunder on 2018-06-06.
 */

public class GraphicOverlay<T extends GraphicOverlay.Graphic> extends View
{
	public static abstract class Graphic
	{
		private GraphicOverlay _overlay;

		public Graphic( GraphicOverlay overlay )
		{
			_overlay = overlay;
		}

		public abstract void draw( Canvas canvas );

		public abstract boolean contains( float x, float y );
		public float scaleX( float horizontal )
		{
			return horizontal * _overlay._widthScaleFactor;
		}
		public float scaleY( float vertical )
		{
			return vertical * _overlay._heightScaleFactor;
		}
		public float translateX( float x )
		{
			if( _overlay._facing == CameraSource.CAMERA_FACING_FRONT )
			{
				return _overlay.getWidth() - scaleX( x );
			}
			return scaleX( x );
		}
		public float translateY( float y )
		{
			return scaleY( y );
		}
		public RectF translateRect( RectF input )
		{
			RectF result = new RectF();

			result.left = translateX( input.left );
			result.top = translateY( input.top );
			result.right = translateX( input.right );
			result.bottom = translateY( input.bottom );

			return result;
		}
		public void postInvalidate()
		{
			_overlay.postInvalidate();
		}
	}

	private final Object _lock = new Object();
	private int _previewWidth;
	private int _previewHeight;
	private float _widthScaleFactor = 1.0f;
	private float _heightScaleFactor = 1.0f;
	private int _facing = CameraSource.CAMERA_FACING_BACK;
	private Set<T> _graphics = new HashSet<>();

	public GraphicOverlay( Context context, AttributeSet attrs )
	{
		super( context, attrs );
	}

	public void clear()
	{
		synchronized( _lock )
		{
			_graphics.clear();
		}
		postInvalidate();
	}

	public void add( T graphic )
	{
		synchronized( _lock )
		{
			_graphics.add( graphic );
		}
		postInvalidate();
	}

	public void remove( T graphic )
	{
		synchronized( _lock )
		{
			_graphics.remove( graphic );
		}
		postInvalidate();
	}

	public T getGraphicAtLocation( float rawX, float rawY )
	{
		T result = null;

		synchronized( _lock )
		{
			int[] location = new int[2];
			getLocationOnScreen( location );

			for( T graphic : _graphics )
			{
				if( graphic.contains( rawX - location[0], rawY - location[1] ) )
				{
					result = graphic;
					break;
				}
			}
		}

		return result;
	}

	public void setCameraInfo( int previewWidth, int previewHeight, int facing )
	{
		synchronized( _lock )
		{
			_previewWidth = previewWidth;
			_previewHeight = previewHeight;
			_facing = facing;
		}
		postInvalidate();
	}

	@Override
	protected void onDraw( Canvas canvas )
	{
		super.onDraw( canvas );

		synchronized( _lock )
		{
			if( _previewWidth != 0 && _previewHeight != 0 )
			{
				_widthScaleFactor = (float)canvas.getWidth() / (float)_previewWidth;
				_heightScaleFactor = (float)canvas.getHeight() / (float)_previewHeight;
			}

			for( Graphic graphic : _graphics )
			{
				graphic.draw( canvas );
			}
		}
	}

	public List<T> getGraphics()
	{
		ArrayList<T> result = new ArrayList<>();

		for( T graphic : _graphics )
		{
			result.add( graphic );
		}

		return result;
	}
}
