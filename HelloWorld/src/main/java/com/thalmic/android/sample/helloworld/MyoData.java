package com.thalmic.android.sample.helloworld;
import com.thalmic.myo.Pose;
import java.io.Serializable;

/**
 * Created by Alessandruino on 17/11/15.
 */
public class MyoData {

    private static final long serialVersionUID = 1L;

    public long timestamp;

    public double roll = 0;
    public double pitch = 0;
    public double yaw = 0;
    public Pose currentPose;

    // default constructor (values will be null until set)
    public MyoData() {
    }

    // constructor with initial values for roll/pitch/yaw
    public MyoData(long timestamp, double roll, double pitch, double yaw, Pose currentPose) {
        this.timestamp = timestamp;
        this.roll = roll;
        this.pitch = pitch;
        this.yaw = yaw;
        this.currentPose = currentPose;
    }



    public double getRoll() {

        return roll;
    }

    public double getPitch() {

        return pitch;
    }

    public double getYaw() {

        return yaw;
    }

    public Pose getPose() {

        return currentPose;
    }


    public void setRoll(double roll) {

        this.roll = roll;
    }

    public void setPitch(double pitch) {

        this.pitch = pitch;
    }

    public void setYaw(double yaw) {

        this.yaw = yaw;
    }

    @Override
    public String toString() {
        return "MyoData [roll=" + roll + ", pitch=" + pitch + ", yaw=" + yaw +", pose=" + currentPose
                + "]";
    }

}
