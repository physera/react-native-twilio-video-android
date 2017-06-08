package com.twiliorn.library;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringDef;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.View;

import com.twilio.video.RNVideoViewGroup;

public class TwilioVideoPreview extends RNVideoViewGroup {

    private static final String TAG = "TwilioVideoPreview";

    public TwilioVideoPreview(Context context) {
        super(context);
        CustomTwilioVideoView.registerThumbnailVideoView(this.getSurfaceViewRenderer());
        this.getSurfaceViewRenderer().setMirror(true);
        this.getSurfaceViewRenderer().applyZOrder(true);
    }
}
