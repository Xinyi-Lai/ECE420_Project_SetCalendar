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
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.features2d.MSER;

import java.util.ArrayList;
import java.util.List;

import static org.opencv.features2d.MSER.*;


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
                process();
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


    public void process(){

        Bitmap grayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

        // Bitmap to Mat
        Utils.bitmapToMat(bmp, rgbaMat);
        Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGB2GRAY);

        // detect and mark MSERs
        rgbaMat = mser_detect(grayMat, rgbaMat);

        // Mat to Bitmap
        Utils.matToBitmap(rgbaMat, grayBitmap);
        // Bitmap to Imageview
        textImage.setImageBitmap(grayBitmap);

    }

    public Mat mser_detect(Mat graymat, Mat rgbamat){
        // basic variable
        MSER mser = create();
        List<MatOfPoint> msers = new ArrayList<MatOfPoint>();
        MatOfRect bboxes = new MatOfRect();

        // detect MSER regions
        mser.detectRegions(graymat, msers, bboxes);

        // get a list of rects
        List<Rect> rects = new ArrayList<Rect>();
        for (int i=0; i<msers.size(); i++) {
            rects.add(Imgproc.boundingRect(msers.get(i)));
        }

        // reduce MSER regions
        List<Rect> real_rects = reduce_mser(rects);

        // not reduce MSER regions
//        List<Rect> real_rects = rects;

        // mark MSER region
        for (int i=0; i<real_rects.size(); i++) {
            Imgproc.rectangle(rgbamat, real_rects.get(i).tl(), real_rects.get(i).br(), new Scalar(0, 255, 0), 2);
        }

        // marked image
        return rgbamat;
    }

    public boolean cover(Rect rect1, Rect rect2){
        if (rect1.x <= rect2.x && rect1.x+rect1.width >= rect2.x+rect2.width &&
            rect1.y <= rect2.y && rect1.y+rect1.height >= rect2.y+rect2.height){
            return true;
        }else{
            return false;
        }
    }

    public boolean isMaximal(Rect rect, List<Rect> rects){
        for (int i=0; i<rects.size(); i++){
            if (rect != rects.get(i) && cover(rects.get(i), rect)){
                return false;
            }
        }
        return true;
    }

    public List<Rect> reduce_mser(List<Rect> regions){
        List<Rect> rects = new ArrayList<Rect>();
        List<Rect> real_rects = new ArrayList<Rect>();

        // region edge
        double EDGE_MIN = (double) (Math.min(height,width)/60.0);
        double EDGE_MAX = (double) (Math.max(height,width)*0.8);
        // region area
        double AREA_MIN = (double) (height*width/2000.0);
        double AREA_MAX = (double) (height*width*0.2);
        // aspect ratio
        double AR_MIN = 0.3;
        double AR_MAX = 1.5;

        for (int i=0; i<regions.size(); i++){
            if (Math.min(regions.get(i).width, regions.get(i).height) < EDGE_MAX &&
                    Math.max(regions.get(i).width, regions.get(i).height) > EDGE_MIN &&
                    regions.get(i).width*regions.get(i).height < AREA_MAX &&
                    regions.get(i).width*regions.get(i).height > AREA_MIN &&
                    (float)(regions.get(i).width/regions.get(i).height) < AR_MAX &&
                    (float)(regions.get(i).width/regions.get(i).height) > AR_MIN){
                if (!rects.contains(regions.get(i))){
                    rects.add(regions.get(i));
                }
            }
        }

        for (int i=0; i<rects.size(); i++){
            if (isMaximal(rects.get(i), rects)){
                real_rects.add(rects.get(i));
            }
        }

        return real_rects;
    }
}