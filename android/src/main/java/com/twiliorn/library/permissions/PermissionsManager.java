package com.twiliorn.library.permissions;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.v4.BuildConfig;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;

import java.util.Arrays;
import java.util.HashMap;

import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.internal.producers.SingleDelayedProducer;
import rx.subscriptions.Subscriptions;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS;
import static android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS;

/**
 * PermissionsManager is the point of entry to finding about and requesting permissions.
 * <p/>
 * This class helper methods for the following permissions:
 * <ul>
 * <li>Camera</li>
 * <li>Microphone</li>
 * </ul>
 * <p/>
 * The method {@link #intentToAppSettings(Activity)} can be used to open the app's settings
 * to turn on a permission if the user checked "Never ask again".
 */
public class PermissionsManager {

    /**
     * Request code to use in your {@link Activity#onRequestPermissionsResult(int, String[], int[])}
     * or {@link Fragment#onRequestPermissionsResult(int, String[], int[])} to check contacts permission
     * was granted. You can also easily check in the {@link Activity#onResume()} or {@link Fragment#onResume()}
     */
    public static final int REQUEST_CAMERA_MICROPHONE_PERMISSION = 0x01;

    private final SharedPreferencesManager preferencesManager;
    private final SystemWrapper            wrapper;
    private final Scheduler                mainThreadScheduler;

    private HashMap<Integer, SingleDelayedProducer<PermissionsResult>> producersMap = new HashMap<>();

    private static PermissionsManager instance;

    public static PermissionsManager get(Context context) {
        if (instance != null) {
            return instance;
        } else {
            instance = new PermissionsManager(context);
            return instance;
        }
    }

    private PermissionsManager(Context context) {
        this.preferencesManager = new SharedPreferencesManager(context);
        this.wrapper = new SystemWrapper(context);
        this.mainThreadScheduler = AndroidSchedulers.mainThread();
    }

    // ==== CAMERA =================================================================================

    /**
     * @return if we have contact permissions
     */
    public boolean isCameraGranted() {
        return wrapper.isPermissionGranted(CAMERA);
    }

    /**
     * @return if contacts permission has been previously requested.
     */
    public boolean hasAskedForCameraPermission() {
        return preferencesManager.isCameraPermissionsAsked();
    }

    @VisibleForTesting
    public boolean shouldShowCameraRationale(@NonNull Fragment fragment) {
        return !isCameraGranted()
                && shouldShowRequestPermissionRationale(fragment,
                                                        CAMERA);
    }

    private boolean shouldShowCameraRationale(@NonNull Activity activity) {
        return !isCameraGranted()
                && shouldShowRequestPermissionRationale(activity,
                                                        CAMERA);
    }

    /**
     * If true the user has checked the "Never ask again" option. We get this by checking two things.
     * Whether we've asked before, checked with {@link #hasAskedForCameraPermission()}, which returns
     * false if the user has never seen the dialog. We also check whether we should request the permission
     * rational for the system. If these two don't match up, the user has selected "Never ask again".
     * <p/>
     * <b>Note: if we have the camera permission, and we call this method, it will return true.
     * This is intentional as we don't want to ask for permissions once we have them. If you
     * do that, you will loose the permission and dialog will come up again.</b>
     * <p/>
     * <b>Another note: if the user selected "Never ask again", then they give you permissions in
     * the app settings page, and then remove them in the same page. This method will return true.
     * Even though at that point you can ask for permissions. I have not been able to figure out a
     * way around this.</b>
     *
     * @param fragment asking for permission
     * @return whether the user has checked "Never ask again" option
     */
    public boolean neverAskForCamera(@NonNull Fragment fragment) {
        return !(hasAskedForCameraPermission()
                == shouldShowCameraRationale(fragment));
    }

    /**
     * See {@link #neverAskForCamera(Fragment)}
     *
     * @param activity to check with
     * @return if should not ask
     */
    public boolean neverAskForCamera(@NonNull Activity activity) {
        return !(hasAskedForCameraPermission()
                == shouldShowCameraRationale(activity));
    }

    // ==== MICROPHONE =============================================================================

    /**
     * @return if microphone permissions are granted.
     */
    public boolean isMicrophoneGranted() {
        return wrapper.isPermissionGranted(RECORD_AUDIO);
    }

    /**
     * @return if storage permission has been previously requested.
     */
    public boolean hasAskedForMicrophonePermission() {
        return preferencesManager.isMicrophonePermissionsAsked();
    }

    @VisibleForTesting
    protected boolean shouldShowRequestMicrophoneRationale(@NonNull Fragment fragment) {
        return !isMicrophoneGranted()
                && shouldShowRequestPermissionRationale(fragment, RECORD_AUDIO);
    }

    private boolean shouldShowRequestMicrophoneRationale(@NonNull Activity activity) {
        return !isMicrophoneGranted()
                && shouldShowRequestPermissionRationale(activity, RECORD_AUDIO);
    }

    /**
     * See {@link #neverAskForMicrophone(Fragment)}
     *
     * @param fragment to check with
     * @return if should not ask
     */
    public boolean neverAskForMicrophone(@NonNull Fragment fragment) {
        return !(hasAskedForMicrophonePermission() == shouldShowRequestMicrophoneRationale(
                fragment));
    }

    /**
     * See {@link #neverAskForMicrophone(Fragment)}
     *
     * @param activity to check with
     * @return if should not ask
     */
    public boolean neverAskForMicrophone(@NonNull Activity activity) {
        return !(hasAskedForMicrophonePermission() == shouldShowRequestMicrophoneRationale(
                activity));
    }

    // ==== PERMISSION REQUESTS ====================================================================

