/*
 * Copyright (C) 2014 Thalmic Labs Inc.
 * Distributed under the Myo SDK license agreement. See LICENSE.txt for details.
 */

package com.thalmic.android.sample.helloworld;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jcodec.common.SeekableByteChannel;
import org.myrobotlab.service.data.JoystickData;
import org.myrobotlab.service.data.MyoData;

import com.thalmic.myo.AbstractDeviceListener;
import com.thalmic.myo.Arm;
import com.thalmic.myo.DeviceListener;
import com.thalmic.myo.Hub;
import com.thalmic.myo.Myo;
import com.thalmic.myo.Pose;
import com.thalmic.myo.Quaternion;
import com.thalmic.myo.XDirection;
import com.thalmic.myo.scanner.ScanActivity;

import org.jcodec.api.android.FrameGrab;

import org.myrobotlab.client.Client;



public class HelloWorldActivity extends Activity implements SensorEventListener {

    LowPassFilter filterYaw = new LowPassFilter(0.03f);

    private TextView mLockStateView;
    private TextView mTextView;
    private TextView mRoll;
    private TextView mPitch;
    private TextView mYaw;

    private SensorManager sensorManager;

    private Sensor vector;
    private Sensor acc;
    private Sensor mag;

    private float[] rMatrix = new float[9];
    private float[] rVector = new float[3];
    private float[] accVector = new float[3];
    private float[] magVector = new float[3];
    private float[] result = new float[3];
    private float[] oldResult = new float[3];
    private float[] tempRMatrix = new float[9];
    private float[] quaternion = new float[4];

    private static final int SCALE = 180;
    private double rollW;
    private double pitchW;
    private double yawW;

    boolean deltaMyo = false;
    boolean deltaOculus = false;
    boolean deltaPose = false;
    boolean deltaJoystick = false;

    private double roll;
    private double pitch;
    private double yaw;
    private String ip;
    private boolean cardboardMode;
    private boolean headTrackingMode;

    private float oldJoystickX;
    private float oldJoystickY;


    Client client;

    MyoData myodata = new MyoData();

    private long interval = 100;
    private long prevMillis = 0;
    private boolean sendPosition = false;

    // for the low-pass filter:
    static final float ALPHA = 0.1f;
    protected float[] accelVals;

    private MjpegView mv;
    private static final String TAG = "MjpegActivity";


