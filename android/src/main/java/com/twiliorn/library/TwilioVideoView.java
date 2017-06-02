package com.twiliorn.library;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.media.AudioManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.facebook.react.bridge.LifecycleEventListener;
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

import java.util.Map;

import rx.functions.Action1;

public class TwilioVideoView extends FrameLayout implements LifecycleEventListener {

    private static final String TAG = "TwilioVideoView";

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

    private TextView             videoStatusTextView;
    private CameraCapturer       cameraCapturer;
    private LocalMedia           localMedia;
    private LocalAudioTrack      localAudioTrack;
    private LocalVideoTrack      localVideoTrack;
    private FloatingActionButton connectActionFab;
    private FloatingActionButton switchCameraActionFab;
    private FloatingActionButton localVideoActionFab;
    private FloatingActionButton muteActionFab;
    private AlertDialog          alertDialog;
    private AudioManager         audioManager;
    private String               participantIdentity;
    private int                  previousAudioMode;
    private VideoRenderer        localVideoView;
    private boolean              disconnectedFromOnDestroy;
    private String               accessToken;

    public TwilioVideoView(ThemedReactContext themedReactContext) {
        super(themedReactContext);
        this.themedReactContext = themedReactContext;
        this.eventEmitter = themedReactContext.getJSModule(RCTEventEmitter.class);
        this.permissionsManager = PermissionsManager.get(themedReactContext);

        // add lifecycle for onResume and on onPause
        themedReactContext.addLifecycleEventListener(this);
        inflate(themedReactContext, R.layout.layout_video_view, this);

        primaryVideoView = (RNVideoView) findViewById(R.id.primary_video_view);
        thumbnailVideoView = (RNVideoView) findViewById(R.id.thumbnail_video_view);
        videoStatusTextView = (TextView) findViewById(R.id.video_status_textview);

        connectActionFab = (FloatingActionButton) findViewById(R.id.connect_action_fab);
        switchCameraActionFab = (FloatingActionButton) findViewById(R.id.switch_camera_action_fab);
        localVideoActionFab = (FloatingActionButton) findViewById(R.id.local_video_action_fab);
        muteActionFab = (FloatingActionButton) findViewById(R.id.mute_action_fab);

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

        intializeUI();
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

    /*
     * The initial state when there is no active conversation.
     */
    private void intializeUI() {
        connectActionFab.setImageDrawable(ContextCompat.getDrawable(getContext(),
                                                                    R.drawable.ic_call_white_24px));
        connectActionFab.show();
        connectActionFab.setOnClickListener(connectActionClickListener());
        switchCameraActionFab.show();
        switchCameraActionFab.setOnClickListener(switchCameraClickListener());
        localVideoActionFab.show();
        localVideoActionFab.setOnClickListener(localVideoClickListener());
        muteActionFab.show();
        muteActionFab.setOnClickListener(muteClickListener());
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
        snackbar.setAction(R.string.action_settings, new View.OnClickListener() {
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

    // ===== BUTTON LISTENERS ======================================================================

    // ----- ROOM DIALOG ---------------------------------------------------------------------------
    /*
     * Creates an connect UI dialog
     */
    private void showConnectDialog() {
        final EditText roomEditText = new EditText(getContext());
        alertDialog = Dialog.createConnectDialog(roomEditText,
                                                 connectClickListener(roomEditText), cancelConnectDialogClickListener(), getContext());
        alertDialog.show();
    }

    private DialogInterface.OnClickListener cancelConnectDialogClickListener() {
        return new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                intializeUI();
                alertDialog.dismiss();
            }
        };
    }

    // ----- CONNECTING ----------------------------------------------------------------------------

    private View.OnClickListener connectActionClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showConnectDialog();
            }
        };
    }

    private DialogInterface.OnClickListener connectClickListener(final EditText roomEditText) {
        return new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                /*
                 * Connect to room
                 */
                connectToRoom(roomEditText.getText()
                                          .toString());
            }
        };
    }

    private View.OnClickListener disconnectClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * Disconnect from room
                 */
                if (room != null) {
                    room.disconnect();
                }
                intializeUI();
            }
        };
    }

    // ----- CALL ACTIONS --------------------------------------------------------------------------

    private View.OnClickListener switchCameraClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cameraCapturer != null) {
                    CameraCapturer.CameraSource cameraSource = cameraCapturer.getCameraSource();
                    cameraCapturer.switchCamera();
                    if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
                        thumbnailVideoView.setMirror(cameraSource == CameraCapturer.CameraSource.BACK_CAMERA);
                    } else {
                        primaryVideoView.setMirror(cameraSource == CameraCapturer.CameraSource.BACK_CAMERA);
                    }
                }
            }
        };
    }

    private View.OnClickListener localVideoClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * Enable/disable the local video track
                 */
                if (localVideoTrack != null) {
                    boolean enable = !localVideoTrack.isEnabled();
                    localVideoTrack.enable(enable);
                    int icon;
                    if (enable) {
                        icon = R.drawable.ic_videocam_green_24px;
                        switchCameraActionFab.show();
                    } else {
                        icon = R.drawable.ic_videocam_off_red_24px;
                        switchCameraActionFab.hide();
                    }
                    localVideoActionFab.setImageDrawable(
                            ContextCompat.getDrawable(getContext(), icon));
                }
            }
        };
    }

    private View.OnClickListener muteClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * Enable/disable the local audio track. The results of this operation are
                 * signaled to other Participants in the same Room. When an audio track is
                 * disabled, the audio is muted.
                 */
                if (localAudioTrack != null) {
                    boolean enable = !localAudioTrack.isEnabled();
                    localAudioTrack.enable(enable);
                    int icon = enable ?
                               R.drawable.ic_mic_green_24px : R.drawable.ic_mic_off_red_24px;
                    muteActionFab.setImageDrawable(ContextCompat.getDrawable(
                            getContext(), icon));
                }
            }
        };
    }

    // ====== CONNECTING ===========================================================================

    private void connectToRoom(String roomName) {
        setAudioFocus(true);
        ConnectOptions connectOptions = new ConnectOptions.Builder()
                .roomName(roomName)
                .localMedia(localMedia)
                .build();
        room = videoClient.connect(connectOptions, roomListener());
        setDisconnectAction();
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

    /*
     * The actions performed during disconnect.
     */
    private void setDisconnectAction() {
        connectActionFab.setImageDrawable(ContextCompat.getDrawable(getContext(),
                                                                    R.drawable.ic_call_end_white_24px));
        connectActionFab.show();
        connectActionFab.setOnClickListener(disconnectClickListener());
    }

    // ====== ROOM LISTENER ========================================================================

    /*
     * Room events listener
     */
    private Room.Listener roomListener() {
        return new Room.Listener() {
            @Override
            public void onConnected(Room room) {
                videoStatusTextView.setText("Connected to " + room.getName());
                // TODO Add a toolbar to this view? R.Pina
//                setTitle(room.getName());

                for (Map.Entry<String, Participant> entry : room.getParticipants()
                                                                .entrySet()) {
                    addParticipant(entry.getValue());
                    break;
                }
            }

            @Override
            public void onConnectFailure(Room room, TwilioException e) {
                videoStatusTextView.setText("Failed to connect");
            }

            @Override
            public void onDisconnected(Room room, TwilioException e) {
                videoStatusTextView.setText("Disconnected from " + room.getName());
                TwilioVideoView.this.room = null;
                // Only reinitialize the UI if disconnect was not called from onDestroy()
                if (!disconnectedFromOnDestroy) {
                    setAudioFocus(false);
                    intializeUI();
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
                /*
                 * Indicates when media shared to a Room is being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
                Log.e(TAG, "onRecordingStarted");
            }

            @Override
            public void onRecordingStopped(Room room) {
                /*
                 * Indicates when media shared to a Room is no longer being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
                Log.e(TAG, "onRecordingStopped");
            }
        };
    }

    /*
     * Called when participant joins the room
     */
    private void addParticipant(Participant participant) {
        participantIdentity = participant.getIdentity();
        videoStatusTextView.setText("Participant " + participantIdentity + " joined");

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
        videoStatusTextView.setText("Participant " + participant.getIdentity() + " left.");
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
                videoStatusTextView.setText("onVideoTrackAdded");
                addParticipantVideo(videoTrack);
            }

            @Override
            public void onVideoTrackRemoved(Media media, VideoTrack videoTrack) {
                videoStatusTextView.setText("onVideoTrackRemoved");
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
}
