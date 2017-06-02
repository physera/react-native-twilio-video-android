package com.twilio.video;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import org.webrtc.EglBase;
import org.webrtc.GlRectDrawer;
import org.webrtc.GlUtil;
import org.webrtc.Logging;
import org.webrtc.RendererCommon;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoRenderer;

import java.util.concurrent.CountDownLatch;

public class RNSurfaceViewRenderer extends SurfaceView implements SurfaceHolder.Callback, VideoRenderer.Callbacks {
    private static final String TAG = "RNSurfaceViewRenderer";
    private HandlerThread renderThread;
    private final Object handlerLock = new Object();
    private Handler renderThreadHandler;
    private Handler uiThreadHandler;
    private EglBase eglBase;
    private final RendererCommon.YuvUploader yuvUploader = new RendererCommon.YuvUploader();
    private RendererCommon.GlDrawer drawer;
    private int[] yuvTextures = null;
    private final Object frameLock = new Object();
    private VideoRenderer.I420Frame pendingFrame;
    private final Object layoutLock        = new Object();
    private       Point  desiredLayoutSize = new Point();
    private final Point  layoutSize        = new Point();
    private final Point  surfaceSize       = new Point();
    private       boolean                       isSurfaceCreated;
    private       int                           frameWidth;
    private       int                           frameHeight;
    private       int                           frameRotation;
    private       RendererCommon.ScalingType    scalingType;
    private       boolean                       mirror;
    private       RendererCommon.RendererEvents rendererEvents;
    private final Object                        statisticsLock;
    private       int                           framesReceived;
    private       int                           framesDropped;
    private       int                           framesRendered;
    private       long                          firstFrameTimeNs;
    private       long                          renderTimeNs;
    private final Runnable                      renderFrameRunnable;
    private final Runnable                      makeBlackRunnable;

    protected RNSurfaceViewRenderer(Context context) {
        super(context);
        this.scalingType = RendererCommon.ScalingType.SCALE_ASPECT_BALANCED;
        this.statisticsLock = new Object();
        this.renderFrameRunnable = new Runnable() {
            public void run() {
                RNSurfaceViewRenderer.this.renderFrameOnRenderThread();
            }
        };
        this.makeBlackRunnable = new Runnable() {
            public void run() {
                RNSurfaceViewRenderer.this.makeBlack();
            }
        };
        this.getHolder().addCallback(this);
    }

    protected RNSurfaceViewRenderer(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.scalingType = RendererCommon.ScalingType.SCALE_ASPECT_BALANCED;
        this.statisticsLock = new Object();
        this.renderFrameRunnable = new Runnable() {
            public void run() {
                RNSurfaceViewRenderer.this.renderFrameOnRenderThread();
            }
        };
        this.makeBlackRunnable = new Runnable() {
            public void run() {
                RNSurfaceViewRenderer.this.makeBlack();
            }
        };
        this.getHolder().addCallback(this);
    }

    protected void init(org.webrtc.EglBase.Context sharedContext, RendererCommon.RendererEvents rendererEvents) {
        this.init(sharedContext, rendererEvents, EglBase.CONFIG_PLAIN, new GlRectDrawer());
    }

    protected void init(final org.webrtc.EglBase.Context sharedContext, RendererCommon.RendererEvents rendererEvents, final int[] configAttributes, RendererCommon.GlDrawer drawer) {
        Object var5 = this.handlerLock;
        synchronized(this.handlerLock) {
            if(this.renderThreadHandler != null) {
                throw new IllegalStateException(this.getResourceName() + "Already initialized");
            }

            Logging.d("SurfaceViewRenderer", this.getResourceName() + "Initializing.");
            this.rendererEvents = rendererEvents;
            this.drawer = drawer;
            this.renderThread = new HandlerThread("SurfaceViewRenderer");
            this.renderThread.start();
            this.renderThreadHandler = new Handler(this.renderThread.getLooper());
            this.uiThreadHandler = new Handler(Looper.getMainLooper());
            ThreadUtils.invokeAtFrontUninterruptibly(this.renderThreadHandler, new Runnable() {
                public void run() {
                    RNSurfaceViewRenderer.this.eglBase = EglBase.create(sharedContext, configAttributes);
                }
            });
        }

        this.tryCreateEglSurface();
    }

    protected void tryCreateEglSurface() {
        this.runOnRenderThread(new Runnable() {
            public void run() {
                synchronized(RNSurfaceViewRenderer.this.layoutLock) {
                    if(RNSurfaceViewRenderer.this.eglBase != null && RNSurfaceViewRenderer.this.isSurfaceCreated && !RNSurfaceViewRenderer.this.eglBase.hasSurface()) {
                        RNSurfaceViewRenderer.this.eglBase.createSurface(RNSurfaceViewRenderer.this.getHolder().getSurface());
                        RNSurfaceViewRenderer.this.eglBase.makeCurrent();
                        GLES20.glPixelStorei(3317, 1);
                    }

                }
            }
        });
    }

