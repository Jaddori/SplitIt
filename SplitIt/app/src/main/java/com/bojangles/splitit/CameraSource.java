package com.bojangles.splitit;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Picture;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.*;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;
import android.support.annotation.StringDef;
import android.util.*;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.security.Policy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Tunder on 2018-06-06.
 */

@SuppressWarnings("deprecation")
public class CameraSource
{
	public static final int CAMERA_FACING_BACK = CameraInfo.CAMERA_FACING_BACK;
	public static final int CAMERA_FACING_FONT = CameraInfo.CAMERA_FACING_FRONT;

	private static final String TAG = "OpenCameraSource";

	private static final int DUMMY_TEXTURE_NAME = 100;
	private static final float ASPECT_RATIO_TOLERANCE = 0.01f;

	private Context _context;
	private final Object _cameraLock = new Object();
	private Camera _camera;
	private int _facing = CAMERA_FACING_BACK;

	private int _rotation;
	private Size _previewSize;
	private float _requestedFps = 30.0f;
	private int _requestedPreviewWidth = 1024;
	private int _requestedPreviewHeight = 768;

	private String _focusMode = null;
	private String _flashMode = null;

	private SurfaceView _dummySurfaceView;
	private SurfaceTexture _dummySurfaceTexture;

	private Thread _processingThread;
	private FrameProcessingRunnable _frameProcessor;

	private Map<byte[], ByteBuffer> _bytesToByteBuffer = new HashMap<>();

	public static class Builder
	{
		private final Detector<?> _detector;
		private CameraSource _cameraSource = new CameraSource();

		public Builder( Context context, Detector<?> detector )
		{
			if( context == null )
				throw new IllegalArgumentException( "No context supplied." );
			if( detector == null )
				throw new IllegalArgumentException( "No detector supplied." );

			_detector = detector;
			_cameraSource._context = context;
			_cameraSource._focusMode = Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
		}

		public Builder setRequestedFps( float fps )
		{
			if( fps <= 0 )
				throw new IllegalArgumentException( "Invalid fps: " + fps );

			_cameraSource._requestedFps = fps;
			return this;
		}

		public Builder setRequestedPreviewSize( int width, int height )
		{
			final int MAX = 1000000;
			if( width <= 0 || width > MAX || height <= 0 || height > MAX )
				throw new IllegalArgumentException( "Invalid preview size: " + width + "x" + height );

			_cameraSource._requestedPreviewWidth = width;
			_cameraSource._requestedPreviewHeight = height;

			return this;
		}

		public Builder setFacing( int facing )
		{
			if( facing != CAMERA_FACING_BACK && facing != CAMERA_FACING_FONT )
				throw new IllegalArgumentException( "Invalid camera: " + facing );

			_cameraSource._facing = facing;
			return this;
		}

		public CameraSource build()
		{
			_cameraSource._frameProcessor = _cameraSource.new FrameProcessingRunnable( _detector );

			return _cameraSource;
		}
	}

	private static class SizePair
	{
		private Size _preview;
		private Size _picture;

		public SizePair( android.hardware.Camera.Size previewSize, android.hardware.Camera.Size pictureSize )
		{
			_preview = new Size( previewSize.width, previewSize.height );
			if( pictureSize != null )
				_picture = new Size( pictureSize.width, pictureSize.height );
		}

		public Size previewSize()
		{
			return _preview;
		}
		public Size pictureSize()
		{
			return _picture;
		}
	}

	private class FrameProcessingRunnable implements Runnable
	{
		private Detector<?> _detector;
		private long _startTimeMs = SystemClock.elapsedRealtime();

		private final Object _lock = new Object();
		private boolean _active = true;

		private long _pendingTimeMs;
		private int _pendingFrameId = 0;
		private ByteBuffer _pendingFrameData;

		FrameProcessingRunnable( Detector<?> detector )
		{
			_detector = detector;
		}

