package com.twiliorn.library;

import android.content.Context;

import com.twilio.video.RNVideoViewGroup;

public class TwilioRemotePreview extends RNVideoViewGroup {

    private static final String TAG = "TwilioRemotePreview";

    public TwilioRemotePreview(Context context) {
        super(context);
        CustomTwilioVideoView.registerPrimaryVideoView(this.getSurfaceViewRenderer());
    }
}
