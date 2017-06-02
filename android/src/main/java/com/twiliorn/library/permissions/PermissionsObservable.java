package com.twiliorn.library.permissions;

import rx.Observable;
import rx.Subscriber;

public class PermissionsObservable extends Observable<PermissionsResult> {
    /**
     * Creates an Observable with a Function to execute when it is subscribed to.
     * <p/>
     * <em>Note:</em> Use {@link #create(OnSubscribe)} to create an Observable, instead of this constructor,
     * unless you specifically have a need for inheritance.
     *
     * @param f {@link OnSubscribe} to be executed when {@link #subscribe(Subscriber)} is called
     */
    protected PermissionsObservable(OnSubscribe<PermissionsResult> f) {
        super(f);
    }

    public static PermissionsObservable from(Observable<PermissionsResult> o) {
        return new PermissionsObservable(new PermissionOnSubscribe(o));
    }

    public static class PermissionOnSubscribe implements Observable.OnSubscribe<PermissionsResult> {

        private final Observable<PermissionsResult> observable;

        PermissionOnSubscribe(Observable<PermissionsResult> observable) {
            this.observable = observable;
        }

        @Override
        public void call(final Subscriber<? super PermissionsResult> subscriber) {
            Subscriber<PermissionsResult> parent = new Subscriber<PermissionsResult>() {
                @Override
                public void onCompleted() {
                    subscriber.onCompleted();
                }

                @Override
                public void onError(Throwable e) {
                    subscriber.onError(e);
                }

                @Override
                public void onNext(PermissionsResult result) {
                    subscriber.onNext(result);
                }
            };
            subscriber.add(parent);
            observable.unsafeSubscribe(parent);
        }
    }
}