    protected void release() {
        final CountDownLatch eglCleanupBarrier = new CountDownLatch(1);
        Object var2 = this.handlerLock;
        synchronized(this.handlerLock) {
            if(this.renderThreadHandler == null) {
                Logging.d("SurfaceViewRenderer", this.getResourceName() + "Already released");
                return;
            }

            this.renderThreadHandler.postAtFrontOfQueue(new Runnable() {
                public void run() {
                    RNSurfaceViewRenderer.this.drawer.release();
                    RNSurfaceViewRenderer.this.drawer = null;
                    if(RNSurfaceViewRenderer.this.yuvTextures != null) {
                        GLES20.glDeleteTextures(3, RNSurfaceViewRenderer.this.yuvTextures, 0);
                        RNSurfaceViewRenderer.this.yuvTextures = null;
                    }

                    RNSurfaceViewRenderer.this.makeBlack();
                    RNSurfaceViewRenderer.this.eglBase.release();
                    RNSurfaceViewRenderer.this.eglBase = null;
                    eglCleanupBarrier.countDown();
                }
            });
            this.renderThreadHandler = null;
            this.uiThreadHandler = null;
        }

        ThreadUtils.awaitUninterruptibly(eglCleanupBarrier);
        this.renderThread.quit();
        var2 = this.frameLock;
        synchronized(this.frameLock) {
            if(this.pendingFrame != null) {
                VideoRenderer.renderFrameDone(this.pendingFrame);
                this.pendingFrame = null;
            }
        }

        ThreadUtils.joinUninterruptibly(this.renderThread);
        this.renderThread = null;
        var2 = this.layoutLock;
        synchronized(this.layoutLock) {
            this.frameWidth = 0;
            this.frameHeight = 0;
            this.frameRotation = 0;
            this.rendererEvents = null;
        }

        this.resetStatistics();
    }

    protected void resetStatistics() {
        Object var1 = this.statisticsLock;
        synchronized(this.statisticsLock) {
            this.framesReceived = 0;
            this.framesDropped = 0;
            this.framesRendered = 0;
            this.firstFrameTimeNs = 0L;
            this.renderTimeNs = 0L;
        }
    }

    protected void setMirror(boolean mirror) {
        Object var2 = this.layoutLock;
        synchronized(this.layoutLock) {
            this.mirror = mirror;
        }
    }

    protected void setScalingType(RendererCommon.ScalingType scalingType) {
        Object var2 = this.layoutLock;
        synchronized(this.layoutLock) {
            this.scalingType = scalingType;
        }
    }

    public void renderFrame(VideoRenderer.I420Frame frame) {
        Object var2 = this.statisticsLock;
        synchronized(this.statisticsLock) {
            ++this.framesReceived;
        }

        var2 = this.handlerLock;
        synchronized(this.handlerLock) {
            if(this.renderThreadHandler == null) {
                Logging.d("SurfaceViewRenderer", this.getResourceName() + "Dropping frame - Not initialized or already released.");
                VideoRenderer.renderFrameDone(frame);
            } else {
                Object var3 = this.frameLock;
                synchronized(this.frameLock) {
                    if(this.pendingFrame != null) {
                        Object var4 = this.statisticsLock;
                        synchronized(this.statisticsLock) {
                            ++this.framesDropped;
                        }

                        VideoRenderer.renderFrameDone(this.pendingFrame);
                    }

                    this.pendingFrame = frame;
                    this.renderThreadHandler.post(this.renderFrameRunnable);
                }

            }
        }
    }

    private Point getDesiredLayoutSize(int widthSpec, int heightSpec) {
        Object var3 = this.layoutLock;
        synchronized(this.layoutLock) {
            int maxWidth = getDefaultSize(2147483647, widthSpec);
            int maxHeight = getDefaultSize(2147483647, heightSpec);
            Point size = RendererCommon.getDisplaySize(this.scalingType, this.frameAspectRatio(), maxWidth, maxHeight);
            if(MeasureSpec.getMode(widthSpec) == MeasureSpec.AT_MOST) {
                size.x = maxWidth;
            }

            if(MeasureSpec.getMode(heightSpec) == MeasureSpec.AT_MOST) {
                size.y = maxHeight;
            }

            return size;
        }
    }

