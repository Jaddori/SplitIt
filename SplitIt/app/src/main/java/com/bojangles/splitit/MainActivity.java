package com.bojangles.splitit;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.text.Text;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class MainActivity extends AppCompatActivity
{
	private static final String TAG = "MainActivity";

	private static final int RC_HANDLE_GMS = 9001;
	private static final int RC_HANDLE_CAMERA_PERM = 2;

	private CameraSource _cameraSource;
	private CameraSourcePreview _preview;
	private GraphicOverlay<OcrGraphic> _graphicOverlay;
	private TextView lbl_total;
	private Button btn_toggleScan;
	private Button btn_edit;
	private boolean _frozen;

	private GestureDetector _gestureDetector;
	private OcrDetectorProcessor _detectorProcessor;

	private Set<OcrGraphic> _selectedGraphics;

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		setContentView( R.layout.activity_main );

		_frozen = true;
		_selectedGraphics = new HashSet<OcrGraphic>();

		_preview = (CameraSourcePreview)findViewById( R.id.preview );
		_graphicOverlay = (GraphicOverlay<OcrGraphic>)findViewById( R.id.graphicOverlay );
		lbl_total = (TextView)findViewById( R.id.lbl_total );
		btn_toggleScan = (Button)findViewById( R.id.btn_toggleScan );
		btn_edit = (Button)findViewById( R.id.btn_edit );

		int rc = ActivityCompat.checkSelfPermission( this, Manifest.permission.CAMERA );
		if( rc == PackageManager.PERMISSION_GRANTED )
		{
			createCameraSource();
		}
		else
		{
			requestCameraPermission();
		}

		_gestureDetector = new GestureDetector( this, new CaptureGestureListener() );
		btn_toggleScan.setOnClickListener( new View.OnClickListener()
		{
			@Override
			public void onClick( View v )
			{
				HandleToggleScan();
			}
		} );
		btn_edit.setOnClickListener( new View.OnClickListener()
		{
			@Override
			public void onClick( View v )
			{
				HandleEdit();
			}
		} );
	}

	private void requestCameraPermission()
	{
		Log.w( TAG, "Camera permission is not granted. Requesting permission." );

		final String[] permissions = new String[]{ Manifest.permission.CAMERA };

		if( !ActivityCompat.shouldShowRequestPermissionRationale( this, Manifest.permission.CAMERA ) )
		{
			ActivityCompat.requestPermissions( this, permissions, RC_HANDLE_CAMERA_PERM );
		}
		else
		{
			final Activity thisActivity = this;

			View.OnClickListener listener = new View.OnClickListener()
			{
				@Override
				public void onClick( View view )
				{
					ActivityCompat.requestPermissions( thisActivity, permissions, RC_HANDLE_CAMERA_PERM );
				}
			};

			/*Snackbar.make( _graphicOverlay, R.string.permission_camera_rationale, Snackbar.LENGTH_INDEFINITE )
					.setAction( R.string.ok, listener )
					.show();*/
		}
	}

	@Override
	public boolean onTouchEvent( MotionEvent e )
	{
		boolean t = _gestureDetector.onTouchEvent( e );

		return t || super.onTouchEvent( e );
	}

	private void createCameraSource()
	{
		Context context = getApplicationContext();

		TextRecognizer textRecognizer = new TextRecognizer.Builder( context ).build();
		_detectorProcessor = new OcrDetectorProcessor( _graphicOverlay );
		textRecognizer.setProcessor( _detectorProcessor );

		if( !textRecognizer.isOperational() )
		{
			Log.w( TAG, "Detector dependencies are not yet available." );

			IntentFilter lowStorageFilter = new IntentFilter( Intent.ACTION_DEVICE_STORAGE_LOW );
			boolean hasLowStorage = registerReceiver( null, lowStorageFilter ) != null;

			if( hasLowStorage )
			{
				Toast.makeText( this, R.string.low_storage_error, Toast.LENGTH_LONG ).show();
				Log.w( TAG, getString( R.string.low_storage_error ) );
			}
		}

		_cameraSource =
				new CameraSource.Builder( context, textRecognizer )
				.setFacing( CameraSource.CAMERA_FACING_BACK )
				.setRequestedPreviewSize( 1280, 1024 )
				.setRequestedFps( 2.0f )
				.build();
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		lbl_total.setText( "0" );
		startCameraSource();
	}

	@Override
	protected void onPause()
	{
		super.onPause();

		if( _preview != null )
			_preview.stop();
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();

		if( _preview != null )
			_preview.release();
	}

	@Override
	public void onRequestPermissionsResult( int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults )
	{
		if( requestCode != RC_HANDLE_CAMERA_PERM )
		{
			Log.d( TAG, "Got unexpected permission result: " + requestCode );
			super.onRequestPermissionsResult( requestCode, permissions, grantResults );
			return;
		}

		if( grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED )
		{
			Log.d( TAG, "Camera permission granted - initialize the camera source." );

			createCameraSource();
		}
		else
		{
			Log.e( TAG, "Permission not granted: results len = " + grantResults.length + " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)") );

			DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick( DialogInterface dialog, int which )
				{
					finish();
				}
			};

			AlertDialog.Builder builder = new AlertDialog.Builder( this );
			builder.setTitle( "SplitIt" )
					.setMessage( R.string.no_camera_permission )
					.setPositiveButton( R.string.ok, listener )
					.show();
		}
	}

	private void startCameraSource() throws SecurityException
	{
		int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable( getApplicationContext() );

		if( code != ConnectionResult.SUCCESS )
		{
			Dialog dlg = GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS );
			dlg.show();
		}

		if( _cameraSource != null )
		{
			try
			{
				_preview.start( _cameraSource, _graphicOverlay );
			}
			catch( IOException e )
			{
				Log.e( TAG, "Unable to start camera source.", e );

				_cameraSource.release();
				_cameraSource = null;
			}
		}
	}

	private boolean onTap( float rawX, float rawY )
	{
		boolean result = false;

		if( _frozen )
		{
			OcrGraphic graphic = _graphicOverlay.getGraphicAtLocation( rawX, rawY );

			if( graphic != null )
			{
				//TextBlock text = graphic.getTextBlock();
				Text text = graphic.getText();

				if( text != null && text.getValue() != null )
				{
					if( graphic.toggleSelection() )
						_selectedGraphics.add( graphic );
					else
					{
						if( _selectedGraphics.contains( graphic ) ) // this should never not be the case
							_selectedGraphics.remove( graphic );
					}

					UpdateTotal();

					result = true;
				}
			}
		}

		return result;
	}

	private void UpdateTotal()
	{
		Money total = new Money();

		for( OcrGraphic graphic : _selectedGraphics )
		{
			Money money = new Money();
			money.parse( graphic.getTextValue() );

			total.add( money );
		}

		lbl_total.setText( total.toString() );
	}

	private class CaptureGestureListener extends GestureDetector.SimpleOnGestureListener
	{
		@Override
		public boolean onSingleTapConfirmed( MotionEvent e )
		{
			return onTap( e.getRawX(), e.getRawY() ) || super.onSingleTapConfirmed( e );
		}
	}

	private void HandleToggleScan()
	{
		_frozen = !_frozen;
		_detectorProcessor.setFrozen( _frozen );

		btn_toggleScan.setText( _frozen ? "Scan" : "Freeze" );
	}

	private void HandleEdit()
	{
		PricePart.GlobalPriceParts.clear();
		for( OcrGraphic graphic : _selectedGraphics )
		{
			Money money = new Money();
			money.parse( graphic.getTextValue() );

			PricePart part = new PricePart( money, 1, true );

			PricePart.GlobalPriceParts.add( part );
		}

		_selectedGraphics.clear();
		_graphicOverlay.clear();

		Intent intent = new Intent( this, EditActivity.class );
		startActivity( intent );
	}
}
