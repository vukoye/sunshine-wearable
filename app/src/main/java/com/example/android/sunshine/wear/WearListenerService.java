package com.example.android.sunshine.wear;

import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class WearListenerService extends WearableListenerService {

    private final String TAG = WearListenerService.class.getSimpleName();

    private final String SUNSHINE_WEATHER_PATH = "/sunshine-weather";

    @Override
    public void onMessageReceived(final MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        Log.d(TAG, "onMessageReceived: ");
        if (messageEvent.getPath().equals(SUNSHINE_WEATHER_PATH)) {
            //start intent to downloads data
        }
    }
}
