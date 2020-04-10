package com.example.finalsetcalendar;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.Color;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;

public class DetectActivity extends AppCompatActivity {

    ImageView textImage;
    Button stepBtn;
    Bitmap bmp;
    int height, width;
    int stepFlag = 0;

    byte[] imageByte;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect);
        textImage = findViewById(R.id.textImageView);
        stepBtn = findViewById(R.id.stepBtn);

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
                test();
            }
        });

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

}