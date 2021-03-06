
package com.android.systemui.eos;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;

import java.util.ArrayList;

public class EosObserver {
    private static final String TAG = "EosObserverHandler";

    // a good starting point that won't interfere anywhere
    private static final int MESSAGE_START_VALUE = 4000;

    // let handler know we need need to update listener list
    private static final int MESSAGE_UPDATE_LISTENERS = 10000;

    private ArrayList<FeatureListener> mListeners = new ArrayList<FeatureListener>();
    private ArrayList<FeatureListener> mTempListeners = new ArrayList<FeatureListener>();

    private int mNextMessage = MESSAGE_START_VALUE;
    private ContentResolver mResolver;
    private UriObserver mObserver;
    private EosFeatureHandler mHandler;

    public interface FeatureListener {
        public ArrayList<String> onRegisterClass();

        public void onSetMessage(String uri, int msg);

        public void onFeatureStateChanged(int msg);
    }

    public EosObserver(Context context) {
        mResolver = context.getContentResolver();
        mHandler = new EosFeatureHandler();
        mObserver = new UriObserver(mHandler);
    }

    /* when we want to register an entire class */
    public void setOnFeatureChangedListener(FeatureListener listener) {
        if (listener == null)
            return;
        ArrayList<String> uris = listener.onRegisterClass();
        if (uris == null)
            return;
        for (String uri : uris) {
            int msg = getNextMessage();
            mObserver.setObservable(uri, msg);
            listener.onSetMessage(uri, msg);
        }
        mTempListeners.add(listener);
        mHandler.sendEmptyMessage(MESSAGE_UPDATE_LISTENERS);
    }

    private int getNextMessage() {
        int oldMessage = mNextMessage;
        mNextMessage++;
        return oldMessage;
    }

    /*
     * pass the message integer as it is unique otherwise we would delete all
     * observers watching a uri
     */
    public boolean unregisterUri(int msg) {
        return mObserver.deleteObservable(msg);
    }

    public void setEnabled(boolean enabled) {
        if (enabled) {
            mObserver.startListening();
        } else {
            mObserver.stopListening();
        }
    }

    private class EosFeatureHandler extends Handler {
        public void handleMessage(Message m) {
            super.handleMessage(m);
            if (m.what == MESSAGE_UPDATE_LISTENERS) {
                mListeners = (ArrayList<FeatureListener>) mTempListeners
                        .clone();
                return;
            } else {
                for (FeatureListener listener : mListeners) {
                    listener.onFeatureStateChanged(m.what);
                }
            }
        }
    }

    private class UriObserver extends ContentObserver {
        private static final boolean STATE_ON = true;
        private static final boolean STATE_OFF = false;

        private Handler handler;
        private ArrayList<HandlerObject> observers = new ArrayList<HandlerObject>();
        private boolean isObserverOn = false;

        public UriObserver(Handler handler) {
            super(handler);
            this.handler = handler;
        }

        public void setObservable(String uri, int message) {
            HandlerObject object = new HandlerObject(uri, message);
            observers.add(object);

            /*
             * if we are already listening, i.e. ECC is up the onChange has
             * already iterated and won't iterate again until listening state
             * changes so if a request comes in during that state we have to
             * maunally register
             */
            if (isObserverOn) {
                registerObserver(object);
                log("message - " + String.valueOf(object.message)
                        + " with uri " + object.uri.toString() + " registered");
            }
        }

        public boolean deleteObservable(int msg) {
            for (HandlerObject object : observers) {
                if (object.message == msg) {
                    if (observers.contains(object)) {
                        observers.remove(observers.indexOf(object));
                        return true;
                    }
                }
            }
            return false;
        }

        public void startListening() {
            if (!isObserverOn) {
                for (HandlerObject object : observers) {
                    registerObserver(object);
                    log(object.uri.toString() + " registered");
                }
                isObserverOn = STATE_ON;
            }
        }

        public void stopListening() {
            if (isObserverOn) {
                mResolver.unregisterContentObserver(UriObserver.this);
                log("CfxObserverHandler unregistered");
                isObserverOn = STATE_OFF;
            }
        }

        public void onChange(boolean selfChange, Uri uri) {
            for (HandlerObject object : observers) {
                if (object.uri.equals(uri)) {
                    handler.sendEmptyMessage(object.message);
                }
            }
        }

        private void registerObserver(HandlerObject obj) {
            mResolver.registerContentObserver(obj.uri, false, UriObserver.this);
        }
    }

    private class HandlerObject {
        public Uri uri;
        public int message;

        public HandlerObject(String uri, int message) {
            this.uri = Settings.System.getUriFor(uri);
            this.message = message;
        }
    }

    static void log(String s) {
        Log.i(TAG, s);
    }
}
