package com.twilio.video;

import android.content.Context;
import android.graphics.Point;
import android.view.ViewGroup;
import android.util.Log;

import org.webrtc.RendererCommon;

public class RNVideoViewGroup extends ViewGroup {
  private VideoView surfaceViewRenderer = null;
  private int videoWidth = 0;
  private int videoHeight = 0;
  private final Object layoutSync = new Object();


  public RNVideoViewGroup(Context context) {
    super(context);

    surfaceViewRenderer = new VideoView(context);
    addView(surfaceViewRenderer);
    surfaceViewRenderer.setListener(
        new VideoRenderer.Listener() {
          @Override
          public void onFirstFrame() {

          }

          @Override
          public void onFrameDimensionsChanged(int vw, int vh, int rotation) {
            synchronized (layoutSync) {
              videoHeight = vh;
              videoWidth = vw;
            }
          }
        }
    );
    Log.i("RNVVG", surfaceViewRenderer.toString());
  }

  public VideoView getSurfaceViewRenderer() {
    return surfaceViewRenderer;
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int height = b - t;
    int width = r - l;
    if (height == 0 || width == 0) {
      l = t = r = b = 0;
    }

    int videoHeight;
    int videoWidth;
    synchronized (layoutSync) {
      videoHeight = this.videoHeight;
      videoWidth = this.videoWidth;
    }

    Point displaySize = RendererCommon.getDisplaySize(
        RendererCommon.ScalingType.SCALE_ASPECT_FIT,
        videoWidth / (float) videoHeight,
        width,
        height
    );

    l = (width - displaySize.x) / 2;
    t = (height - displaySize.y) / 2;
    r = l + displaySize.x;
    b = t + displaySize.y;

    surfaceViewRenderer.layout(l, t, r, b);
  }
}
