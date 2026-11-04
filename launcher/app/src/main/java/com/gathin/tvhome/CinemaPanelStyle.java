package com.gathin.tvhome;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

/** Shared native panel typography. Does not change actions, permissions or confirmation defaults. */
final class CinemaPanelStyle {
    static final int TEXT=0xFFF1F5FC, SECONDARY=0xFFB9C9DF, MUTED=0xFF9AAFCB;
    static GradientDrawable backdrop(){return new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF182338,0xFF0A101D,0xFF121827});}
    static void title(TextView view){view.setTextColor(TEXT);view.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));view.setIncludeFontPadding(false);}
    static void dialog(AlertDialog dialog){
        if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF19283E,0xFF101925}));
        for(int id:new int[]{-1,-2,-3}){TextView button=dialog.getButton(id);if(button!=null){button.setTextColor(TEXT);button.setAllCaps(false);button.setTextSize(19);}}
    }
    private CinemaPanelStyle(){}
}