    /**
     * Will request the following permission if not already granted:
     * <ul>
     * <li>{@link Manifest.permission#CAMERA}</li>
     * * <li>{@link Manifest.permission#RECORD_AUDIO}</li>
     * </ul>
     * <p/>
     * Resulting permission can be checked using {@link #REQUEST_CAMERA_MICROPHONE_PERMISSION}
     * <p/>
     */
    public PermissionsObservable requestCameraAndMicrophonePermissions() {
        final Observable<PermissionsResult> o;
        if (isCameraGranted()) {
            o = Observable.just(new PermissionsResult(true, false));
        } else {
            preferencesManager.setCameraPermissionsAsked();
            preferencesManager.setMicrophonePermissionsAsked();
            o = requestPermission(CAMERA, RECORD_AUDIO);
        }
        return PermissionsObservable.from(o);
    }

    /**
     * Request a permission.
     *
     * @param permissions to request
     */
    public Observable<PermissionsResult> requestPermission(@NonNull final String... permissions) {
        assertPermissionsNotGranted(permissions);
        if (isAskingForPermissions()) {
            return Observable.empty();
        }
        return Observable.create(new Observable.OnSubscribe<PermissionsResult>() {
            @Override
            public void call(Subscriber<? super PermissionsResult> subscriber) {
                assertMainThread();
                final SingleDelayedProducer<PermissionsResult> producer = new SingleDelayedProducer<>(
                        subscriber);
                final int key = Arrays.hashCode(permissions);

                if (isAskingForPermissions()) {
                    throw new IllegalStateException(
                            "Already requesting permissions, cannot ask for permissions.");
                }

                startPermissionsActivity(permissions);

                producersMap.put(key, producer);
                // Clean up if we unsubscribe before permissions come back
                subscriber.setProducer(producer);
                subscriber.add(Subscriptions.create(new Action0() {
                    @Override
                    public void call() {
                        producersMap.remove(key);
                    }
                }));

            }
        })
                         .subscribeOn(mainThreadScheduler);

    }

    protected boolean isAskingForPermissions() {
        return PermissionRequestActivity.isAskingForPermissions();
    }

    protected void startPermissionsActivity(@NonNull String[] permissions) {
        final Context context = wrapper.getContext()
                                       .getApplicationContext();
        Intent i = PermissionRequestActivity.getIntent(context, permissions);
        context.startActivity(i);
    }

    protected void assertMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new RuntimeException(
                    "Cannot request permissions off the main thread.");
        }
    }

    // ---- REQUEST PERMISSION RATIONALE -----------------------------------------------------------

    /**
     * Wraps {@link Fragment#shouldShowRequestPermissionRationale(String)}
     *
     * @param fragment   checking for permissions
     * @param permission to check
     * @return if we should show
     */
    public boolean shouldShowRequestPermissionRationale(@NonNull Fragment fragment,
                                                        @NonNull String permission) {
        return fragment.shouldShowRequestPermissionRationale(permission);
    }

    /**
     * Wraps {@link ActivityCompat#shouldShowRequestPermissionRationale(Activity, String)}
     *
     * @param activity   checking for permissions
     * @param permission to check
     * @return if we should show
     */
    public boolean shouldShowRequestPermissionRationale(@NonNull Activity activity,
                                                        @NonNull String permission) {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
    }

    // ---- INTENT TO SETTINGS ---------------------------------------------------------------------

    /**
     * Open the app's settings page so the user could switch an activity.
     *
     * @param activity starting this intent.
     */
    public void intentToAppSettings(@NonNull Activity activity) {
        //Open the specific App Info page:
        Intent intent = new Intent(ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + activity.getPackageName()));
        if (intent.resolveActivity(activity.getPackageManager()) != null) {
            activity.startActivity(intent);
        } else {
            intent = new Intent(ACTION_MANAGE_APPLICATIONS_SETTINGS);
            if (intent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivity(intent);
            }
        }
    }

    // ---- CHECK PERMISSIONS GRANTED --------------------------------------------------------------

    /**
     * Check if permissions are granted.
     *
     * @param grantResults permissions returned in {@link Activity#onRequestPermissionsResult(int, String[], int[])}
     * @return whether all permissions were granted
     */
    public boolean arePermissionsGranted(@NonNull int[] grantResults) {
        for (int result : grantResults) {
            if (result != PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    // ---- DEBUG HELPER ---------------------------------------------------------------------------

    // If we have a permissions, and we ask again, and the user ignores it, or says no, we loose it.
    // also, even if we have a permission, and ask for it event, the system will ask it
    protected void assertPermissionsNotGranted(@NonNull String[] permissions) {
        if (BuildConfig.DEBUG) {
            for (String permission : permissions) {
                if (wrapper.isPermissionGranted(permission)) {
                    throw new AssertionError("Yo! You's need to not ask for " + permission + ". It's already been granted!");
                }
            }
        }
    }

    /* PACKAGE */ void onRequestPermissionsResult(@NonNull String[] permissions,
                                                  @NonNull int[] grantResults) {
        final int key = Arrays.hashCode(permissions);
        SingleDelayedProducer<PermissionsResult> producer = this.producersMap.get(key);
        if (producer == null) {
            return;
        }
        final boolean granted = arePermissionsGranted(grantResults);
        producer.setValue(new PermissionsResult(granted, true));
    }

    public static class SystemWrapper {

        private Context context;

        public SystemWrapper(Context context) {
            this.context = context;
        }

        public boolean isPermissionGranted(String permission) {
            return ContextCompat.checkSelfPermission(context, permission)
                    == PERMISSION_GRANTED;
        }

        public Context getContext() {
            return context;
        }
    }

}