    // Classes that inherit from AbstractDeviceListener can be used to receive events from Myo devices.
    // If you do not override an event, the default behavior is to do nothing.
    public DeviceListener mListener = new AbstractDeviceListener() {


        // onConnect() is called whenever a Myo has been connected.
        @Override
        public void onConnect(Myo myo, long timestamp) {
            // Set the text color of the text view to cyan when a Myo connects.
            mTextView.setTextColor(Color.CYAN);

        }

        // onDisconnect() is called whenever a Myo has been disconnected.
        @Override
        public void onDisconnect(Myo myo, long timestamp) {
            // Set the text color of the text view to red when a Myo disconnects.
            mTextView.setTextColor(Color.RED);
        }

        // onArmSync() is called whenever Myo has recognized a Sync Gesture after someone has put it on their
        // arm. This lets Myo know which arm it's on and which way it's facing.
        @Override
        public void onArmSync(Myo myo, long timestamp, Arm arm, XDirection xDirection) {
            mTextView.setText(myo.getArm() == Arm.LEFT ? R.string.arm_left : R.string.arm_right);
        }

        // onArmUnsync() is called whenever Myo has detected that it was moved from a stable position on a person's arm after
        // it recognized the arm. Typically this happens when someone takes Myo off of their arm, but it can also happen
        // when Myo is moved around on the arm.
        @Override
        public void onArmUnsync(Myo myo, long timestamp) {
            mTextView.setText(R.string.hello_world);
        }

        // onUnlock() is called whenever a synced Myo has been unlocked. Under the standard locking
        // policy, that means poses will now be delivered to the listener.
        @Override
        public void onUnlock(Myo myo, long timestamp) {
            mLockStateView.setText(R.string.unlocked);
        }

        // onLock() is called whenever a synced Myo has been locked. Under the standard locking
        // policy, that means poses will no longer be delivered to the listener.
        @Override
        public void onLock(Myo myo, long timestamp) {
            mLockStateView.setText(R.string.locked);
        }

        // onOrientationData() is called whenever a Myo provides its current orientation,
        // represented as a quaternion.
        @Override
        public void onOrientationData(Myo myo, long timestamp, Quaternion rotation) {
            // Calculate Euler angles (roll, pitch, and yaw) from the quaternion.
            float roll = (float) Math.toDegrees(Quaternion.roll(rotation));
            float pitch = (float) Math.toDegrees(Quaternion.pitch(rotation));
            float yaw = (float) Math.toDegrees(Quaternion.yaw(rotation));

            // Adjust roll and pitch for the orientation of the Myo on the arm.
            if (myo.getXDirection() == XDirection.TOWARD_ELBOW) {
                roll *= -1;
                pitch *= -1;
            }

            deltaMyo = ((Math.abs(myodata.roll - roll) >=  1) || (  Math.abs(myodata.pitch - pitch) >= 1) || ( Math.abs(myodata.yaw - yaw) >= 1));

            if (deltaMyo) {
                // Next, we apply a rotation to the text view using the roll, pitch, and yaw.
                mTextView.setRotation(roll);
                mTextView.setRotationX(pitch);
                mTextView.setRotationY(yaw);
                mRoll.setText("roll:" + Float.toString(roll));
                mPitch.setText("pitch:" + Float.toString(pitch));
                mYaw.setText("yaw:" + Float.toString(yaw));
                myodata.roll = roll;
                myodata.yaw = yaw;
                myodata.pitch = pitch;
                myodata.timestamp = timestamp;
                if (client != null ) {try {

                    //client.send("servo01", "moveTo",(roll+90.0));
                    client.send("myo", "publishMyoData", myodata);
                } catch (IOException e) {
                    e.printStackTrace();
                }}
            }
        }

        // onPose() is called whenever a Myo provides a new pose.
        @Override
        public void onPose(Myo myo, long timestamp, Pose pose) {
            // Handle the cases of the Pose enumeration, and change the text of the text view
            // based on the pose we receive.
            if (pose.toString() != myodata.currentPose){
                deltaPose = true;
            }
            myodata.currentPose = pose.toString();
            if (client != null & deltaPose) {try {

                //client.send("servo01", "moveTo",(roll+90.0));
                client.send("myo", "publishMyoData", myodata);
                deltaPose = false;
            } catch (IOException e) {
                e.printStackTrace();
            }}
            switch (pose) {
                case UNKNOWN:
                    mTextView.setText(getString(R.string.hello_world));
                    break;
                case REST:
                case DOUBLE_TAP:
                    int restTextId = R.string.hello_world;
                    switch (myo.getArm()) {
                        case LEFT:
                            restTextId = R.string.arm_left;
                            break;
                        case RIGHT:
                            restTextId = R.string.arm_right;
                            break;
                    }
                    mTextView.setText(getString(restTextId));
                    break;
                case FIST:
                    mTextView.setText(getString(R.string.pose_fist));
                    break;
                case WAVE_IN:
                    mTextView.setText(getString(R.string.pose_wavein));
                    break;
                case WAVE_OUT:
                    mTextView.setText(getString(R.string.pose_waveout));
                    break;
                case FINGERS_SPREAD:
                    mTextView.setText(getString(R.string.pose_fingersspread));
                    break;
            }

            if (pose != Pose.UNKNOWN && pose != Pose.REST) {
                // Tell the Myo to stay unlocked until told otherwise. We do that here so you can
                // hold the poses without the Myo becoming locked.
                myo.unlock(Myo.UnlockType.HOLD);

                // Notify the Myo that the pose has resulted in an action, in this case changing
                // the text on the screen. The Myo will vibrate.
                myo.notifyUserAction();
            } else {
                // Tell the Myo to stay unlocked only for a short period. This allows the Myo to
                // stay unlocked while poses are being performed, but lock after inactivity.
                myo.unlock(Myo.UnlockType.HOLD);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hello_world);

        sensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        vector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        mLockStateView = (TextView) findViewById(R.id.lock_state);
        mTextView = (TextView) findViewById(R.id.text);
        mRoll = (TextView) findViewById(R.id.roll);
        mPitch = (TextView) findViewById(R.id.pitch);
        mYaw = (TextView) findViewById(R.id.yaw);

        EditText videoip = (EditText) findViewById(R.id.videoip);
        videoip.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    ip = v.getText().toString();
                    startVideo();
                handled = true;
                 }

            return handled;
                }
            });




        EditText connect = (EditText) findViewById(R.id.connect);
        connect.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_SEND) {

                    try {
                        String address = v.getText().toString();
                        client = new Client(("tcp://" + address + ":6767"), "client");

                        try {
                            client.send("runtime", "getUptime");
                            //client.send("runtime", "start", "arduino", "Arduino");
                            //client.send("runtime", "start", "servo01", "Servo");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                    }
                    handled = true;
                }
                return handled;
            }
        });

        EditText comPort = (EditText) findViewById(R.id.comport);
        comPort.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    String address = v.getText().toString();
                    if (client != null) {
                        try {
                            client.send("arduino", "connect", address);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    handled = true;
                }
                return handled;
            }
        });

        final Button calibrate = (Button) findViewById(R.id.calibrate);
        calibrate.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //
                // Perform action on click
                if (client != null) {
                    try {
                        client.send("oculus", "calibrate");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }
        });

        ToggleButton toggleCardboard = (ToggleButton) findViewById(R.id.cardboard);
        toggleCardboard.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    cardboardMode = true;
                } else {
                    cardboardMode = false;
                }
            }
        });

        ToggleButton toggleHeadTracking = (ToggleButton) findViewById(R.id.headTrackingButton);
        toggleHeadTracking.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    headTrackingMode = true;
                } else {
                    headTrackingMode = false;
                }
            }
        });


        // First, we initialize the Hub singleton with an application identifier.
        Hub hub = Hub.getInstance();
        if (!hub.init(this, getPackageName())) {
            // We can't do anything with the Myo device if the Hub can't be initialized, so exit.
            Toast.makeText(this, "Couldn't initialize Hub", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        // Next, register for DeviceListener callbacks.
        hub.addListener(mListener);
        //hub.attachToAdjacentMyo();
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // We don't want any callbacks when the Activity is gone, so unregister the listener.
        Hub.getInstance().removeListener(mListener);

        if (isFinishing()) {
            // The Activity is finishing, so shutdown the Hub. This will disconnect from the Myo.
            Hub.getInstance().shutdown();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (R.id.action_scan == id) {
            onScanActionSelected();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void onResume() {
        super.onResume();
        // Register a listener for the sensor.
        sensorManager.registerListener(this, vector, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_GAME);
    }

    public final void onSensorChanged(SensorEvent event) {
        // get reading from the sensor
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy(event.values, 0, accVector, 0, 3);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy(event.values, 0, magVector, 0, 3);
                break;
            default:
                return;
        }
        rVector[0] = event.values[0];
        rVector[1] = event.values[1];
        rVector[2] = event.values[2];
        calculateAngles(result, rVector, accVector, magVector);
        result[0] = Math.round(filterYaw.lowPass(result[0]));
        deltaOculus = ((Math.abs(oldResult[0] - result[0]) >  1) || (  Math.abs(oldResult[1] - result[1]) > 1) || ( Math.abs(oldResult[2] - result[2]) > 1));
        if (deltaOculus){
        TextView textView = (TextView) findViewById(R.id.accData);
        textView.setText("Euler Angles are \nyaw: " + result[0] + " °\n" +
                "roll: " + result[1] + " °\n" +
                "pitch: " + result[2] + " °\n");
            oldResult[0] = result[0];
            oldResult[1] = result[1];
            oldResult[2] = result[2];
            if (headTrackingMode){

            if (client != null) {
            try {

                //client.send("servo01", "moveTo",(roll+90.0));
                client.send("oculus", "computeAnglesAndroid", result[0], result[1], result[2]);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }}}
    }

    public void calculateAngles(float[] result, float[] rVector, float[] accVector, float[] magVector) {
        //caculate temp rotation matrix from rotation vector first
        SensorManager.getRotationMatrix(rMatrix, null, accVector, magVector);
        SensorManager.getQuaternionFromVector(quaternion, rVector);

        roll = Math.atan2(2.0f * (quaternion[0] * quaternion[1] + quaternion[2] * quaternion[3]), 1.0f - 2.0f * (quaternion[1] * quaternion[1] + quaternion[2] * quaternion[2]));
        pitch = Math.asin(2.0f * (quaternion[0] * quaternion[2] - quaternion[3] * quaternion[1]));
        yaw = Math.atan2(2.0f * (quaternion[0] * quaternion[3] + quaternion[1] * quaternion[2]), 1.0f - 2.0f * (quaternion[2] * quaternion[2] + quaternion[3] * quaternion[3]));

        rollW = ((roll + Math.PI) / (Math.PI * 2.0) * SCALE);
        pitchW = ((pitch + Math.PI / 2.0) / Math.PI * SCALE);
        yawW = ((yaw + Math.PI) / (Math.PI * 2.0) * SCALE);

        //calculate Euler angles now
        SensorManager.getOrientation(rMatrix, result);

        //Now we can convert it to degrees
        convertToDegrees(result);
    }

    private void convertToDegrees(float[] vector) {
        for (int i = 0; i < vector.length; i++) {
            vector[i] = Math.round(Math.toDegrees(vector[i]));
        }
    }

    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // to do something
    }

    @Override
    protected void onPause() {
        super.onPause();
    }


    private void onScanActionSelected() {
        // Launch the ScanActivity to scan for Myos to connect to.
        Intent intent = new Intent(this, ScanActivity.class);
        startActivity(intent);
    }

    public void startVideo() {
            Intent intent = new Intent(this, SensorSender.class);
            intent.putExtra("udpIp", ip);
            intent.putExtra("cardboardMode",cardboardMode);
            startActivity(intent);
        }

    public boolean onGenericMotionEvent(MotionEvent event) {

        // Check that the event came from a game controller
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) ==
                InputDevice.SOURCE_JOYSTICK &&
                event.getAction() == MotionEvent.ACTION_MOVE) {

            // Process all historical movement samples in the batch
            final int historySize = event.getHistorySize();

            // Process the movements starting from the
            // earliest historical position in the batch
            for (int i = 0; i < historySize; i++) {
                // Process the event at historical position i
                processJoystickInput(event, i);
            }

            // Process the current movement sample in the batch (position -1)
            processJoystickInput(event, -1);
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    private void processJoystickInput(MotionEvent event, int historyPos) {

        InputDevice mInputDevice = event.getDevice();

        // Calculate the horizontal distance to move by
        // using the input value from one of these physical controls:
        // the left control stick, hat axis, or the right control stick.
        float x = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_X, historyPos);
        if (x == 0) {
            x = getCenteredAxis(event, mInputDevice,
                    MotionEvent.AXIS_HAT_X, historyPos);
        }
        if (x == 0) {
            x = getCenteredAxis(event, mInputDevice,
                    MotionEvent.AXIS_Z, historyPos);
        }

        // Calculate the vertical distance to move by
        // using the input value from one of these physical controls:
        // the left control stick, hat switch, or the right control stick.
        float y = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_Y, historyPos);
        if (y == 0) {
            y = getCenteredAxis(event, mInputDevice,
                    MotionEvent.AXIS_HAT_Y, historyPos);
        }
        if (y == 0) {
            y = getCenteredAxis(event, mInputDevice,
                    MotionEvent.AXIS_RZ, historyPos);
        }

        deltaJoystick = ((Math.abs(oldJoystickX - x) >=  0.1) || (  Math.abs(oldJoystickY - y) >= 0.1));

        JoystickData joystickDataX = new JoystickData("x",x);
        JoystickData joystickDataY = new JoystickData("y",y);

        if (deltaJoystick){

            mPitch.setText("y is :" + y);
            oldJoystickX = x;
            oldJoystickY = y;

        if (client != null ) {try {

            //client.send("servo01", "moveTo",(roll+90.0));
            client.send("joystick", "publishJoystickInput", joystickDataX);
            client.send("joystick", "publishJoystickInput", joystickDataY);
        } catch (IOException e) {
            e.printStackTrace();
        }}}


    }

    private static float getCenteredAxis(MotionEvent event, InputDevice device, int axis, int historyPos) {
        final InputDevice.MotionRange range =
                device.getMotionRange(axis, event.getSource());

        // A joystick at rest does not always report an absolute position of
        // (0,0). Use the getFlat() method to determine the range of values
        // bounding the joystick axis center.
        if (range != null) {
            final float flat = range.getFlat();
            final float value =
                    historyPos < 0 ? event.getAxisValue(axis):
                            event.getHistoricalAxisValue(axis, historyPos);

            // Ignore axis values that are within the 'flat' region of the
            // joystick axis center.
            if (Math.abs(value) > flat) {
                return value;
            }
        }
        return 0;
    }
}


