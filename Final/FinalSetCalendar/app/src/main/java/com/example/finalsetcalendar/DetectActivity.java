package com.example.finalsetcalendar;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.Color;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import java.util.Arrays;


public class DetectActivity extends AppCompatActivity {

    ImageView textImage;
    Button stepBtn;
    Bitmap bmp;
    int height, width;
    int stepFlag = 0;

    Mat rgbaMat, grayMat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect);
        textImage = findViewById(R.id.textImageView);
        stepBtn = findViewById(R.id.stepBtn);

        rgbaMat = new Mat();
        grayMat = new Mat();

        // Load bitmap from main activity
        bmp = MainActivity.bmp;
        height = bmp.getHeight();
        width = bmp.getWidth();
        //Log.d("tag", "(height, width) = " + height + ", " + width);

        // Display orig image
        textImage.setImageBitmap(bmp);

        stepBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("tag", "step" + stepFlag);
                //test();
                //convertToGray();
                encode();
            }
        });

    }

    private void encode() {

        // get gray Mat
        Utils.bitmapToMat(bmp, rgbaMat);
        Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGB2GRAY);

        Log.d("tag", String.valueOf(grayMat.total()));
        Log.d("tag", String.valueOf(grayMat.channels()));

        byte[] imgData = new byte[(int) (grayMat.total() * grayMat.channels())];
        Arrays.fill(imgData, (byte) 0);
        grayMat.put(0, 0, imgData);

        Rect r = new Rect(10, 10, 100, 100);
        Mat smallImg = grayMat.submat(r);


        // show image
        Bitmap tmp = Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565);
        Utils.matToBitmap(smallImg, tmp);
        textImage.setImageBitmap(tmp);

    }

    private void test() {
        Bitmap tmp = Bitmap.createBitmap(bmp.getWidth(),bmp.getHeight(), bmp.getConfig());
        for (int i=0; i<width; i++) {
            for (int j=0; j<height; j++) {
                int p = bmp.getPixel(i, j);
                tmp.setPixel(i, j, Color.argb(Color.alpha(p), Color.red(p), 0, 0));
            }
            //Log.d("tag", "i=" + i);
        }
        textImage.setImageBitmap(tmp);
        Log.d("tag", "step" + stepFlag + " done");
    }


    public void convertToGray(){

        Bitmap grayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

        // Bitmap to Mat
        Utils.bitmapToMat(bmp, rgbaMat);
        Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGB2GRAY);

        // Mat to Bitmap
        Utils.matToBitmap(grayMat, grayBitmap);
        // Bitmap to Imageview
        textImage.setImageBitmap(grayBitmap);

    }


}