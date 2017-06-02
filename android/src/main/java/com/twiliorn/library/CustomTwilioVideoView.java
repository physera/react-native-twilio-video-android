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
import android.widget.FrameLayout;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.twilio.video.CameraCapturer;
import com.twilio.video.ConnectOptions;
import com.twilio.video.LocalAudioTrack;
import com.twilio.video.LocalMedia;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.Media;
import com.twilio.video.Participant;
import com.twilio.video.RNVideoView;
import com.twilio.video.Room;
import com.twilio.video.RoomState;
import com.twilio.video.TwilioException;
import com.twilio.video.VideoClient;
import com.twilio.video.VideoRenderer;
import com.twilio.video.VideoTrack;
import com.twiliorn.library.permissions.PermissionsManager;
import com.twiliorn.library.permissions.PermissionsResult;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

import rx.functions.Action1;

import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_AUDIO_CHANGED;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_CAMERA_SWITCHED;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_CONNECTED;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_CONNECT_FAILURE;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_DICONNECTED;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_PARTICIPANT_CONNECTED;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_PARTICIPANT_DISCONNECTED;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_VIDEO_CHANGED;

public class CustomTwilioVideoView extends FrameLayout implements LifecycleEventListener {

    private static final String TAG = "CustomTwilioVideoView";

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({Events.ON_CAMERA_SWITCHED,
            Events.ON_VIDEO_CHANGED,
            Events.ON_AUDIO_CHANGED,
            Events.ON_CONNECTED,
            Events.ON_CONNECT_FAILURE,
            Events.ON_DICONNECTED,
            Events.ON_PARTICIPANT_CONNECTED,
            Events.ON_PARTICIPANT_DISCONNECTED})
    public @interface Events {
        String ON_CAMERA_SWITCHED          = "onCameraSwitched";
        String ON_VIDEO_CHANGED            = "onVideoChanged";
        String ON_AUDIO_CHANGED            = "onAudioChanged";
        String ON_CONNECTED                = "onConnected";
        String ON_CONNECT_FAILURE          = "onConnectFailure";
        String ON_DICONNECTED              = "onDisconnected";
        String ON_PARTICIPANT_CONNECTED    = "onParticipantConnected";
        String ON_PARTICIPANT_DISCONNECTED = "onParticipantDisconnected";

    }

    private final ThemedReactContext themedReactContext;
    private final RCTEventEmitter    eventEmitter;
    private final PermissionsManager permissionsManager;

    /*
     * The Video Client allows a client to connect to a room
     */
    private VideoClient videoClient;

    /*
     * A Room represents communication between the client and one or more participants.
     */
    private Room room;

    /*
     * A VideoView receives frames from a local or remote video track and renders them
     * to an associated view.
     */
    private RNVideoView primaryVideoView;
    private RNVideoView thumbnailVideoView;

    private CameraCapturer  cameraCapturer;
    private LocalMedia      localMedia;
    private LocalAudioTrack localAudioTrack;
    private LocalVideoTrack localVideoTrack;
    private AudioManager    audioManager;
    private String          participantIdentity;
    private int             previousAudioMode;
    private VideoRenderer   localVideoView;
    private boolean         disconnectedFromOnDestroy;
    private String          accessToken;

    public CustomTwilioVideoView(ThemedReactContext themedReactContext) {
        super(themedReactContext);
        this.themedReactContext = themedReactContext;
        this.eventEmitter = themedReactContext.getJSModule(RCTEventEmitter.class);
        this.permissionsManager = PermissionsManager.get(themedReactContext);

        // add lifecycle for onResume and on onPause
        themedReactContext.addLifecycleEventListener(this);
        inflate(themedReactContext, R.layout.layout_video_preview, this);

        primaryVideoView = (RNVideoView) findViewById(R.id.primary_video_view);
        thumbnailVideoView = (RNVideoView) findViewById(R.id.thumbnail_video_view);

        /*
         * Enable changing the volume using the up/down keys during a conversation
         */
        if (themedReactContext.getCurrentActivity() != null) {
            themedReactContext.getCurrentActivity()
                              .setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        }
        /*
         * Needed for setting/abandoning audio focus during call
         */
        audioManager = (AudioManager) themedReactContext.getSystemService(Context.AUDIO_SERVICE);

        /*
         * Check camera and microphone permissions. Needed in Android M.
         */
        if (!permissionsManager.isCameraGranted() || !permissionsManager.isMicrophoneGranted()) {
            requestPermissionForCameraAndMicrophone();
        } else {
            createLocalMedia();
            createVideoClient();
        }
    }

