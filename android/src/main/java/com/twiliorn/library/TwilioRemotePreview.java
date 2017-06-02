package com.twiliorn.library;

import android.content.Context;

import com.twilio.video.RNVideoView;

public class TwilioRemotePreview extends RNVideoView {

    private static final String TAG = "TwilioRemotePreview";

    public TwilioRemotePreview(Context context) {
        super(context);
        CustomTwilioVideoView.registerPrimaryVideoView(this);
    }
}