		void release()
		{
			assert( _processingThread.getState() == Thread.State.TERMINATED );
			_detector.release();
			_detector = null;
		}

		void setActive( boolean active )
		{
			synchronized( _lock )
			{
				_active = active;
				_lock.notifyAll();
			}
		}

		void setNextFrame( byte[] data, Camera camera )
		{
			synchronized( _lock )
			{
				if( _pendingFrameData != null )
				{
					_camera.addCallbackBuffer( _pendingFrameData.array() );
					_pendingFrameData = null;
				}

				if( !_bytesToByteBuffer.containsKey( data ) )
				{
					Log.d( TAG, "Skipping frame. Could not find ByteBuffer associated with the image date from the camera." );
				}
				else
				{
					_pendingTimeMs = SystemClock.elapsedRealtime() - _startTimeMs;
					_pendingFrameId++;
					_pendingFrameData = _bytesToByteBuffer.get( data );

					_lock.notifyAll();
				}
			}
		}

		@Override
		public void run()
		{
			Frame frame;
			ByteBuffer data;

			while(true)
			{
				synchronized( _lock )
				{
					while( _active && _pendingFrameData == null )
					{
						try
						{
							_lock.wait();
						}
						catch( InterruptedException e )
						{
							Log.d( TAG, "Frame processing loop terminated.", e );
							return;
						}
					}

					if( !_active )
					{
						return;
					}

					frame = new Frame.Builder()
							.setImageData( _pendingFrameData, _previewSize.getWidth(), _previewSize.getHeight(), ImageFormat.NV21 )
							.setId( _pendingFrameId )
							.setTimestampMillis( _pendingTimeMs )
							.setRotation( _rotation )
							.build();

					data = _pendingFrameData;
					_pendingFrameData = null;
				}

				try
				{
					_detector.receiveFrame( frame );
				}
				catch( Throwable t )
				{
					Log.e( TAG, "Exception thrown from receiver.", t );
				}
				finally
				{
					_camera.addCallbackBuffer( data.array() );
				}
			}
		}
	}

	public interface ShutterCallback
	{
		void onShutter();
	}

	public interface PictureCallback
	{
		void onPictureTaken( byte[] data );
	}

	public interface AutoFocusCallback
	{
		void onAutoFocus( boolean success );
	}

	public interface AutoFocusMoveCallback
	{
		void onAutoFocusMoving( boolean start );
	}

	public void release()
	{
		synchronized( _cameraLock )
		{
			stop();
			_frameProcessor.release();
		}
	}

	@RequiresPermission( Manifest.permission.CAMERA )
	public CameraSource start() throws IOException
	{
		synchronized( _cameraLock )
		{
			if( _camera == null )
			{
				_camera = createCamera();

				if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB )
				{
					_dummySurfaceTexture = new SurfaceTexture( DUMMY_TEXTURE_NAME );
					_camera.setPreviewTexture( _dummySurfaceTexture );
				}
				else
				{
					_dummySurfaceView = new SurfaceView( _context );
					_camera.setPreviewDisplay( _dummySurfaceView.getHolder() );
				}

				_camera.startPreview();

				_processingThread = new Thread( _frameProcessor );
				_frameProcessor.setActive( true );
				_processingThread.start();
			}
		}

