package com.sanlin.mkeyboard.keyboard;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.util.Xml;

import com.sanlin.mkeyboard.R;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.List;

public class Keyboard {
    final static String TAG = "Keyboard";
    public List<Row> rows = new ArrayList<>();

    public Keyboard(Context context, int xmlResId, int totalWidth) {
        parseXml(context, xmlResId, totalWidth);
    }

    private void parseXml(Context context, int xmlResId, int totalWidth) {
        XmlResourceParser parser = context.getResources().getXml(xmlResId);
        String keyMdlNs = "http://schemas.android.com/apk/res-auto";
        try {
            int x = 0, y = 0;
            int defaultKeyWidth = 100, defaultKeyHeight = 100, hGap = 0, vGap = 0;
            Row currentRow = null;

            while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    String tag = parser.getName();

                    if ("Keyboard".equals(tag)) {
                        defaultKeyWidth = parseSize(parser.getAttributeValue(keyMdlNs, "defaultKeyWidth"), totalWidth);
                        defaultKeyHeight = parseSize(parser.getAttributeValue(keyMdlNs, "defaultKeyHeight"), totalWidth);

                        Log.d(TAG, "parseXml: "+defaultKeyWidth+" "+defaultKeyHeight);
                        hGap = parseSize(parser.getAttributeValue(keyMdlNs, "horizontalGap"), totalWidth);
                        vGap = parseSize(parser.getAttributeValue(keyMdlNs, "verticalGap"), totalWidth);
                    } else if ("Row".equals(tag)) {
                        x = 0;
                        y += defaultKeyHeight + vGap;
                        currentRow = new Row();
                        rows.add(currentRow);
                    } else if ("Key".equals(tag) && currentRow != null) {
                        AttributeSet attrs = Xml.asAttributeSet(parser);
                        Log.d("XML_PARSER", "AttributeSet count from Xml.asAttributeSet: " + attrs.getAttributeCount());
                        Rect bounds = new Rect(x, y, x + defaultKeyWidth, y + defaultKeyHeight);
                        // Prepare TypedArray for custom attributes
                        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Key);

                        Key key = new Key(context, attrs, a, x, y, defaultKeyWidth, defaultKeyHeight);

                        currentRow.keys.add(key);
                        x += key.getWidth() + hGap;
                        a.recycle();
                    }
                }
                parser.next();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int parseSize(String value, int baseWidth) {
        if (value == null) return 0;
        if (value.endsWith("%p")) {
            float percent = Float.parseFloat(value.replace("%p", ""));
            return (int) (baseWidth * percent / 100);
        } else if (value.endsWith("dp")) {
            float dp = Float.parseFloat(value.replace("dp", ""));
            return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, Resources.getSystem().getDisplayMetrics());
        } else if (value.endsWith("dip")) {
            float dp = Float.parseFloat(value.replace("dip", ""));
            return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, Resources.getSystem().getDisplayMetrics());
        } else {
            float px = Float.parseFloat(value.replace("px", ""));
            return (int) px;
        }
    }
}



