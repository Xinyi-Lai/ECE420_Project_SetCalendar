package com.ece420.lab6;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.Manifest;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;


public class MainActivity extends AppCompatActivity {

    // Flag to control app behavior
    public static int appFlag = 0;
    // UI Variables
    private Button histeqButton;
    private Button sharpButton;
    private Button edgeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        super.setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Request User Permission on Camera
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, 1);}

        // Setup Button for Histogram Equalization
        histeqButton = (Button) findViewById(R.id.histeqButton);
        histeqButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appFlag = 1;
                startActivity(new Intent(MainActivity.this, CameraActivity.class));
            }
        });

        // Setup Button for Sharpening
        sharpButton = (Button) findViewById(R.id.sharpeButton);
        sharpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appFlag = 2;
                startActivity(new Intent(MainActivity.this, CameraActivity.class));
            }
        });

        // Setup Button for Edge Detection
        edgeButton = (Button) findViewById(R.id.edgeButton);
        edgeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appFlag = 3;
                startActivity(new Intent(MainActivity.this, CameraActivity.class));
            }
        });

    }

    @Override
    protected void onResume(){
        super.setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        super.onResume();
    }

}
