package com.ece420.lab7;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect2d;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.tracking.Tracker;
import org.opencv.tracking.TrackerKCF;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "MainActivity";

    // UI Variables
    private Button controlButton;
    private SeekBar colorSeekbar;
    private SeekBar widthSeekbar;
    private SeekBar heightSeekbar;
    private TextView widthTextview;
    private TextView heightTextview;

    // Declare OpenCV based camera view base
    private CameraBridgeViewBase mOpenCvCameraView;
    // Camera size
    private int myWidth;
    private int myHeight;

    // Mat to store RGBA and Grayscale camera preview frame
    private Mat mRgba;
    private Mat mGray;

    // KCF Tracker variables
    private TrackerKCF myTacker;
    private Rect2d myROI = new Rect2d(0,0,0,0);
    private int myROIWidth = 70;
    private int myROIHeight = 70;
    private Scalar myROIColor = new Scalar(0,0,0);
    private int tracking_flag = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        super.setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        // Request User Permission on Camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, 1);}

        // OpenCV Loader and Avoid using OpenCV Manager
        if (!OpenCVLoader.initDebug()) {
            Log.e(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), not working.");
        } else {
            Log.d(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), working.");
        }

        // Setup color seek bar
        colorSeekbar = (SeekBar) findViewById(R.id.colorSeekBar);
        colorSeekbar.setProgress(50);
        setColor(50);
        colorSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                setColor(progress);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Setup width seek bar
        widthTextview = (TextView) findViewById(R.id.widthTextView);
        widthSeekbar = (SeekBar) findViewById(R.id.widthSeekBar);
        widthSeekbar.setProgress(myROIWidth - 20);
        widthSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                // Only allow modification when not tracking
                if(tracking_flag == -1) {
                    myROIWidth = progress + 20;
                }
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Setup width seek bar
        heightTextview = (TextView) findViewById(R.id.heightTextView);
        heightSeekbar = (SeekBar) findViewById(R.id.heightSeekBar);
        heightSeekbar.setProgress(myROIHeight - 20);
        heightSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                // Only allow modification when not tracking
                if(tracking_flag == -1) {
                    myROIHeight = progress + 20;
                }
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Setup control button
        controlButton = (Button)findViewById((R.id.controlButton));
        controlButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tracking_flag == -1) {
                    // Modify UI
                    controlButton.setText("STOP");
                    widthTextview.setVisibility(View.INVISIBLE);
                    widthSeekbar.setVisibility(View.INVISIBLE);
                    heightTextview.setVisibility(View.INVISIBLE);
                    heightSeekbar.setVisibility(View.INVISIBLE);
                    // Modify tracking flag
                    tracking_flag = 0;
                }
                else if(tracking_flag == 1){
                    // Modify UI
                    controlButton.setText("START");
                    widthTextview.setVisibility(View.VISIBLE);
                    widthSeekbar.setVisibility(View.VISIBLE);
                    heightTextview.setVisibility(View.VISIBLE);
                    heightSeekbar.setVisibility(View.VISIBLE);
                    // Tear down myTracker
                    myTacker.clear();
                    // Modify tracking flag
                    tracking_flag = -1;
                }
            }
        });

        // Setup OpenCV Camera View
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.opencv_camera_preview);
        // Use main camera with 0 or front camera with 1
        mOpenCvCameraView.setCameraIndex(0);
        // Force camera resolution, ignored since OpenCV automatically select best ones
        // mOpenCvCameraView.setMaxFrameSize(1280, 720);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    // Helper Function to map single integer to color scalar
    // https://www.particleincell.com/2014/colormap/
    public void setColor(int value) {
        double a=(1-(double)value/100)/0.2;
        int X=(int)Math.floor(a);
        int Y=(int)Math.floor(255*(a-X));
        double newColor[] = {0,0,0};
        switch(X)
        {
            case 0:
                // r=255;g=Y;b=0;
                newColor[0] = 255;
                newColor[1] = Y;
                break;
            case 1:
                // r=255-Y;g=255;b=0
                newColor[0] = 255-Y;
                newColor[1] = 255;
                break;
            case 2:
                // r=0;g=255;b=Y
                newColor[1] = 255;
                newColor[2] = Y;
                break;
            case 3:
                // r=0;g=255-Y;b=255
                newColor[1] = 255-Y;
                newColor[2] = 255;
                break;
            case 4:
                // r=Y;g=0;b=255
                newColor[0] = Y;
                newColor[2] = 255;
                break;
            case 5:
                // r=255;g=0;b=255
                newColor[0] = 255;
                newColor[2] = 255;
                break;
        }
        myROIColor.set(newColor);
        return;
    }

    // OpenCV Camera Functionality Code
    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC1);
        myWidth = width;
        myHeight = height;
        myROI = new Rect2d(myWidth / 2 - myROIWidth / 2,
                            myHeight / 2 - myROIHeight / 2,
                            myROIWidth,
                            myROIHeight);
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
        mGray.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        // Timer
        long start = Core.getTickCount();
        // Grab camera frame in rgba and grayscale format
        mRgba = inputFrame.rgba();
        // Grab camera frame in gray format
        mGray = inputFrame.gray();

        // Action based on tracking flag
        if(tracking_flag == -1){
            // Update myROI to keep the window to the center
            myROI.x = myWidth / 2 - myROIWidth / 2;
            myROI.y = myHeight / 2 - myROIHeight / 2;
            myROI.width = myROIWidth;
            myROI.height = myROIHeight;
        }
        else if(tracking_flag == 0){
            // Initialize KCF Tracker and Start Tracking
            // 1. Create a KCF Tracker
            // 2. Initialize KCF Tracker with grayscale image and ROI
            // 3. Modify tracking flag to start tracking
            // ******************** START YOUR CODE HERE ******************** //

            myTacker = TrackerKCF.create();
            myTacker.init(mGray, myROI);
            tracking_flag = 1;


            // ******************** END YOUR CODE HERE ******************** //
        }
        else{
            // Update tracking result is succeed
            // If failed, print text "Tracking failure occurred!" at top left corner of the frame
            // Calculate and display "FPS@fps_value" at top right corner of the frame
            // ******************** START YOUR CODE HERE ******************** //

            boolean success = myTacker.update(mGray, myROI);
            if (!success) {
                Imgproc.putText(mRgba, "Tracking failure occurred!", new Point(200, 200), Core.FONT_HERSHEY_TRIPLEX, 2.0, new Scalar(255,255,255));
            }
            double fps = Math.round( Core.getTickFrequency() / ((double) Core.getTickCount() - start) );
            Imgproc.putText(mRgba, "FPS@"+fps, new Point(myWidth-400,myHeight-100), Core.FONT_HERSHEY_COMPLEX, 1.0, new Scalar(255,255,255));

            // ******************** END YOUR CODE HERE ******************** //
        }

        // Draw a rectangle on to the current frame
        Imgproc.rectangle(mRgba,
                          new Point(myROI.x, myROI.y),
                          new Point(myROI.x + myROI.width, myROI.y + myROI.height),
                          myROIColor,
                        4);

        // Returned frame will be displayed on the screen
        return mRgba;

        // add comments
    }
}