		return this;
	}

	@RequiresPermission( Manifest.permission.CAMERA )
	public CameraSource start( SurfaceHolder surfaceHolder ) throws IOException
	{
		synchronized( _cameraLock )
		{
			if( _camera == null )
			{
				_camera = createCamera();
				_camera.setPreviewDisplay( surfaceHolder );
				_camera.startPreview();

				_processingThread = new Thread( _frameProcessor );
				_frameProcessor.setActive( true );
				_processingThread.start();
			}
		}

		return this;
	}

	public void stop()
	{
		synchronized( _cameraLock )
		{
			_frameProcessor.setActive( false );
			if( _processingThread != null )
			{
				try
				{
					_processingThread.join();
				}
				catch( InterruptedException e )
				{
					Log.d( TAG, "Frame processing thread interrupted on release." );
				}
				_processingThread = null;
			}

			_bytesToByteBuffer.clear();
			if( _camera != null )
			{
				_camera.stopPreview();
				_camera.setPreviewCallbackWithBuffer( null );

				try
				{
					if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB )
						_camera.setPreviewTexture( null );
					else
						_camera.setPreviewDisplay( null );
				}
				catch( Exception e )
				{
					Log.e( TAG, "Failed to clear camera preview: " + e );
				}

				_camera.release();
				_camera = null;
			}
		}
	}

	public Size getPreviewSize()
	{
		return _previewSize;
	}
	public int getCameraFacing()
	{
		return _facing;
	}

	public void takePicture( ShutterCallback shutter, PictureCallback jpeg )
	{
		synchronized( _cameraLock )
		{
			if( _camera != null )
			{
				PictureStartCallback startCallback = new PictureStartCallback();
				startCallback._delegate = shutter;
				PictureDoneCallback doneCallback = new PictureDoneCallback();
				doneCallback._delegate = jpeg;
				_camera.takePicture( startCallback, null, null, doneCallback );
			}
		}
	}

	public void autoFocus( @Nullable AutoFocusCallback cb )
	{
		synchronized( _cameraLock )
		{
			CameraAutoFocusCallback autoFocusCallback = null;
			if( cb != null )
			{
				autoFocusCallback = new CameraAutoFocusCallback();
				autoFocusCallback._delegate = cb;
			}

			_camera.autoFocus( autoFocusCallback );
		}
	}

	public void cancelAutoFocus()
	{
		synchronized( _cameraLock )
		{
			if( _camera != null )
			{
				_camera.cancelAutoFocus();
			}
		}
	}

	@TargetApi( Build.VERSION_CODES.JELLY_BEAN )
	public boolean setAutoFocusMoveCallback( @Nullable AutoFocusMoveCallback cb )
	{
		if( Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN )
		{
			return false;
		}

		synchronized( _cameraLock )
		{
			if( _camera != null )
			{
				CameraAutoFocusMoveCallback autoFocusMoveCallback = null;
				if( cb != null )
				{
					autoFocusMoveCallback = new CameraAutoFocusMoveCallback();
					autoFocusMoveCallback._delegate = cb;
				}
				_camera.setAutoFocusMoveCallback( autoFocusMoveCallback );
			}
		}

		return true;
	}

	private CameraSource()
	{
	}

	private class PictureStartCallback implements Camera.ShutterCallback
	{
		private ShutterCallback _delegate;

		@Override
		public void onShutter()
		{
			if( _delegate != null )
				_delegate.onShutter();
		}
	}

	private class PictureDoneCallback implements Camera.PictureCallback
	{
		private PictureCallback _delegate;

		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			if (_delegate != null) {
				_delegate.onPictureTaken(data);
			}
			synchronized (_cameraLock) {
				if (CameraSource.this._camera != null) {
					CameraSource.this._camera.startPreview();
				}
			}
		}
	}

	private class CameraAutoFocusCallback implements Camera.AutoFocusCallback
	{
		private AutoFocusCallback _delegate;

		@Override
		public void onAutoFocus( boolean success, Camera camera )
		{
			if( _delegate != null )
				_delegate.onAutoFocus( success );
		}
	}

	@TargetApi( Build.VERSION_CODES.JELLY_BEAN )
	private class CameraAutoFocusMoveCallback implements Camera.AutoFocusMoveCallback
	{
		private AutoFocusMoveCallback _delegate;

		@Override
		public void onAutoFocusMoving( boolean start, Camera camera )
		{
			if( _delegate != null )
				_delegate.onAutoFocusMoving( start );
		}
	}

	private Camera createCamera()
	{
		int requestedCameraId = getIdForRequestedCamera( _facing );
		if( requestedCameraId == -1 )
			throw new RuntimeException( "Could not find requested camera." );

		Camera camera = Camera.open( requestedCameraId );

		SizePair sizePair = selectSizePair( camera, _requestedPreviewWidth, _requestedPreviewHeight );
		if( sizePair == null )
			throw new RuntimeException( "Could not find suitable preview size." );

		Size pictureSize = sizePair.pictureSize();
		_previewSize = sizePair.previewSize();

		int[] previewFpsRange = selectPreviewFpsRange( camera, _requestedFps );
		if( previewFpsRange == null )
			throw new RuntimeException( "Could not find suitable preview frames per second range." );

		Camera.Parameters parameters = camera.getParameters();

		if( pictureSize != null )
			parameters.setPictureSize( pictureSize.getWidth(), pictureSize.getHeight() );

		parameters.setPreviewSize( _previewSize.getWidth(), _previewSize.getHeight() );
		parameters.setPreviewFpsRange(
				previewFpsRange[Parameters.PREVIEW_FPS_MIN_INDEX],
				previewFpsRange[Parameters.PREVIEW_FPS_MAX_INDEX] );
		parameters.setPreviewFormat( ImageFormat.NV21 );

		setRotation( camera, parameters, requestedCameraId );

		if( _focusMode != null )
		{
			if( parameters.getSupportedFocusModes().contains( _focusMode ) )
			{
				parameters.setFocusMode( _focusMode );
			}
			else
				Log.i( TAG, "Camera focus mode: " + _focusMode + " is not supported on this device." );
		}

		_focusMode = parameters.getFocusMode();

		if( _flashMode != null )
		{
			if( parameters.getSupportedFlashModes().contains( _flashMode ) )
			{
				parameters.setFlashMode( _flashMode );
			}
			else
				Log.i( TAG, "Camera flash mode: " + _flashMode + " is not supported on this device." );
		}

		_flashMode = parameters.getFlashMode();

		camera.setParameters( parameters );

		camera.setPreviewCallbackWithBuffer( new CameraPreviewCallback() );
		camera.addCallbackBuffer( createPreviewBuffer( _previewSize ) );
		camera.addCallbackBuffer( createPreviewBuffer( _previewSize ) );
		camera.addCallbackBuffer( createPreviewBuffer( _previewSize ) );
		camera.addCallbackBuffer( createPreviewBuffer( _previewSize ) );

		return camera;
	}

	private static int getIdForRequestedCamera( int facing )
	{
		int result = -1;
		CameraInfo info = new CameraInfo();

		for( int i = 0; i < Camera.getNumberOfCameras() && result < 0; i++ )
		{
			Camera.getCameraInfo( i, info );

			if( info.facing == facing )
				result = i;
		}

		return result;
	}

	private static SizePair selectSizePair( Camera camera, int desiredWidth, int desiredHeight )
	{
		List<SizePair> validPreviewSizes = generateValidPreviewSizeList( camera );

		SizePair selectedPair = null;
		int minDiff = Integer.MAX_VALUE;

		for( SizePair sizePair : validPreviewSizes )
		{
			Size size = sizePair.previewSize();

			int diff = Math.abs( size.getWidth() - desiredWidth ) +
					Math.abs( size.getHeight() - desiredHeight );

			if( diff < minDiff )
			{
				selectedPair = sizePair;
				minDiff = diff;
			}
		}

		return selectedPair;
	}

	private static List<SizePair> generateValidPreviewSizeList( Camera camera )
	{
		Camera.Parameters parameters = camera.getParameters();

		List<android.hardware.Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
		List<android.hardware.Camera.Size> supportedPictureSizes = parameters.getSupportedPictureSizes();

		List<SizePair> validPreviewSizes = new ArrayList<>();

		for( android.hardware.Camera.Size previewSize : supportedPreviewSizes )
		{
			float previewAspectRatio = (float)previewSize.width / (float)previewSize.height;

			for( android.hardware.Camera.Size pictureSize : supportedPictureSizes )
			{
				float pictureAspectRatio = (float)pictureSize.width / (float)pictureSize.height;

				if( Math.abs( previewAspectRatio - pictureAspectRatio ) < ASPECT_RATIO_TOLERANCE )
				{
					validPreviewSizes.add( new SizePair( previewSize, pictureSize ) );
					break;
				}
			}
		}

		if( validPreviewSizes.size() == 0 )
		{
			Log.w( TAG, "No preview sizes have a corresponding same-aspect-ratio picture size." );

			for( android.hardware.Camera.Size previewSize : supportedPreviewSizes )
			{
				validPreviewSizes.add( new SizePair( previewSize, null ) );
			}
		}

		return validPreviewSizes;
	}

	private int[] selectPreviewFpsRange( Camera camera, float desiredPreviewFps )
	{
		int desiredPreviewFpsScaled = (int)(desiredPreviewFps * 1000.0f);

		int[] selectedFpsRange = null;
		int minDiff = Integer.MAX_VALUE;

		List<int[]> previewFpsRangeList = camera.getParameters().getSupportedPreviewFpsRange();

		for( int[] range : previewFpsRangeList )
		{
			int deltaMin = desiredPreviewFpsScaled - range[Parameters.PREVIEW_FPS_MIN_INDEX];
			int deltaMax = desiredPreviewFpsScaled - range[Parameters.PREVIEW_FPS_MAX_INDEX];

			int diff = Math.abs( deltaMin ) + Math.abs( deltaMax );
			if( diff < minDiff )
			{
				selectedFpsRange = range;
				minDiff = diff;
			}
		}

		return selectedFpsRange;
	}

	private void setRotation( Camera camera, Camera.Parameters parameters, int cameraId )
	{
		WindowManager windowManager = (WindowManager)_context.getSystemService( Context.WINDOW_SERVICE );

		int degrees = 0;
		int rotation = windowManager.getDefaultDisplay().getRotation();

		switch( rotation )
		{
			case Surface.ROTATION_0:
				degrees = 0;
				break;

			case Surface.ROTATION_90:
				degrees = 90;
				break;

			case Surface.ROTATION_180:
				degrees = 180;
				break;

			case Surface.ROTATION_270:
				degrees = 270;
				break;

			default:
				Log.e( TAG, "Bad rotation value: " + rotation );
		}

		CameraInfo info = new CameraInfo();
		Camera.getCameraInfo( cameraId, info );

		int angle;
		int displayAngle;

		if( info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT )
		{
			angle = (info.orientation + degrees) % 180;
			displayAngle = (360 - angle);
		}
		else
		{
			angle = (info.orientation - degrees + 360) % 360;
			displayAngle = angle;
		}

		_rotation = angle / 90;

		camera.setDisplayOrientation( displayAngle );
		parameters.setRotation( angle );
	}

	private byte[] createPreviewBuffer( Size previewSize )
	{
		int bitsPerPixel = ImageFormat.getBitsPerPixel( ImageFormat.NV21 );
		long sizeInBits = previewSize.getHeight() * previewSize.getWidth() * bitsPerPixel;
		int bufferSize = (int)Math.ceil( sizeInBits / 8.0d ) + 1;

		byte[] byteArray = new byte[bufferSize];
		ByteBuffer buffer = ByteBuffer.wrap( byteArray );
		if( !buffer.hasArray() || buffer.array() != byteArray )
			throw new IllegalStateException( "Failed to create valid buffer for camera source." );

		_bytesToByteBuffer.put( byteArray, buffer );
		return byteArray;
	}

	private class CameraPreviewCallback implements Camera.PreviewCallback
	{
		@Override
		public void onPreviewFrame( byte[] data, Camera camera )
		{
			_frameProcessor.setNextFrame( data, camera );
		}
	}
}
