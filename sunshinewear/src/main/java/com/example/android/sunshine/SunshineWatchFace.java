/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {

    private static final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final Typeface BOLD_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    private static final String TAG = SunshineWatchFace.class.getSimpleName();
    private int mWeatherId;
    private String mHighTemp = "X";
    private String mLowTemp = "Y";

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {

        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient
            .OnConnectionFailedListener {

        private static final String WEATHER_PATH = "/sunshine-weather";

        private static final String KEY_WEATHER_ID = "weather-id";
        private static final String KEY_MAX_TEMP = "max-temp";
        private static final String KEY_MIN_TEMP = "min-temp";

        GoogleApiClient mGoogleApiClient;

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mBackgroundPaintAmbient;
        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mDatePaint;
        Paint mMaxTempPaint;
        Paint mMinTempPaint;
        Paint mWeatherPaint;
        Paint mLinebreak;
        Bitmap mWeatherBitmap;

        SimpleDateFormat mDayOfWeekFormat;

        boolean mAmbient;
        Calendar mCalendar;
        SimpleDateFormat mDateFormat;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mXOffset;
        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        private float mLineHeight;
        private float mLineWidth;
        private float mTextPadding;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this).setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                                                                                .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                                                                                .setShowSystemUiTime(false)
                                                                                .setAcceptsTapEvents(true)
                                                                                .build());

            mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this).addApi(Wearable.API)
                                                                                  .addConnectionCallbacks(this)
                                                                                  .addOnConnectionFailedListener(this)
                                                                                  .build();

            Resources resources = SunshineWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mLineWidth = resources.getDimension(R.dimen.separator_line_width);
            mTextPadding = resources.getDimension(R.dimen.text_padding);
            int color_text = resources.getColor(R.color.text_white);
            int color_text_blue = resources.getColor(R.color.text_blue);

            mHourPaint = createTextPaint(color_text, BOLD_TYPEFACE);
            mMinutePaint = createTextPaint(color_text, NORMAL_TYPEFACE);
            mDatePaint = createTextPaint(color_text_blue, NORMAL_TYPEFACE);
            mMaxTempPaint = createTextPaint(color_text, BOLD_TYPEFACE);
            mMaxTempPaint.setTextAlign(Paint.Align.CENTER);
            mMinTempPaint = createTextPaint(color_text_blue, NORMAL_TYPEFACE);
            mMinTempPaint.setTextAlign(Paint.Align.CENTER);
            mWeatherPaint = new Paint();
            mWeatherPaint.setTextAlign(Paint.Align.CENTER);
            mLinebreak = new Paint();
            mLinebreak.setColor(color_text);

            mCalendar = Calendar.getInstance();
            mDateFormat = new SimpleDateFormat("EEE, MMM d yyyy", Locale.getDefault());
            mDateFormat.setCalendar(mCalendar);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                registerGoogleApiClient();
                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
                unregisterGoogleApiClient();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private void registerGoogleApiClient() {
            if (mGoogleApiClient != null) {
                mGoogleApiClient.connect();
            }
        }

        private void unregisterGoogleApiClient() {
            if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                Wearable.DataApi.removeListener(mGoogleApiClient, this);
                mGoogleApiClient.disconnect();
            }
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            mLineHeight = resources.getDimension(isRound ? R.dimen.line_height_round : R.dimen.line_height);

            float bigTextSize = resources.getDimension(isRound ? R.dimen.big_text_size_round : R.dimen.big_text_size);
            float smallTextSize = resources.getDimension(isRound ? R.dimen.small_text_size_round : R.dimen.small_text_size);

            mHourPaint.setTextSize(bigTextSize);
            mMinutePaint.setTextSize(bigTextSize);
            mDatePaint.setTextSize(smallTextSize);
            mMaxTempPaint.setTextSize(bigTextSize);
            mMinTempPaint.setTextSize(bigTextSize);

        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mHourPaint.setAntiAlias(!inAmbientMode);
                    mMinutePaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                    mMaxTempPaint.setAntiAlias(!inAmbientMode);
                    mMinTempPaint.setAntiAlias(!inAmbientMode);
                    mBackgroundPaint.setColor(getColor(R.color.background_ambient));
                    mWeatherPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (mAmbient) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            String hourString = String.format("%d:", mCalendar.get(Calendar.HOUR));
            String minutesString = mAmbient ?
                                   String.format("%02d", mCalendar.get(Calendar.MINUTE)) :
                                   String.format("%02d:%02d", mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND));

            String dateString = mDateFormat.format(mCalendar.getTime()).toUpperCase();

            //canvas.drawText(text, mXOffset, mYOffset, mTextPaint);

            float xHours = bounds.centerX() - (mHourPaint.measureText(hourString) + mMinutePaint.measureText(minutesString)) / 2;
            canvas.drawText(hourString, xHours, mYOffset, mHourPaint);

            float xMinutes = xHours + mHourPaint.measureText(hourString);
            canvas.drawText(minutesString, xMinutes, mYOffset, mMinutePaint);
            if (mAmbient) {
                float xDate = bounds.centerX() - mDatePaint.measureText(dateString) / 2;
                canvas.drawText(dateString, xDate, mYOffset + mLineHeight * 2f, mDatePaint);

                canvas.drawLine(bounds.centerX() - mLineWidth / 2, mYOffset + mLineHeight * 3f, bounds.centerX() + mLineWidth / 2, mYOffset + mLineHeight *
                        3f + 1, mLinebreak);

                canvas.drawText(mHighTemp, bounds.centerX(), mYOffset + mLineHeight * 7, mMaxTempPaint);
                float xMinTemp = bounds.centerX() + mMaxTempPaint.measureText(mHighTemp);
                canvas.drawText(mLowTemp, xMinTemp, mYOffset + mLineHeight * 7, mMinTempPaint);

                if (mWeatherBitmap != null) {
                    float xWeather = bounds.centerX() / 4;
                    canvas.drawBitmap(mWeatherBitmap, xWeather, mYOffset + mLineHeight * 7 - mWeatherBitmap.getHeight() / 2, mWeatherPaint);
                }
            }

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onDataChanged(final DataEventBuffer dataBuffer) {
            for (DataEvent event : dataBuffer) {
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    if (event.getDataItem().getUri().getPath().equals(WEATHER_PATH)) {
                        DataMap dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                        mWeatherId = dataMap.getInt(KEY_WEATHER_ID);
                        mHighTemp = dataMap.getString(KEY_MAX_TEMP);
                        mLowTemp = dataMap.getString(KEY_MIN_TEMP);
                        Log.d(TAG, "Weather Data Received id: " + mWeatherId + " HighTemp: " + mHighTemp + " LowTemp: " + mLowTemp);
                        Resources resources = SunshineWatchFace.this.getResources();
                        mWeatherBitmap = BitmapFactory.decodeResource(resources, getSmallArtResourceIdForWeatherCondition(mWeatherId));
                        invalidate();
                    }
                }
            }
        }

        @Override
        public void onConnected(@Nullable final Bundle bundle) {
            Log.d(TAG, "OnConnected");
            Wearable.DataApi.addListener(mGoogleApiClient, this);
            requestWeatherData();
        }

        @Override
        public void onConnectionSuspended(final int i) {
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
        }

        @Override
        public void onConnectionFailed(@NonNull final ConnectionResult connectionResult) {

        }

        public void requestWeatherData() {
            Wearable.MessageApi.sendMessage(mGoogleApiClient, "", WEATHER_PATH, null).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                @Override
                public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                    Log.e(TAG, "requestWeatherData:" + sendMessageResult.getStatus());
                }
            });
        }
    }

    public static int getSmallArtResourceIdForWeatherCondition(int weatherId) {

        /*
         * Based on weather code data for Open Weather Map.
         */
        if (weatherId >= 200 && weatherId <= 232) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 300 && weatherId <= 321) {
            return R.drawable.ic_light_rain;
        } else if (weatherId >= 500 && weatherId <= 504) {
            return R.drawable.ic_rain;
        } else if (weatherId == 511) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 520 && weatherId <= 531) {
            return R.drawable.ic_rain;
        } else if (weatherId >= 600 && weatherId <= 622) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 701 && weatherId <= 761) {
            return R.drawable.ic_fog;
        } else if (weatherId == 761 || weatherId == 771 || weatherId == 781) {
            return R.drawable.ic_storm;
        } else if (weatherId == 800) {
            return R.drawable.ic_clear;
        } else if (weatherId == 801) {
            return R.drawable.ic_light_clouds;
        } else if (weatherId >= 802 && weatherId <= 804) {
            return R.drawable.ic_cloudy;
        } else if (weatherId >= 900 && weatherId <= 906) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 958 && weatherId <= 962) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 951 && weatherId <= 957) {
            return R.drawable.ic_clear;
        }

        Log.e(TAG, "Unknown Weather: " + weatherId);
        return R.drawable.ic_storm;
    }
}
