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
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.features2d.MSER;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;

import static org.opencv.core.CvType.CV_8U;
import static org.opencv.features2d.MSER.*;
import static org.opencv.imgproc.Imgproc.INTER_AREA;
import static org.opencv.imgproc.Imgproc.resize;


public class DetectActivity extends AppCompatActivity {

    ImageView textImage;
    Button stepBtn, resetBtn;
    Bitmap bmp;
    int height, width;
    int stepFlag;
    double maxsim = -1.0;
    String letter = "";

    Mat origMat, rgbaMat, grayMat, bwMat;
    List<Rect> rects, strong_text, weak_text, non_text;
    Dictionary<String, String> mlbp_dict;

    // *****************************you can directly copy and paste the code ABOVE this line*************************************

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect);
        textImage = findViewById(R.id.textImageView);
        stepBtn = findViewById(R.id.stepBtn);
        resetBtn = findViewById(R.id.resetBtn);
        // Initialization
        reset();

        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reset();
            }
        });

        stepBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stepFlag += 1;
                // Step 1: extract MSER
                if (stepFlag == 1) {
                    Log.d("tag", "step" + stepFlag + ": extract MSER");
                    mser_detect();
                    Log.d("tag", "number of MSER regions: " + rects.size());
                }
                // Step 2: reduce MSER
                else if (stepFlag == 2) {
                    Log.d("tag", "step" + stepFlag + ": reduce MSER");
                    mser_reduce();
                    Log.d("tag", "number of regions: " + rects.size());
                }
                // Step 3: classify ROI
                else if (stepFlag == 3) {
                    Log.d("tag", "step" + stepFlag + ": classify ROI");
                    //classify();
                }

                // Mat to Bitmap to Imageview
                Utils.matToBitmap(rgbaMat, bmp);
                textImage.setImageBitmap(bmp);
            }
        });

    }

    private void reset() {
        // reset variable
        stepFlag = 0;
        origMat = new Mat();
        rgbaMat = new Mat();
        grayMat = new Mat();
        bwMat = new Mat(); // *************************bwMat added*********************************
        rects = new ArrayList<Rect>();
        // *******************************three new List<Rect> added*********************************
        strong_text = new ArrayList<Rect>();
        weak_text = new ArrayList<Rect>();
        non_text = new ArrayList<Rect>();
        // ******************************************************************************************

        // Load bitmap from main activity
        bmp = MainActivity.bmp.copy(MainActivity.bmp.getConfig(), false);
        height = bmp.getHeight();
        width = bmp.getWidth();
        //Log.d("tag", "(height, width) = " + height + ", " + width);

        // Bitmap to Mat
        Utils.bitmapToMat(bmp, origMat);
        Imgproc.cvtColor(origMat, grayMat, Imgproc.COLOR_RGB2GRAY);
        Imgproc.threshold(grayMat, bwMat, 128, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU); //*************bwMat added*************
        // Display orig image
        textImage.setImageBitmap(bmp);
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


    public void mser_detect(){
        // basic variable
        MSER mser = create();
        List<MatOfPoint> msers = new ArrayList<MatOfPoint>();
        MatOfRect bboxes = new MatOfRect();
        // detect MSER regions
        mser.detectRegions(grayMat, msers, bboxes);

        // get a list of rects
        for (int i=0; i<msers.size(); i++) {
            rects.add(Imgproc.boundingRect(msers.get(i)));
        }
        // mark MSER region
        origMat.copyTo(rgbaMat);
        for (int i=0; i<rects.size(); i++) {
            Imgproc.rectangle(rgbaMat, rects.get(i).tl(), rects.get(i).br(), new Scalar(0, 255, 0), 2);
        }
    }


    public void mser_reduce(){
        // reduce MSER regions
        rects = reduce_mser(rects);

        // mark MSER region
        origMat.copyTo(rgbaMat);
        for (int i=0; i<rects.size(); i++) {
            Imgproc.rectangle(rgbaMat, rects.get(i).tl(), rects.get(i).br(), new Scalar(0, 255, 0), 2);
        }
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
        double AR_MAX = 2.5;

        for (int i=0; i<regions.size(); i++){
            if (Math.min(regions.get(i).width, regions.get(i).height) < EDGE_MAX &&
                    Math.max(regions.get(i).width, regions.get(i).height) > EDGE_MIN &&
                    regions.get(i).width*regions.get(i).height < AREA_MAX &&
                    regions.get(i).width*regions.get(i).height > AREA_MIN &&
                    ((double)regions.get(i).width) / ((double)regions.get(i).height) < AR_MAX &&
                    ((double)regions.get(i).width) / ((double)regions.get(i).height) > AR_MIN
            ){
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

    // ************************************************ new code after mser is fixed *******************************************************
    public String mlbp_encode(Mat img, int size, boolean inv){
        String img_mlbp = "";

        for (int i=1; i<size-1; i++){
            for (int j=1; j<size-1; j++){
                String mlbp = "";
                Mat roi = img.adjustROI(i-1, i+2, j-1, j+2);
                double avg = mat_mean(roi);
                int[] neigh_row_idx = {i-1, i-1, i-1, i,   i+1, i+1, i+1, i};
                int[] neigh_col_idx = {j-1, j,   j+1, j+1, j+1, j,   j-1, j-1};

                for (int k=0; k<neigh_col_idx.length; k++){
                    if (!inv){
                        if (img.get(neigh_row_idx[k], neigh_col_idx[k])[0] > avg){
                            mlbp += "1";
                        }else{
                            mlbp += "0";
                        }
                    }else{
                        if (img.get(neigh_row_idx[k], neigh_col_idx[k])[0] < avg){
                            mlbp += "1";
                        }else{
                            mlbp += "0";
                        }
                    }
                }

                img_mlbp += mlbp;
            }
        }

        return img_mlbp;
    }

    public double mat_mean(Mat img){
        double sum = 0;
        for (int i=0; i<img.rows(); i++){
            for (int j=0; j<img.cols(); j++){
                sum += img.get(i, j)[0]; // should be grayscale
            }
        }
        return sum/(img.total());
    }

    // ***************************** NOTE: function name is encodeImg, the same as python *****************************************
    public String encodeImg(Mat img, boolean bw, boolean inv){
        return null; // ************I think you have accomplished this function, but with a different name "encode"*****************
    }

    public double compare_mlbp(String mlbp_1, String mlbp_2){
        if (mlbp_1 == null || mlbp_2 == null){
            return -1.0;
        }
        if (mlbp_1.length() != mlbp_2.length()){
            return -1.0;
        }

        int dist = 0;
        for (int i=0; i<mlbp_1.length(); i++){
            if (mlbp_1.charAt(i) != mlbp_2.charAt(i)){
                dist += 1;
            }
        }
        return 1.0 - ((double)dist / (double)mlbp_1.length());
    }

    public void cannyTextClassifier(Mat roi, Dictionary<String, String> mlbp_dict){
        String roi_mlbp = encodeImg(roi, true, false);
        String inv_roi_mlbp = encodeImg(roi, true, true);

        List<String> exempt_list = new ArrayList<String>(); // *****************an unused list for you to test****************************

        Enumeration<String> keys = mlbp_dict.keys();
        while (keys.hasMoreElements()) {
            String cur_key = keys.nextElement();
            String std_mlbp = mlbp_dict.get(cur_key);
            double sim = Math.max(compare_mlbp(roi_mlbp, std_mlbp), compare_mlbp(inv_roi_mlbp, std_mlbp));
            if (sim > maxsim && !exempt_list.contains(cur_key)) {
                maxsim = sim;
                letter = cur_key;
            }
        }
    }

    public void cannyTextClassify(List<Rect> rois){
        double th1 = 0.75;
        double th2 = 0.45;

        double tth1 = 0.88;
        double tth2 = 0.60;

        double lth1 = 0.85;
        double lth2 = 0.60;

        for (int i=0; i<rois.size(); i++){
            Mat roi = bwMat.adjustROI(rois.get(i).y, rois.get(i).y+rois.get(i).height, rois.get(i).x, rois.get(i).x+rois.get(i).width);

            cannyTextClassifier(roi, mlbp_dict); // update maxsim and letter

            if (letter != "i" && letter != "j"){
                if (letter == "t"){
                    if (maxsim > tth1){
                        strong_text.add(rois.get(i));
                    }else if (maxsim > tth2){
                        weak_text.add(rois.get(i));
                    }else{
                        non_text.add(rois.get(i));
                    }
                }else if (letter == "l"){
                    if (maxsim > lth1){
                        strong_text.add(rois.get(i));
                    }else if (maxsim > lth2){
                        weak_text.add(rois.get(i));
                    }else{
                        non_text.add(rois.get(i));
                    }
                }else if (maxsim > th1){
                    strong_text.add(rois.get(i));
                }else if (maxsim > th2){
                    weak_text.add(rois.get(i));
                }else {
                    non_text.add(rois.get(i));
                }
            }

        }
    }

    // top-level function for canny text classification
    public void mser_classify(){
        // classify MSER regions
        cannyTextClassify(rects);

        // mark strong text region with green boxes
        origMat.copyTo(rgbaMat);
        for (int i=0; i<strong_text.size(); i++) {
            Imgproc.rectangle(rgbaMat, strong_text.get(i).tl(), strong_text.get(i).br(), new Scalar(0, 255, 0), 2);
        }

        // mark weak text region with red boxes
        for (int i=0; i<weak_text.size(); i++) {
            Imgproc.rectangle(rgbaMat, weak_text.get(i).tl(), weak_text.get(i).br(), new Scalar(255, 0, 0), 2);
        }
    }
}