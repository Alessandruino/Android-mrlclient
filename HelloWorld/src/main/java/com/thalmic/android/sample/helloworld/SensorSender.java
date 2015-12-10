package com.thalmic.android.sample.helloworld;

import java.io.IOException;
import java.net.URI;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;

public class SensorSender extends Activity {
    private long interval = 100;
    private long prevMillis = 0;
    private boolean sendPosition = false;

    // for the low-pass filter:
    static final float ALPHA = 0.1f;
    protected float[] accelVals;

    private String udpIp;
    private boolean cardboardMode;

    private MjpegView mv;
    private DoubleMjpegView mv2;
    private static final String TAG = "MjpegActivity";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        // We're going full screen:
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        super.onCreate(savedInstanceState);

        Intent iin = getIntent();
        Bundle extras = iin.getExtras();
        udpIp = extras.getString("udpIp");
        cardboardMode = extras.getBoolean("cardboardMode");

        // Here we'll form the MJPG stream URL:
        String URL = "http://" + udpIp + ":8080/?action=stream";

        // Now show the MJPG stream:
        if (cardboardMode == true) {
            mv2 = new DoubleMjpegView(this);
            setContentView(mv2);
            new DoRead().execute(URL);
            }
        else {
        mv = new MjpegView(this);
        setContentView(mv);
        new DoRead().execute(URL);}
        }

    @Override
    protected void onPause() {
        // unregister listener
        super.onPause();
        if (cardboardMode == true) {
            mv2.stopPlayback();
        }
        else {
            mv.stopPlayback();
    }

    }

    protected void onResume(){
        super.onResume();
        if (cardboardMode == true) {
            mv2.stopPlayback();
        }
        else {
            mv.stopPlayback();
        }
    }

    public class DoRead extends AsyncTask<String, Void, MjpegInputStream> {
        protected MjpegInputStream doInBackground(String... url) {
            // if camera has authentication deal with it and don't just
            // not work
            HttpResponse res = null;
            DefaultHttpClient httpclient = new DefaultHttpClient();
            Log.d(TAG, "1. Sending http request");
            try {
                res = httpclient.execute(new HttpGet(URI.create(url[0])));
                Log.d(TAG, "2. Request finished, status = "
                        + res.getStatusLine().getStatusCode());
                if (res.getStatusLine().getStatusCode() == 401) {
                    // You must turn off camera User Access Control before this
                    // will work
                    return null;
                }
                return new MjpegInputStream(res.getEntity().getContent());
            } catch (ClientProtocolException e) {
                e.printStackTrace();
                Log.d(TAG, "Request failed-ClientProtocolException", e);
                // Error connecting to camera
            } catch (IOException e) {

                e.printStackTrace();
                Log.d(TAG, "Request failed-IOException", e);
                // Error connecting to camera
            }

            return null;
        }

        protected void onPostExecute(MjpegInputStream result) {
            if (cardboardMode == true) {
                mv2.setSource(result);
                mv2.setDisplayMode(DoubleMjpegView.SIZE_BEST_FIT);
                mv2.showFps(false);
            }
            else {
            mv.setSource(result);
            mv.setDisplayMode(MjpegView.SIZE_FULLSCREEN);
            mv.showFps(false);}
        }
    }
}