    protected void onMeasure(int widthSpec, int heightSpec) {
        Object var4 = this.layoutLock;
        boolean isNewSize;
        synchronized(this.layoutLock) {
            if(this.frameWidth == 0 || this.frameHeight == 0) {
                super.onMeasure(widthSpec, heightSpec);
                return;
            }

            this.desiredLayoutSize = this.getDesiredLayoutSize(widthSpec, heightSpec);
            isNewSize = this.desiredLayoutSize.x != this.getMeasuredWidth() || this.desiredLayoutSize.y != this.getMeasuredHeight();
            this.setMeasuredDimension(this.desiredLayoutSize.x, this.desiredLayoutSize.y);
        }

        if(isNewSize) {
            var4 = this.handlerLock;
            synchronized(this.handlerLock) {
                if(this.renderThreadHandler != null) {
                    this.renderThreadHandler.postAtFrontOfQueue(this.makeBlackRunnable);
                }
            }
        }

    }

    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        Object var6 = this.layoutLock;
        synchronized(this.layoutLock) {
            this.layoutSize.x = right - left;
            this.layoutSize.y = bottom - top;
        }

        this.runOnRenderThread(this.renderFrameRunnable);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Logging.d("SurfaceViewRenderer", this.getResourceName() + "Surface created.");
        Object var2 = this.layoutLock;
        synchronized(this.layoutLock) {
            this.isSurfaceCreated = true;
        }

        this.tryCreateEglSurface();
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Logging.d("SurfaceViewRenderer", this.getResourceName() + "Surface destroyed.");
        Object var2 = this.layoutLock;
        synchronized(this.layoutLock) {
            this.isSurfaceCreated = false;
            this.surfaceSize.x = 0;
            this.surfaceSize.y = 0;
        }

        this.runOnRenderThread(new Runnable() {
            public void run() {
                if(RNSurfaceViewRenderer.this.eglBase != null) {
                    RNSurfaceViewRenderer.this.eglBase.detachCurrent();
                    RNSurfaceViewRenderer.this.eglBase.releaseSurface();
                }

            }
        });
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Logging.d("SurfaceViewRenderer", this.getResourceName() + "Surface changed: " + width + "x" + height);
        Object var5 = this.layoutLock;
        synchronized(this.layoutLock) {
            this.surfaceSize.x = width;
            this.surfaceSize.y = height;
        }

