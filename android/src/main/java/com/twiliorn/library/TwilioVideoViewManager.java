package com.twiliorn.library;

import android.support.annotation.Nullable;

import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;

public class TwilioVideoViewManager extends SimpleViewManager<TwilioVideoView> {

    public static final String REACT_CLASS = "RNTwilioVideoView";

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @Override
    protected TwilioVideoView createViewInstance(ThemedReactContext reactContext) {
        return new TwilioVideoView(reactContext);
    }

    @ReactProp(name = "twilioAccessToken")
    public void setAccessToken(TwilioVideoView view, @Nullable String accessToken) {
        view.setAccessToken(accessToken);
    }
}
