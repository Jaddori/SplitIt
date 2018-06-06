package com.bojangles.splitit;

import android.Manifest;
import android.content.Context;
import android.content.res.Configuration;
import android.support.annotation.RequiresPermission;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import java.io.IOException;

/**
 * Created by Tunder on 2018-06-06.
 */

public class CameraSourcePreview extends ViewGroup
{
	private static final String TAG = "CameraSourcePreview";

	private Context _context;
	private SurfaceView _surfaceView;
	private boolean _startRequested;
	private boolean _surfaceAvailable;
	private CameraSource _cameraSource;

	private GraphicOverlay _overlay;

	public CameraSourcePreview( Context context, AttributeSet attrs )
	{
		super( context, attrs );

		_context = context;
		_startRequested = false;
		_surfaceAvailable = false;

		_surfaceView = new SurfaceView( context );
		_surfaceView.getHolder().addCallback( new SurfaceCallback() );
		addView( _surfaceView );
	}

	@RequiresPermission( Manifest.permission.CAMERA )
	public void start( CameraSource cameraSource ) throws IOException, SecurityException
	{
		if( _cameraSource == null )
			stop();

		_cameraSource = cameraSource;

		if( _cameraSource != null )
		{
			_startRequested = true;
			startIfReady();
		}
	}

	@RequiresPermission( Manifest.permission.CAMERA )
	public void start( CameraSource cameraSource, GraphicOverlay overlay ) throws IOException, SecurityException
	{
		_overlay = overlay;
		start( cameraSource );
	}

	public void stop()
	{
		if( _cameraSource != null )
			_cameraSource.stop();
	}

	public void release()
	{
		if( _cameraSource != null )
		{
			_cameraSource.release();
			_cameraSource = null;
		}
	}

	@RequiresPermission( Manifest.permission.CAMERA )
	private void startIfReady() throws IOException, SecurityException
	{
		if( _startRequested && _surfaceAvailable )
		{
			_cameraSource.start( _surfaceView.getHolder() );

			if( _overlay != null )
			{
				Size size = _cameraSource.getPreviewSize();
				int min = Math.min( size.getWidth(), size.getHeight() );
				int max = Math.max( size.getWidth(), size.getHeight() );

				if( isPortraitMode() )
					_overlay.setCameraInfo( min, max, _cameraSource.getCameraFacing() );
				else
					_overlay.setCameraInfo( max, min, _cameraSource.getCameraFacing() );

				_overlay.clear();
			}

			_startRequested = false;
		}
	}

	private class SurfaceCallback implements SurfaceHolder.Callback
	{
		@Override
		public void surfaceCreated( SurfaceHolder surface )
		{
			_surfaceAvailable = true;
			try
			{
				startIfReady();
			}
			catch( SecurityException e )
			{
				Log.e( TAG, "Do not have permission to start the camera.", e );
			}
			catch( IOException e )
			{
				Log.e( TAG, "Could not start the camera source.", e );
			}
		}

		@Override
		public void surfaceDestroyed( SurfaceHolder surface )
		{
			_surfaceAvailable = false;
		}

		@Override
		public void surfaceChanged( SurfaceHolder holder, int format, int width, int height )
		{
		}
	}

	@Override
	protected void onLayout( boolean changed, int left, int top, int right, int bottom )
	{
		int previewWidth = 320;
		int previewHeight = 240;

		if( _cameraSource != null )
		{
			Size size = _cameraSource.getPreviewSize();
			if( size != null )
			{
				previewWidth = size.getWidth();
				previewHeight = size.getHeight();
			}
		}

		if( isPortraitMode() )
		{
			int tmp = previewWidth;
			previewWidth = previewHeight;
			previewHeight = tmp;
		}

		final int viewWidth = right - left;
		final int viewHeight = bottom - top;

		int childWidth;
		int childHeight;
		int childXOffset = 0;
		int childYOffset = 0;
		float widthRatio = (float)viewWidth / (float)previewWidth;
		float heightRatio = (float)viewHeight / (float)previewHeight;

		if( widthRatio > heightRatio )
		{
			childWidth = viewWidth;
			childHeight = (int)((float)previewHeight * widthRatio);
			childYOffset = (childHeight - viewHeight) / 2;
		}
		else
		{
			childWidth = (int)((float)previewWidth * heightRatio);
			childHeight = viewHeight;
			childXOffset = (childWidth - viewWidth) / 2;
		}

		for( int i=0; i<getChildCount(); i++ )
		{
			getChildAt( i ).layout( -1 * childXOffset, -1 * childYOffset, childWidth - childXOffset, childHeight - childYOffset );
		}

		try
		{
			startIfReady();
		}
		catch( SecurityException e )
		{
			Log.e( TAG, "Do not have permission to start the camera.", e );
		}
		catch( IOException e )
		{
			Log.e( TAG, "Could not start camera source.", e );
		}
	}

	private boolean isPortraitMode()
	{
		boolean result = false;

		int orientation = _context.getResources().getConfiguration().orientation;

		if( orientation == Configuration.ORIENTATION_PORTRAIT )
			result = true;

		return result;
	}
}