        this.runOnRenderThread(this.renderFrameRunnable);
    }

    private void runOnRenderThread(Runnable runnable) {
        Object var2 = this.handlerLock;
        synchronized(this.handlerLock) {
            if(this.renderThreadHandler != null) {
                this.renderThreadHandler.post(runnable);
            }

        }
    }

    private String getResourceName() {
        try {
            return this.getResources().getResourceEntryName(this.getId()) + ": ";
        } catch (Resources.NotFoundException var2) {
            return "";
        }
    }

    private void makeBlack() {
        if(Thread.currentThread() != this.renderThread) {
            throw new IllegalStateException(this.getResourceName() + "Wrong thread.");
        } else {
            if(this.eglBase != null && this.eglBase.hasSurface()) {
                GLES20.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                GLES20.glClear(16384);
                this.eglBase.swapBuffers();
            }

        }
    }

    private boolean checkConsistentLayout() {
        if(Thread.currentThread() != this.renderThread) {
            throw new IllegalStateException(this.getResourceName() + "Wrong thread.");
        } else {
            Object var1 = this.layoutLock;
            synchronized(this.layoutLock) {
                return this.layoutSize.equals(this.desiredLayoutSize) && this.surfaceSize.equals(this.layoutSize);
            }
        }
    }

    private void renderFrameOnRenderThread() {
        if(Thread.currentThread() != this.renderThread) {
            throw new IllegalStateException(this.getResourceName() + "Wrong thread.");
        } else {
            Object startTimeNs = this.frameLock;
            VideoRenderer.I420Frame frame;
            synchronized(this.frameLock) {
                if(this.pendingFrame == null) {
                    return;
                }

                frame = this.pendingFrame;
                this.pendingFrame = null;
            }

            this.updateFrameDimensionsAndReportEvents(frame);
            if(this.eglBase != null && this.eglBase.hasSurface()) {
                // FIXME Added this so that it doesn't show black screen when coming on screen R.Pina 20170218
//                if(!this.checkConsistentLayout()) {
//                    Logging.d("SurfaceViewRenderer", this.getResourceName() + "detected inconsistent layout while renderering frame");
//                    this.makeBlack();
//                    VideoRenderer.renderFrameDone(frame);
//                } else {
                    startTimeNs = this.layoutLock;
                    synchronized(this.layoutLock) {
                        if(this.eglBase.surfaceWidth() != this.surfaceSize.x || this.eglBase.surfaceHeight() != this.surfaceSize.y) {
                            Logging.d("SurfaceViewRenderer", this.getResourceName() + "Egl surface does not match actual surface");
                            this.makeBlack();
                            VideoRenderer.renderFrameDone(frame);
                            return;
                        }
                    }

                    long var16 = System.nanoTime();
                    Object i = this.layoutLock;
                    float[] texMatrix;
                    synchronized(this.layoutLock) {
                        float[] rotatedSamplingMatrix = RendererCommon.rotateTextureMatrix(frame.samplingMatrix, (float)frame.rotationDegree);
                        float[] layoutMatrix = RendererCommon.getLayoutMatrix(this.mirror, this.frameAspectRatio(), (float)this.layoutSize.x / (float)this.layoutSize.y);
                        texMatrix = RendererCommon.multiplyMatrices(rotatedSamplingMatrix, layoutMatrix);
                    }

                    GLES20.glClear(16384);
                    if(frame.yuvFrame) {
                        if(this.yuvTextures == null) {
                            this.yuvTextures = new int[3];

                            for(int var17 = 0; var17 < 3; ++var17) {
                                this.yuvTextures[var17] = GlUtil.generateTexture(3553);
                            }
                        }

                        this.yuvUploader.uploadYuvData(this.yuvTextures, frame.width, frame.height, frame.yuvStrides, frame.yuvPlanes);
                        this.drawer.drawYuv(this.yuvTextures, texMatrix, frame.rotatedWidth(), frame.rotatedHeight(), 0, 0, this.surfaceSize.x, this.surfaceSize.y);
                    } else {
                        this.drawer.drawOes(frame.textureId, texMatrix, frame.rotatedWidth(), frame.rotatedHeight(), 0, 0, this.surfaceSize.x, this.surfaceSize.y);
                    }

                    this.eglBase.swapBuffers();
                    VideoRenderer.renderFrameDone(frame);
                    i = this.statisticsLock;
                    synchronized(this.statisticsLock) {
                        if(this.framesRendered == 0) {
                            this.firstFrameTimeNs = var16;
                            Object var18 = this.layoutLock;
                            synchronized(this.layoutLock) {
                                Logging.d("SurfaceViewRenderer", this.getResourceName() + "Reporting first rendered frame.");
                                if(this.rendererEvents != null) {
                                    this.rendererEvents.onFirstFrameRendered();
                                }
                            }
                        }

                        ++this.framesRendered;
                        this.renderTimeNs += System.nanoTime() - var16;
                        if(this.framesRendered % 300 == 0) {
                            this.logStatistics();
                        }

                    }
//                }
            } else {
                Logging.d("SurfaceViewRenderer", this.getResourceName() + "No surface to draw on");
                VideoRenderer.renderFrameDone(frame);
            }
        }
    }

    private float frameAspectRatio() {
        Object var1 = this.layoutLock;
        synchronized(this.layoutLock) {
            return this.frameWidth != 0 && this.frameHeight != 0?(this.frameRotation % 180 == 0?(float)this.frameWidth / (float)this.frameHeight:(float)this.frameHeight / (float)this.frameWidth):0.0F;
        }
    }

    private void updateFrameDimensionsAndReportEvents(VideoRenderer.I420Frame frame) {
        Object var2 = this.layoutLock;
        synchronized(this.layoutLock) {
            if(this.frameWidth != frame.width || this.frameHeight != frame.height || this.frameRotation != frame.rotationDegree) {
                Logging.d("SurfaceViewRenderer", this.getResourceName() + "Reporting frame resolution changed to " + frame.width + "x" + frame.height + " with rotation " + frame.rotationDegree);
                if(this.rendererEvents != null) {
                    this.rendererEvents.onFrameResolutionChanged(frame.width, frame.height, frame.rotationDegree);
                }

                this.frameWidth = frame.width;
                this.frameHeight = frame.height;
                this.frameRotation = frame.rotationDegree;
                this.uiThreadHandler.post(new Runnable() {
                    public void run() {
                        RNSurfaceViewRenderer.this.requestLayout();
                    }
                });
            }

        }
    }

    private void logStatistics() {
        Object var1 = this.statisticsLock;
        synchronized(this.statisticsLock) {
            Logging.d("SurfaceViewRenderer", this.getResourceName() + "Frames received: " + this.framesReceived + ". Dropped: " + this.framesDropped + ". Rendered: " + this.framesRendered);
            if(this.framesReceived > 0 && this.framesRendered > 0) {
                long timeSinceFirstFrameNs = System.nanoTime() - this.firstFrameTimeNs;
                Logging.d("SurfaceViewRenderer", this.getResourceName() + "Duration: " + (int)((double)timeSinceFirstFrameNs / 1000000.0D) + " ms. FPS: " + (double)this.framesRendered * 1.0E9D / (double)timeSinceFirstFrameNs);
                Logging.d("SurfaceViewRenderer", this.getResourceName() + "Average render time: " + (int)(this.renderTimeNs / (long)(1000 * this.framesRendered)) + " us.");
            }

        }
    }
}