    // ===== SETUP =================================================================================

    private void createLocalMedia() {
        localMedia = LocalMedia.create(getContext());

        // Share your microphone
        localAudioTrack = localMedia.addAudioTrack(true);

        // Share your camera
        cameraCapturer = new CameraCapturer(getContext(), CameraCapturer.CameraSource.FRONT_CAMERA);
        localVideoTrack = localMedia.addVideoTrack(true, cameraCapturer);
        primaryVideoView.setMirror(true);
        localVideoTrack.addRenderer(primaryVideoView);
        localVideoView = primaryVideoView;
    }

    private void createVideoClient() {
        /*
         * Create a VideoClient allowing you to connect to a Room
         */

        // OPTION 1- Generate an access token from the getting started portal
        // https://www.twilio.com/console/video/dev-tools/testing-tools
        if (accessToken != null) {
            videoClient = new VideoClient(getContext(), accessToken);
        }

        // OPTION 2- Retrieve an access token from your own web app
        // retrieveAccessTokenfromServer();
    }

    // ===== PERMISSIONS ===========================================================================

    private void requestPermissionForCameraAndMicrophone() {
        final Activity activity = themedReactContext.getCurrentActivity();
        if (activity != null) {
            if (permissionsManager.neverAskForCamera(activity) || permissionsManager.neverAskForMicrophone(activity)) {
                showPermissionsNeededSnackbar(activity);
            } else {
                permissionsManager.requestCameraAndMicrophonePermissions()
                                  .subscribe(new Action1<PermissionsResult>() {
                                      @Override
                                      public void call(PermissionsResult permissionsResult) {
                                          if (permissionsResult.isGranted()) {
                                              createLocalMedia();
                                              createVideoClient();
                                          } else {
                                              showPermissionsNeededSnackbar(activity);
                                          }
                                      }
                                  }, new Action1<Throwable>() {
                                      @Override
                                      public void call(Throwable throwable) {
                                          Log.e(TAG, "Requesting permissions threw. Something's wrong.... message: " + throwable.getMessage());
                                      }
                                  });
            }
        }
    }

    private void showPermissionsNeededSnackbar(@NonNull final Activity activity) {
        final Snackbar snackbar = Snackbar.make(activity.getWindow()
                                                        .getDecorView(),
                                                R.string.permissions_needed,
                                                Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(R.string.action_settings, new OnClickListener() {
            @Override
            public void onClick(View v) {
                snackbar.dismiss();
                permissionsManager.intentToAppSettings(activity);
            }
        });
        snackbar.show();
    }

    // ===== LIFECYCLE EVENTS ======================================================================

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        primaryVideoView.updateLayout(left, top, right, bottom);
    }

    @Override
    public void onHostResume() {
        /*
         * If the local video track was removed when the app was put in the background, add it back.
         */
        if (localMedia != null && localVideoTrack == null) {
            localVideoTrack = localMedia.addVideoTrack(true, cameraCapturer);
            localVideoTrack.addRenderer(localVideoView);
        }
        /*
         * In case it wasn't set.
         */
        if (themedReactContext.getCurrentActivity() != null) {
            themedReactContext.getCurrentActivity()
                              .setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        }
    }

    @Override
    public void onHostPause() {
        /*
         * Remove the local video track before going in the background. This ensures that the
         * camera can be used by other applications while this app is in the background.
         *
         * If this local video track is being shared in a Room, participants will be notified
         * that the track has been removed.
         */
        if (localMedia != null && localVideoTrack != null) {
            localMedia.removeVideoTrack(localVideoTrack);
            localVideoTrack = null;
        }
    }

