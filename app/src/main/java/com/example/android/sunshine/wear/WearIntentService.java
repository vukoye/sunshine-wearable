package com.example.android.sunshine.wear;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.data.WeatherContract;
import com.example.android.sunshine.utilities.SunshineWeatherUtils;
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
    private String mHighTemp;
    private String mLowTemp;

    public WearIntentService(final String name) {
        super(name);
    }

    public WearIntentService() {
        super("WearIntentService");
    }


    @Override
    protected void onHandleIntent(@Nullable final Intent intent) {
        String sortOrder = WeatherContract.WeatherEntry.COLUMN_DATE + " ASC";
        String selection = WeatherContract.WeatherEntry.getSqlSelectForTodayOnwards();
        Cursor cursor = getContentResolver().query(WeatherContract.WeatherEntry.CONTENT_URI,
                PROJECTION, selection, null, sortOrder);

        if (cursor == null || cursor.getCount() == 0) {
            return;
        }
        cursor.moveToFirst();
        mWeatherId = cursor.getInt(INDEX_WEATHER_ID);
        double maxTemp = cursor.getDouble(INDEX_MAX_TEMP);
        double mintTemp = cursor.getDouble(INDEX_MIN_TEMP);
        mHighTemp = SunshineWeatherUtils.formatTemperature(getApplicationContext(), maxTemp);
        mLowTemp = SunshineWeatherUtils.formatTemperature(getApplicationContext(), mintTemp);
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
        putDataMapRequest.getDataMap().putString(KEY_MAX_TEMP, mHighTemp);
        putDataMapRequest.getDataMap().putString(KEY_MIN_TEMP, mLowTemp);
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
