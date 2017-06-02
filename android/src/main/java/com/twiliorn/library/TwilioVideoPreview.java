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

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.twilio.video.CameraCapturer;
import com.twilio.video.ConnectOptions;
import com.twilio.video.LocalAudioTrack;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.Participant;
import com.twilio.video.RNVideoView;

public class TwilioVideoPreview extends RNVideoView {

    private static final String TAG = "TwilioVideoPreview";

    public TwilioVideoPreview(Context context) {
        super(context);
        CustomTwilioVideoView.registerThumbnailVideoView(this);
        this.setMirror(true);
    }
}