    @Override
    public void onHostDestroy() {
        /*
         * Always disconnect from the room before leaving the Activity to
         * ensure any memory allocated to the Room resource is freed.
         */
        if (room != null && room.getState() != RoomState.DISCONNECTED) {
            room.disconnect();
            disconnectedFromOnDestroy = true;
        }

        /*
         * Release the local media ensuring any memory allocated to audio or video is freed.
         */
        if (localMedia != null) {
            localMedia.release();
            localMedia = null;
        }
    }

    // ====== CONNECTING ===========================================================================

    public void connectToRoom(String roomName) {
        setAudioFocus(true);
        ConnectOptions connectOptions = new ConnectOptions.Builder()
                .roomName(roomName)
                .localMedia(localMedia)
                .build();
        room = videoClient.connect(connectOptions, roomListener());
    }

    private void setAudioFocus(boolean focus) {
        if (focus) {
            previousAudioMode = audioManager.getMode();
            // Request audio focus before making any device switch.
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                                           AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            /*
             * Use MODE_IN_COMMUNICATION as the default audio mode. It is required
             * to be in this mode when playout and/or recording starts for the best
             * possible VoIP performance. Some devices have difficulties with
             * speaker mode if this is not set.
             */
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        } else {
            audioManager.setMode(previousAudioMode);
            audioManager.abandonAudioFocus(null);
        }
    }

    // ====== DISCONNECTING ========================================================================

    public void disconnect() {
        if (room != null) {
            room.disconnect();
        }
    }

    // ===== BUTTON LISTENERS ======================================================================

