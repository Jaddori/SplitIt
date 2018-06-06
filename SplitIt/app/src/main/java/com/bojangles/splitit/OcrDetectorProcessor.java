package com.bojangles.splitit;

import android.util.Log;
import android.util.SparseArray;

import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.text.TextBlock;

/**
 * Created by Tunder on 2018-06-06.
 */

public class OcrDetectorProcessor implements Detector.Processor<TextBlock>
{
	private GraphicOverlay<OcrGraphic> _overlay;

	OcrDetectorProcessor( GraphicOverlay<OcrGraphic> overlay )
	{
		_overlay = overlay;
	}

	@Override
	public void receiveDetections( Detector.Detections<TextBlock> detections )
	{
		_overlay.clear();

		SparseArray<TextBlock> items = detections.getDetectedItems();
		for( int i = 0; i < items.size(); i++ )
		{
			TextBlock item = items.valueAt( i );
			if( item != null && item.getValue() != null )
			{
				Log.d( "OcrDetectorProcessor", "Text detected: " + item.getValue() );
				OcrGraphic graphic = new OcrGraphic( _overlay, item );
				_overlay.add( graphic );
			}
		}
	}

	@Override
	public void release()
	{
		_overlay.clear();
	}
}
