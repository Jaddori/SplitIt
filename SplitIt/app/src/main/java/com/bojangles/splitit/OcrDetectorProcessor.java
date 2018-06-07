package com.bojangles.splitit;

import android.graphics.Rect;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.text.Text;
import com.google.android.gms.vision.text.TextBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Tunder on 2018-06-06.
 */

public class OcrDetectorProcessor implements Detector.Processor<TextBlock>
{
	private GraphicOverlay<OcrGraphic> _overlay;
	private boolean _frozen;
	private Pattern _digitPattern;

	public void setFrozen( boolean frozen ) { _frozen = frozen; }
	public boolean getFrozen() { return _frozen; }

	OcrDetectorProcessor( GraphicOverlay<OcrGraphic> overlay )
	{
		_overlay = overlay;
		_digitPattern = Pattern.compile( "[0-9]" );
		_frozen = true;
	}

	@Override
	public void receiveDetections( Detector.Detections<TextBlock> detections )
	{
		if( !_frozen )
		{
			_overlay.clear();

			SparseArray<TextBlock> items = detections.getDetectedItems();
			for( int i = 0; i < items.size(); i++ )
			{
				TextBlock item = items.valueAt( i );
				if( item != null && item.getValue() != null )
				{
					List<? extends Text> textComponents = item.getComponents();
					for( Text currentText : textComponents )
					{
						String str = currentText.getValue();
						Matcher matcher = _digitPattern.matcher( str );

						if( matcher.find() )
						{
							Log.d( "OcrDetectorProcessor", "Text detected: " + str );
							OcrGraphic graphic = new OcrGraphic( _overlay, currentText );
							_overlay.add( graphic );
						}
					}
				}
			}
		}
	}

	@Override
	public void release()
	{
		_overlay.clear();
	}

	public List<String> getSelectedStrings()
	{
		ArrayList<String> result = new ArrayList<>();

		for( OcrGraphic graphic : _overlay.getGraphics() )
		{
			if( graphic.getSelected() )
			{
				String str = graphic.getTextValue();
				result.add( str );
			}
		}

		return result;
	}
}
