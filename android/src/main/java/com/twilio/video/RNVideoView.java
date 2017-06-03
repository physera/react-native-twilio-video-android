package com.twilio.video;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;

import org.webrtc.RendererCommon;

public class RNVideoView extends RNSurfaceViewRenderer implements VideoRenderer {
    private final Handler                       uiThreadHandler;
    private final RendererCommon.RendererEvents internalEventListener;
    private       boolean                       mirror;
    private       boolean                       overlaySurface;
    private       VideoScaleType                videoScaleType;
    private       Listener                      listener;
    private       EglBaseProvider               eglBaseProvider;

    public RNVideoView(Context context) {
        this(context, (AttributeSet)null);
    }

    public RNVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.uiThreadHandler = new Handler(Looper.getMainLooper());
        this.internalEventListener = new RendererCommon.RendererEvents() {
            public void onFirstFrameRendered() {
                RNVideoView.this.refreshRenderer();
                if(RNVideoView.this.listener != null) {
                    RNVideoView.this.listener.onFirstFrame();
                }

            }

            public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {
                RNVideoView.this.refreshRenderer();
                if(RNVideoView.this.listener != null) {
                    RNVideoView.this.listener.onFrameDimensionsChanged(videoWidth, videoHeight, rotation);
                }

            }
        };
        this.mirror = false;
        this.overlaySurface = false;
        this.videoScaleType = VideoScaleType.ASPECT_FIT;
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if(!this.isInEditMode()) {
            this.eglBaseProvider = EglBaseProvider.instance(this);
            this.setupRenderer();
        }
    }

    protected void onDetachedFromWindow() {
        super.release();
        this.eglBaseProvider.release(this);
        super.onDetachedFromWindow();
    }

    public boolean getMirror() {
        return this.mirror;
    }

    public void setMirror(boolean mirror) {
        this.mirror = mirror;
        super.setMirror(mirror);
        this.refreshRenderer();
    }

    public VideoScaleType getVideoScaleType() {
        return this.videoScaleType;
    }

    public void setVideoScaleType(VideoScaleType videoScaleType) {
        this.videoScaleType = videoScaleType;
        this.setScalingType(this.convertToWebRtcScaleType(videoScaleType));
        this.refreshRenderer();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void renderFrame(I420Frame frame) {
        super.renderFrame(frame.webRtcI420Frame);
    }

    public void applyZOrder(boolean overlaySurface) {
        this.overlaySurface = overlaySurface;
        this.setZOrderMediaOverlay(overlaySurface);
    }

    private void setupRenderer() {
        this.init(this.eglBaseProvider.getRootEglBase().getEglBaseContext(), this.internalEventListener);
        this.setMirror(this.mirror);
        this.setScalingType(this.convertToWebRtcScaleType(this.videoScaleType));
        this.setZOrderMediaOverlay(this.overlaySurface);
    }

    private void refreshRenderer() {
        this.uiThreadHandler.post(new Runnable() {
            public void run() {
                RNVideoView.this.requestLayout();
            }
        });
    }

    private RendererCommon.ScalingType convertToWebRtcScaleType(VideoScaleType videoScaleType) {
        switch(videoScaleType) {
            case ASPECT_FIT:
                return RendererCommon.ScalingType.SCALE_ASPECT_FIT;
            case ASPECT_FILL:
                return RendererCommon.ScalingType.SCALE_ASPECT_FILL;
            case ASPECT_BALANCED:
                return RendererCommon.ScalingType.SCALE_ASPECT_BALANCED;
            default:
                return RendererCommon.ScalingType.SCALE_ASPECT_FIT;
        }
    }

    // FIXME Added this so that it re-lays out during orientation R.Pina 20170218
    public void updateLayout(int left, int top, int right, int bottom) {
        layout(left, top, right, bottom);
    }
}