    public void switchCamera() {
        if (cameraCapturer != null) {
            CameraCapturer.CameraSource cameraSource = cameraCapturer.getCameraSource();
            final boolean isBackCamera = cameraSource == CameraCapturer.CameraSource.BACK_CAMERA;
            cameraCapturer.switchCamera();
            if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
                thumbnailVideoView.setMirror(isBackCamera);
            } else {
                primaryVideoView.setMirror(isBackCamera);
            }

            WritableMap event = new WritableNativeMap();
            event.putBoolean("isBackCamera", isBackCamera);
            pushEvent(CustomTwilioVideoView.this, ON_CAMERA_SWITCHED, event);
        }
    }

    public void toggleVideo() {
        if (localVideoTrack != null) {
            boolean enable = !localVideoTrack.isEnabled();
            localVideoTrack.enable(enable);

            WritableMap event = new WritableNativeMap();
            event.putBoolean("videoEnabled", enable);
            pushEvent(CustomTwilioVideoView.this, ON_VIDEO_CHANGED, event);
        }
    }

    public void toggleAudio() {
        if (localAudioTrack != null) {
            boolean enable = !localAudioTrack.isEnabled();
            localAudioTrack.enable(enable);

            WritableMap event = new WritableNativeMap();
            event.putBoolean("audioEnabled", enable);
            pushEvent(CustomTwilioVideoView.this, ON_AUDIO_CHANGED, event);
        }
    }

    // ====== ROOM LISTENER ========================================================================

    /*
     * Room events listener
     */
    private Room.Listener roomListener() {
        return new Room.Listener() {
            @Override
            public void onConnected(Room room) {
                WritableMap event = new WritableNativeMap();
                event.putString("room", room.getName());
                pushEvent(CustomTwilioVideoView.this, ON_CONNECTED, event);

                //noinspection LoopStatementThatDoesntLoop
                for (Map.Entry<String, Participant> entry : room.getParticipants()
                                                                .entrySet()) {
                    addParticipant(entry.getValue());
                    break;
                }
            }

            @Override
            public void onConnectFailure(Room room, TwilioException e) {
                WritableMap event = new WritableNativeMap();
                event.putString("reason", e.message);
                pushEvent(CustomTwilioVideoView.this, ON_CONNECT_FAILURE, event);
            }

            @Override
            public void onDisconnected(Room room, TwilioException e) {
                WritableMap event = new WritableNativeMap();
                event.putString("participant", participantIdentity);
                pushEvent(CustomTwilioVideoView.this, ON_DICONNECTED, event);

                CustomTwilioVideoView.this.room = null;
                // Only reinitialize the UI if disconnect was not called from onDestroy()
                if (!disconnectedFromOnDestroy) {
                    setAudioFocus(false);
                    moveLocalVideoToPrimaryView();
                }
            }

            @Override
            public void onParticipantConnected(Room room, Participant participant) {
                addParticipant(participant);
            }

            @Override
            public void onParticipantDisconnected(Room room, Participant participant) {
                removeParticipant(participant);
            }

            @Override
            public void onRecordingStarted(Room room) {
            }

            @Override
            public void onRecordingStopped(Room room) {
            }
        };
    }

    /*
     * Called when participant joins the room
     */
    private void addParticipant(Participant participant) {
        participantIdentity = participant.getIdentity();
        WritableMap event = new WritableNativeMap();
        event.putString("participant", participantIdentity);
        pushEvent(this, ON_PARTICIPANT_CONNECTED, event);

        /*
         * Add participant renderer
         */
        if (participant.getMedia()
                       .getVideoTracks()
                       .size() > 0) {
            addParticipantVideo(participant.getMedia()
                                           .getVideoTracks()
                                           .get(0));
        }

        /*
         * Start listening for participant media events
         */
        participant.getMedia()
                   .setListener(mediaListener());
    }

    /*
     * Called when participant leaves the room
     */
    private void removeParticipant(Participant participant) {
        WritableMap event = new WritableNativeMap();
        event.putString("participant", participantIdentity);
        pushEvent(this, ON_PARTICIPANT_DISCONNECTED, event);
        if (!participant.getIdentity()
                        .equals(participantIdentity)) {
            return;
        }

        /*
         * Remove participant renderer
         */
        if (participant.getMedia()
                       .getVideoTracks()
                       .size() > 0) {
            removeParticipantVideo(participant.getMedia()
                                              .getVideoTracks()
                                              .get(0));
        }
        participant.getMedia()
                   .setListener(null);
        moveLocalVideoToPrimaryView();
    }

    private void moveLocalVideoToPrimaryView() {
        localVideoTrack.removeRenderer(thumbnailVideoView);
        thumbnailVideoView.setVisibility(View.GONE);
        localVideoTrack.addRenderer(primaryVideoView);
        localVideoView = primaryVideoView;
        primaryVideoView.setMirror(cameraCapturer.getCameraSource() ==
                                           CameraCapturer.CameraSource.FRONT_CAMERA);
    }

    // ====== MEDIA LISTENER =======================================================================

    private Media.Listener mediaListener() {
        return new MediaListener() {
            @Override
            public void onVideoTrackAdded(Media media, VideoTrack videoTrack) {
                addParticipantVideo(videoTrack);
            }

            @Override
            public void onVideoTrackRemoved(Media media, VideoTrack videoTrack) {
                removeParticipantVideo(videoTrack);
            }
        };
    }

    /*
     * Set primary view as renderer for participant video track
     */
    private void addParticipantVideo(VideoTrack videoTrack) {
        moveLocalVideoToThumbnailView();
        primaryVideoView.setMirror(false);
        videoTrack.addRenderer(primaryVideoView);
    }

    private void moveLocalVideoToThumbnailView() {
        thumbnailVideoView.setVisibility(View.VISIBLE);
        localVideoTrack.removeRenderer(primaryVideoView);
        localVideoTrack.addRenderer(thumbnailVideoView);
        localVideoView = thumbnailVideoView;
        thumbnailVideoView.setMirror(cameraCapturer.getCameraSource() ==
                                             CameraCapturer.CameraSource.FRONT_CAMERA);
    }

    private void removeParticipantVideo(VideoTrack videoTrack) {
        videoTrack.removeRenderer(primaryVideoView);
    }

    public void setAccessToken(@Nullable String accessToken) {
        this.accessToken = accessToken;
        createVideoClient();
    }

    // ===== EVENTS TO RN ==========================================================================

    void pushEvent(View view, String name, WritableMap data) {
        eventEmitter.receiveEvent(view.getId(), name, data);
    }
}
