package com.example.android.sunshine.wear;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

public class WearIntentService extends IntentService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private GoogleApiClient mGoogleApiClient;

    private static final String TAG = WearIntentService.class.getSimpleName();

    private static final String WEATHER_PATH = "/sunshine-weather";

    private static final String KEY_WEATHER_ID = "weather-id";
    private static final String KEY_MAX_TEMP = "max-temp";
    private static final String KEY_MIN_TEMP = "min-temp";

    private final String[] PROJECTION = {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP
    };

    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 1;
    private static final int INDEX_MIN_TEMP = 2;
    private int mWeatherId;
    private double mMaxTemp;
    private double mMinTemp;

    public WearIntentService(final String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(@Nullable final Intent intent) {
        Uri weatherUri = WeatherContract.WeatherEntry.buildWeatherUriWithDate(System.currentTimeMillis());
        String sortOrder = WeatherContract.WeatherEntry.COLUMN_DATE + " ASC";
        Cursor cursor = getContentResolver().query(weatherUri, PROJECTION, null, null, sortOrder);

        if (cursor == null) {
            return;
        }

        mWeatherId = cursor.getInt(INDEX_WEATHER_ID);
        mMaxTemp = cursor.getDouble(INDEX_MAX_TEMP);
        mMinTemp = cursor.getDouble(INDEX_MIN_TEMP);

        cursor.close();

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            mGoogleApiClient.connect();
        }


    }

    @Override
    public void onConnected(@Nullable final Bundle bundle) {
        sendWeatherData();
    }

    @Override
    public void onConnectionSuspended(final int i) {
        Log.d(TAG, "Connection Suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull final ConnectionResult connectionResult) {
        Log.d(TAG, "Connection Failed reason:" + connectionResult.getErrorCode());
    }

    private void sendWeatherData() {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(WEATHER_PATH);
        putDataMapRequest.getDataMap().putInt(KEY_WEATHER_ID, mWeatherId);
        putDataMapRequest.getDataMap().putDouble(KEY_MAX_TEMP, mMaxTemp);
        putDataMapRequest.getDataMap().putDouble(KEY_MIN_TEMP, mMinTemp);
        PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
        Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(@NonNull final DataApi.DataItemResult dataItemResult) {
                if (dataItemResult.getStatus().isSuccess()) {
                    Log.d(TAG, "Sent");
                } else {
                    Log.d(TAG, "Sending failed");
                }
            }
        });
    }
}
