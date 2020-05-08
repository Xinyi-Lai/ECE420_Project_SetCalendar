package com.example.finalsetcalendar;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.icu.util.Calendar;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.CalendarContract;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.googlecode.tesseract.android.TessBaseAPI;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Range;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.MSER;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.opencv.features2d.MSER.create;
import static org.opencv.imgproc.Imgproc.INTER_AREA;
import static org.opencv.imgproc.Imgproc.resize;


public class DetectActivity extends AppCompatActivity {

    ImageView textImage;
    Button stepBtn, resetBtn;
    Bitmap bmp;
    int height, width;
    int stepFlag;
    String exact_text; // used to store the exact text extracted by OCR

    Mat origMat, rgbaMat, grayMat, bwMat = new Mat();
    List<Rect> rects, strong_text, weak_text, non_text, text = new ArrayList<Rect>();
    Rect detect_rect;

    //four variables created for OCR
    private static final String TAG = "TessTag"; // tag for Tess part
    private  static final String DATA_PATH = Environment.getExternalStorageDirectory().toString() + "/";  // not "/Tess"
//    private final String DATA_PATH = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) + "/"; //cant use the app files...
    private static final String TESS_DATA = "/tessdata"; //a subfolder mandatory for OCR, where the eng.traineddata is stored
    private TessBaseAPI tessBaseAPI; //an instance for the TessBaseAPI to implement OCR

    //Permission request
    private static final int PERMISSION_REQUEST_CODE=0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect);
        textImage = findViewById(R.id.textImageView);
        stepBtn = findViewById(R.id.stepBtn);
        resetBtn = findViewById(R.id.resetBtn);
        reset();        // Initialization

        // Permission to write on the external storage
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            }
        }

        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reset();
            }
        });

        stepBtn.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onClick(View v) {

                stepFlag += 1;
                // Step 1: extract MSER
                if (stepFlag == 1) {
                    rects = mser_detect();
                    markRect(rects, new Scalar(0,255,0));
                    Log.d("tag", "step" + stepFlag + ": extract MSER => number of MSER regions: " + rects.size());
                    Toast.makeText(DetectActivity.this, "MSER extracted", Toast.LENGTH_SHORT).show();
                }
                // Step 2: reduce MSER
                else if (stepFlag == 2) {
                    rects = mser_reduce(rects);
                    markRect(rects, new Scalar(0,255,0));
                    Log.d("tag", "step" + stepFlag + ": reduce MSER => number of regions: " + rects.size());
                    Toast.makeText(DetectActivity.this, "MSER reduced", Toast.LENGTH_SHORT).show();
//                    // DEBUG
//                    for (int i=0; i<rects.size(); i++)
//                        Log.d("tag", "rect[" + i + "]: " + rects.get(i));
                }
                // Step 3: classify ROI
                else if (stepFlag == 3) {
                    Log.d("tag", "step" + stepFlag + ": classify ROI");
                    Toast.makeText(DetectActivity.this, "Classifying...", Toast.LENGTH_SHORT).show();
                    cannyClassify();
                }
                // Step 4: Hysteresis
                else if(stepFlag == 4) {
                    text = hysteresis_tracking(strong_text);
                    markRect(text, new Scalar(0,255,0));
                    Log.d("tag", "step" + stepFlag + ": Hysteresis");
                    Toast.makeText(DetectActivity.this, "Hysteresis Tracking", Toast.LENGTH_SHORT).show();
                }
                // Step 5: Grouping
                else if(stepFlag == 5) {
                    text = grouping(text);
                    markRect(text, new Scalar(0,255,0));
                    Log.d("tag", "step" + stepFlag + ": Grouping");
                    Toast.makeText(DetectActivity.this, "Grouping", Toast.LENGTH_SHORT).show();
                }
                // Step 6: Extracting the texts with OCR
                else if(stepFlag == 6) {
                    Log.d("tag", "step" + stepFlag + ": Extracting text");
                    Toast.makeText(DetectActivity.this, "Extracting text...", Toast.LENGTH_SHORT).show();

                    prepareTessData();

                    Mat tessMat = new Mat();
                    grayMat.copyTo(tessMat);
                    // rotate the image before ocr
                    Mat rotM = Imgproc.getRotationMatrix2D(new Point(tessMat.cols()/2, tessMat.rows()/2), 270, 1);
                    Imgproc.warpAffine(tessMat, tessMat, rotM, tessMat.size());
                    detectText(tessMat); //this one extract all the text in rgbaMat (this should be fine, if prepareTessData() works)

                    Log.d("tag", exact_text);
                    Toast.makeText(DetectActivity.this, exact_text, Toast.LENGTH_SHORT).show();
                }
                // Step 7: Set calendar
                else if(stepFlag == 7) {
                    set_calendar();
                }

                // Mat to Bitmap to Imageview
                Imgproc.rectangle(rgbaMat, detect_rect.tl(), detect_rect.br(), new Scalar(180,180,180), 2);
                Utils.matToBitmap(rgbaMat, bmp);
                textImage.setImageBitmap(bmp);
            }
        });

    }


    /**
     * Initialization and reset
     * Clean all the local variables and reload the bitmap from main activity
     */
    private void reset() {
        // reset variable
        stepFlag = 0;
        origMat = new Mat();
        rgbaMat = new Mat();
        grayMat = new Mat();
        bwMat = new Mat();
        rects = new ArrayList<Rect>();
        strong_text = new ArrayList<Rect>();
        weak_text = new ArrayList<Rect>();
        non_text = new ArrayList<Rect>();

        // Load bitmap from main activity
        bmp = MainActivity.bmp.copy(MainActivity.bmp.getConfig(), false);
        height = bmp.getHeight();
        width = bmp.getWidth();
        //Log.d("tag", "(height, width) = " + height + ", " + width);

        // Real detection area
        detect_rect = new Rect(new Point(width/4, height/10), new Point(width*3/4, height*9/10));

        // Bitmap to Mat
        Utils.bitmapToMat(bmp, origMat);
        Imgproc.cvtColor(origMat, grayMat, Imgproc.COLOR_RGB2GRAY);
        Imgproc.threshold(grayMat, bwMat, 128, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);

        // Display orig image
        origMat.copyTo(rgbaMat);
        Imgproc.rectangle(rgbaMat, detect_rect.tl(), detect_rect.br(), new Scalar(150, 150, 150), 2);
        Utils.matToBitmap(rgbaMat, bmp);
        textImage.setImageBitmap(bmp);
    }


    /**
     * Mark the rectangle on the image
     * @param rects a list of rectangles to be marked
     * @param color the color of the rectangles
     */
    private void markRect(List<Rect> rects, Scalar color) {
        origMat.copyTo(rgbaMat);
        for (int i=0; i<rects.size(); i++) {
            Imgproc.rectangle(rgbaMat, rects.get(i).tl(), rects.get(i).br(), color, 2);
        }
    }


    /**
     * Extract all the MSER regions, using Imgproc
     * @return a list of MSER regions
     */
    private List<Rect> mser_detect(){
        // basic variable
        MSER mser = create();
        List<MatOfPoint> msers = new ArrayList<>();
        MatOfRect bboxes = new MatOfRect();
        // detect MSER regions
        mser.detectRegions(grayMat, msers, bboxes);
        // get a list of rects
        List<Rect> rects = new ArrayList<>();
        for (int i=0; i<msers.size(); i++) {
            rects.add(Imgproc.boundingRect(msers.get(i)));
        }
        return rects;
    }


    /**
     * Reduce the MSER regions with inappropriate size and that are not maximal
     * @param regions the list of regions to be reduced
     * @return a list of reduced regions
     */
    private List<Rect> mser_reduce(List<Rect> regions){
        List<Rect> rects = new ArrayList<>();
        List<Rect> real_rects = new ArrayList<>();

        // region edge
        double EDGE_MIN = Math.min(height,width)/60.0;
        double EDGE_MAX = Math.max(height,width)*0.8;
        // region area
        double AREA_MIN = height*width/2000.0;
        double AREA_MAX = height*width*0.1;
        // aspect ratio
        double AR_MIN = 0.2;
        double AR_MAX = 3;

        for (int i=0; i<regions.size(); i++){
            int sqsize = Math.max(regions.get(i).width, regions.get(i).height);
            if (Math.min(regions.get(i).width, regions.get(i).height) < EDGE_MAX &&
                    Math.max(regions.get(i).width, regions.get(i).height) > EDGE_MIN &&
                    regions.get(i).width*regions.get(i).height < AREA_MAX &&
                    regions.get(i).width*regions.get(i).height > AREA_MIN &&
                    ((double)regions.get(i).width) / ((double)regions.get(i).height) < AR_MAX &&
                    ((double)regions.get(i).width) / ((double)regions.get(i).height) > AR_MIN &&
                    regions.get(i).x + sqsize < grayMat.cols() && regions.get(i).x + sqsize < grayMat.rows()
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


    /**
     * Helper function of mser_reduce, check if rect1 covers rect2
     * @param rect1 rect1
     * @param rect2 rect2
     * @return true if rect1 covers rect2
     */
    private boolean cover(Rect rect1, Rect rect2){
        return rect1.x <= rect2.x && rect1.x + rect1.width >= rect2.x + rect2.width &&
                rect1.y <= rect2.y && rect1.y + rect1.height >= rect2.y + rect2.height;
    }

    /**
     * Helper function of mser_reduce, check if rect is maximal in the list of rects
     * @param rect the rect to be checked
     * @param rects the list of rects to be checked against
     * @return true if rect is maximal
     */
    private boolean isMaximal(Rect rect, List<Rect> rects){
        for (int i=0; i<rects.size(); i++){
            if (rect != rects.get(i) && cover(rects.get(i), rect)){
                return false;
            }
        }
        return true;
    }


    /**
     * Encode the given roi into an MLBP sequence
     * @param roi region of interest
     * @return MLBP sequence
     */
    private String encodeImg( Rect roi ) {

        // first resize
        int size = 32;
        Mat resizeMat = resizeROI(roi, size);
        // then encode
        String mlbp = mlbp_encode(resizeMat, size, false);
        //Log.d("tag", mlbp);
        return mlbp;
    }

    /**
     * Helper function of encodeImg, resize the given roi into specified size, padded with white and rotate
     * @param roi region of interest
     * @param size size
     * @return a resized roi, mat
     */
    private Mat resizeROI(Rect roi, int size) {
        Mat resizeMat;  // 32x32
        Mat tmp = new Mat();    // resized roi
        int h = roi.height;
        int w = roi.width;
        //Log.d("tag", "roi.width " + roi.width + "roi.height " + roi.height);

        // NOTE: FIXED: rotation padded with BLACK
        // Take a square to contain the ROI, so that it can be rotated smoothly
        int sqside = Math.max(h, w);
        Rect sq = new Rect(roi.x, roi.y, sqside, sqside);  // a larger square
        Mat roiMat = bwMat.submat(sq);
        //Log.d("tag", "sq: " + sq);
        resize(roiMat, tmp, new Size( size, size), 0, 0, INTER_AREA); // tmp.size() = 32x32

        float scale = (float)size / (float)sqside;
        Mat rotM = Imgproc.getRotationMatrix2D(new Point(size/2, size/2), 270, 1);  // rotate the 32x32 Mat


        //******** BE CAREFUL, THE IMAGE IS ROTATED CW 90 DEGREES, NEED TO ROTATE BACK *******//

        // if it is tall and thin, ACTUALLY it is short and fat
        if (h > w) {    // sqside = h
            Imgproc.warpAffine(tmp, tmp, rotM, tmp.size());
            int newR = (int) (w*scale+0.5);
            tmp = tmp.rowRange(new Range(0, newR ));
            int margin = size/2 - tmp.rows()/2;
            resizeMat = new Mat(margin, size, tmp.type(), new Scalar(255));
            resizeMat.push_back(tmp);
            Mat whiterows = new Mat(size-margin-tmp.rows(), size, tmp.type(), new Scalar(255));
            resizeMat.push_back(whiterows);
        }
        // if it is short and fat: h <= w, ACTUALLY it is tall and thin
        else {          // sqside = w
            int newR = (int) (h*scale+0.5);
            tmp = tmp.rowRange(new Range(0, newR ));
            int margin = size/2 - tmp.rows()/2;
            resizeMat = new Mat(margin, size, tmp.type(), new Scalar(255));
            resizeMat.push_back(tmp);
            Mat whiterows = new Mat(size-margin-tmp.rows(), size, tmp.type(), new Scalar(255));
            resizeMat.push_back(whiterows);
            Imgproc.warpAffine(resizeMat, resizeMat, rotM, resizeMat.size());
        }

/*        // show the resized roi
        byte[] resizeData = new byte[(int) (resizeMat.total()*resizeMat.channels())];
        resizeMat.get(0, 0, resizeData);
        byte[] bwData = new byte[(int) (bwMat.total()*bwMat.channels()) ];
        for (int y = 0; y < bwMat.rows(); y++) {
            for (int x = 0; x < bwMat.cols(); x++) {
                for (int c = 0; c < bwMat.channels(); c++) {
                    // pad white pixels outside
                    if ( (x >= resizeMat.cols()) || y >= resizeMat.rows() ) {
                        bwData[(y * bwMat.cols() + x) * bwMat.channels() + c] = (byte)128;
                    } else {
                        byte pixelValue = resizeData[( y * resizeMat.cols() + x ) * resizeMat.channels() + c];
                        bwData[(y * bwMat.cols() + x) * bwMat.channels() + c] = pixelValue;
                    }
                }
            }
        }
        bwMat.put(0, 0, bwData);
        rgbaMat = bwMat;
*/
        return resizeMat;
    }


    /**
     * Helper function of encodeImg, MLBP (Mean Local Binary Pattern) encoding of a given image
     * @param img the ROI to be encoded
     * @param size size of the ROI, should be 32 in our case
     * @param inv whether or not the ROI needs to be inverted, false in our case, deal with scene text
     * @return the MLBP binary string
     */
    private String mlbp_encode(Mat img, int size, boolean inv){
        StringBuilder img_mlbp = new StringBuilder();

        for (int i=1; i<size-1; i++){
            for (int j=1; j<size-1; j++){
                StringBuilder mlbp = new StringBuilder();
                Mat roi = img.rowRange(i-1, i+2).colRange(j-1, j+2);
                double avg = mat_mean(roi);
                int[] neigh_row_idx = {i-1, i-1, i-1, i,   i+1, i+1, i+1, i};
                int[] neigh_col_idx = {j-1, j,   j+1, j+1, j+1, j,   j-1, j-1};

                for (int k=0; k<neigh_col_idx.length; k++){
                    if (!inv){
                        if (img.get(neigh_row_idx[k], neigh_col_idx[k])[0] > avg) mlbp.append("1");
                        else mlbp.append("0");
                    }else{
                        if (img.get(neigh_row_idx[k], neigh_col_idx[k])[0] < avg) mlbp.append("1");
                        else mlbp.append("0");
                    }
                }
                img_mlbp.append(mlbp);
            }
        }

        return img_mlbp.toString();
    }

    /**
     * Helper function of mlbp_encode
     * @param img the image region
     * @return the mean value of the image
     */
    private double mat_mean(Mat img){
        double sum = 0;
        for (int i=0; i<img.rows(); i++){
            for (int j=0; j<img.cols(); j++){
                sum += img.get(i, j)[0]; // should be grayscale
            }
        }
        return sum/(img.total());
    }


    /**
     * Classify the rois into strong text, weak text and non text based on the cannyclassifier's output
     */
    private void cannyClassify(){

        strong_text = new ArrayList<>();
        weak_text = new ArrayList<>();
        non_text = new ArrayList<>();

        for (int i=0; i<rects.size(); i++){
            String mlbp= encodeImg(rects.get(i));
            CannyClassifier myCanny = new CannyClassifier(mlbp);

            if (myCanny.theclass == myCanny.STRONG) {
                strong_text.add(rects.get(i));
            } else if (myCanny.theclass == myCanny.WEAK) {
                weak_text.add(rects.get(i));
            } else {
                non_text.add(rects.get(i));
            }
//            Log.d("tag", "classifying: " + i*100 / rects.size() + "%");
//            Log.d("tag", "letter: " + myCanny.letter + "; sim: " + myCanny.sim + "; class: " + myCanny.theclass);
        }

        Log.d("tag", "# of strong: " + strong_text.size());
        Log.d("tag", "# of weak: " + weak_text.size());

        // mark strong text with green boxes, weak text with red boxes, non text with blue boxes
        origMat.copyTo(rgbaMat);
        for (int i=0; i<strong_text.size(); i++)
            Imgproc.rectangle(rgbaMat, strong_text.get(i).tl(), strong_text.get(i).br(), new Scalar(0, 255, 0), 2);
        for (int i=0; i<weak_text.size(); i++)
            Imgproc.rectangle(rgbaMat, weak_text.get(i).tl(), weak_text.get(i).br(), new Scalar(255, 0, 0), 2);
        for (int i=0; i<non_text.size(); i++)
            Imgproc.rectangle(rgbaMat, non_text.get(i).tl(), non_text.get(i).br(), new Scalar(0, 0, 255), 2);
    }


    /**
     * Hysteresis tracking
     * @param strong_text strong text to be hysteresis off
     * @return a list of valid text inside the detect area
     */
    private List<Rect> hysteresis_tracking( List<Rect> strong_text) {
        List<Rect> real_text = new ArrayList<>();
        List<Rect> temp = new ArrayList<>(strong_text);

        while(!strong_text.isEmpty()) {
            for(int i = 0; i < weak_text.size(); i++) {
                if(!temp.contains(weak_text.get(i)) && close(weak_text.get(i),strong_text.get(0))) {
                    temp.add(weak_text.get(i));
                    strong_text.add(weak_text.get(i));
                }
            }
            strong_text.remove(0);
        }

        // restrict the central part to be the valid text
        for (int i=0; i<temp.size(); i++){
            if (temp.get(i).tl().x >= detect_rect.x  && temp.get(i).tl().y >= detect_rect.y &&
                    temp.get(i).br().x <= detect_rect.x+detect_rect.width && temp.get(i).br().y <= detect_rect.y+detect_rect.height) {
                real_text.add(temp.get(i));
            }
        }
        Log.d("tag", "text size: " + real_text.size());
        return real_text;
    }

    /**
     * Helper function for hysteresis tracking, decide whether rect a and rect b are close enough
     * @param a rect a
     * @param b rect b
     * @return true if a and b are close enough
     */
    private boolean close(Rect a, Rect b) {
        double align_ratio = .2;
        double h_space_ratio = .75;
        double v_space_ratio = .05;

        // coordinate transformation

        int xa = height- (a.y+a.height);
        int ya = a.x;
        int wa = a.height;
        int ha = a.width;

        int xb = height - (b.y+b.height);
        int yb = b.x;
        int wb = b.height;
        int hb = b.width;

//        int xa = a.x;
//        int ya = a.y;
//
//        int wa = a.width;
//        int ha = a.height;
//
//        //dimensions of b
//        int xb = b.x;
//        int yb = b.y;
//
//        int wb = b.width;
//        int hb = b.height;

        if(yb <= ya + ha - hb*align_ratio && yb + hb >= ya + hb*align_ratio) {
            if((xb <= xa + wa + wb*h_space_ratio && xb >= xa + wa - wb*h_space_ratio ) ||
                    (xb + wb >= xa - wb*h_space_ratio && xb + wb <= xa + wb*h_space_ratio)) {
                return true;
            }
        }
        if(xb <= xa + wa + wb*align_ratio && xb + wb >= xa - wb*align_ratio) {
            if((yb <= ya + ha + hb*v_space_ratio && yb >= ya + ha - hb*v_space_ratio ) ||
                    (yb + hb >= ya - hb*v_space_ratio && yb + hb <= ya + hb*v_space_ratio)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Grouping each line of text together
     * @param text to be grouped
     * @return grouped text
     */
    private List<Rect> grouping(List<Rect> text) {

        //sort text
        Collections.sort(text, new Comparator<Rect>() {
            @Override
            public int compare(Rect a, Rect b) {
                //sort by x //which is sorting by y of actual
                int xa = height- (a.y+a.height);
                int ya = a.x;
                int xb = height - (b.y+b.height);
                int yb = b.x;

//                int xa = a.x;
//                int ya = a.y;
//                int xb = b.x;
//                int yb = b.y;

                //sort by y
                if(ya < yb) return -1;
                // then sort by x
                else if(ya == yb) {
                    if(xa < xb) return -1;
                    else return 1;
                }
                else return 1;
            }
        });

//        int count = 0;
        while(true) {
//            count++;
            //Log.d("tag", "infinite loop 1");
            //all possible merges
            while(true) {
                int merge_index = find_merge_idx(text.get(0), text);
                // if nothing to be merged
                if(merge_index == -1)
                    break;

                // merge rectangle
                int x = Math.min(text.get(0).x,text.get(merge_index).x);
                int y = Math.min(text.get(0).y,text.get(merge_index).y);
                int w = Math.max(text.get(0).x + text.get(0).width,text.get(merge_index).x+ text.get(merge_index).width) - x;
                int h = Math.max(text.get(0).y +text.get(0).height,text.get(merge_index).y+ text.get(merge_index).height) - y;

                // replace the first rect with the merged rect and removed the one being merged
                text.set(0,new Rect(x,y,w,h));
                text.remove(merge_index);
            }

            // move element to the end so we can compare the next element
            text.add(text.get(0));
            text.remove(0);
            // check if we can continue merging
            int stop = 1;
            for(int i = 0; i < text.size();i++) {
                if(find_merge_idx(text.get(i), text) != -1) {
                    stop = 0;
                    break;
                }
            }
            if(stop==1)
                break;
        }
        Log.d("tag", "final grouped text size: " + text.size());
        return text;

    }

    /**
     * Helper function for grouping, find the index of rect to merge with a
     * @param a rect to be merged
     * @return the index of rect that are close enough to a and can be merged
     */
    private int find_merge_idx(Rect a, List<Rect> text) {
        for(int i = 0; i < text.size(); i++)
            if (text.get(i) != a && close(a, text.get(i))) return i;
        return -1;
    }


    /**
     * On Permission, write the traineddata to the external storage
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.i(TAG, "onRequestPermissionsResult: "+grantResults[0]);
        switch (requestCode){
            case PERMISSION_REQUEST_CODE:
                if (grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    Log.i(TAG, "onRequestPermissionsResult: copy");
                    prepareTessData();
                }
                break;
            default:
                break;
        }
    }


    /**
     * store eng.traineddata onto the tablet
     */
    private void prepareTessData(){

            String path = DATA_PATH + TESS_DATA + "/eng.traineddata";
            String name = "eng.traineddata";

            Log.d(TAG, path);
            //if exist, delete it
            File f = new File(path);
            if (f.exists()){
                f.delete();
            }
            if (!f.exists()){
                File p = new File(f.getParent());
                if (!p.exists()){ //make the directory
                    p.mkdir();
                }
                try {
                    f.createNewFile(); //create eng.traineddata in tablet
                } catch (IOException e){
                    e.printStackTrace();
                    Log.d(TAG, "printStackTrace");
                }

                //write every thing in /assests/eng.traineddata to the eng.traindata in tablet
                InputStream is = null;
                OutputStream os = null;
                try {
                    is = this.getAssets().open(name);
                    File file = new File(path);
                    os = new FileOutputStream(file);
                    byte[] bytes = new byte[2048];
                    int len = 0;
                    while ((len = is.read(bytes)) != -1){
                        os.write(bytes, 0, len);
                    }
                    os.flush();
                } catch (IOException e){
                    e.printStackTrace();
                } finally {
                    try {
                        if (is != null){
                            is.close();
                        }
                        if (os != null){
                            os.close();
                        }
                    } catch (IOException e){
                        e.printStackTrace();
                    }
                }
            }
            Log.d(TAG, "prepare tess data succeeds");
    }

    /**
     * The main function that extract the text using OCR
     * @param mat a grayMat
     */
    private void detectText(Mat mat){
        List<Rect> rotatedtext = new ArrayList<>();
        for (int i=0; i<text.size(); i++) {
            Rect tmp = text.get(i);
            int rotx = height - (tmp.y+tmp.height) - (height-width)/2;
            int roty = tmp.x + (height-width)/2;
            int rotw = tmp.height;
            int roth = tmp.width;
            Rect rotRect = new Rect(rotx, roty, rotw, roth);
            //Log.d("tag", "textidx:"+i+" x:"+rotx+" y:"+roty+" w:"+rotw+" h:"+roth);
            rotatedtext.add(rotRect);
        }

        Bitmap bmp = null;
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i<rotatedtext.size(); ++i){
            Rect rotatedRect = rotatedtext.get(i);
            try {
                Mat croppedPart = mat.submat(rotatedRect);
                bmp = Bitmap.createBitmap(croppedPart.width(), croppedPart.height(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(croppedPart, bmp);
            } catch (Exception e){
                Log.d(TAG, "Cropped part error");
            }
            if (bmp != null){
                String str = getTextWithTesseract (bmp); //this is where always get an error
                if (str != null){
                    sb.append(str).append("\n");
                }
            }
        }
        exact_text = sb.toString(); //this is what we are looking for, THE EXACT TEXT!!!
        origMat.copyTo(rgbaMat);
        for (int i=0; i<text.size(); i++) {
            Imgproc.rectangle(rgbaMat, text.get(i).tl(), text.get(i).br(), new Scalar(0, 255, 0), 2);
        }

//        Imgproc.putText(rgbaMat, exact_text, new Point(100,100), Core.FONT_HERSHEY_COMPLEX, 1.0, new Scalar(255,255,255));
//        mat.copyTo(rgbaMat);
//        for (int i=0; i<text.size(); i++) {
//            Imgproc.rectangle(rgbaMat, rotatedtext.get(i).tl(), rotatedtext.get(i).br(), new Scalar(0, 255, 0), 2);
//        }

    }

    /**
     * Helper function to get the string
     * @param bitmap text area
     * @return the detected text string in the given text area
     */
    private String getTextWithTesseract(Bitmap bitmap){
        try {
            tessBaseAPI = new TessBaseAPI();
        } catch (Exception e){
            Log.d(TAG, e.getMessage());
        }
        tessBaseAPI.init(DATA_PATH, "eng");
        tessBaseAPI.setImage(bitmap);
        String retStr = tessBaseAPI.getUTF8Text();
        tessBaseAPI.end();
        //Log.d(TAG, retStr);
        return retStr;
    }


    //added for setting calendar
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void set_calendar(){

        // Parse the exact text string
        String title = exact_text.split("\n")[0];
        String date = exact_text.split("\n")[1];
        String time = exact_text.split("\n")[2];
        String location = exact_text.split("\n")[3];
        int year = Integer.parseInt(date.split(" ")[2]);
        int day = Integer.parseInt(date.split(" ")[0]);
        int month;
        switch (date.split(" ")[1]) {
            case "JAN": {month = 0; break;}
            case "FEB": {month = 1; break;}
            case "MAR": {month = 2; break;}
            case "APR": {month = 3; break;}
            case "MAY": {month = 4; break;}
            case "JUN": {month = 5; break;}
            case "JUL": {month = 6; break;}
            case "AUG": {month = 7; break;}
            case "SEP": {month = 8; break;}
            case "OCT": {month = 9; break;}
            case "NOV": {month = 10; break;}
            case "DEC": {month = 11; break;}
            default: month = 0;
        }

        int startHour = Integer.parseInt( time.split(" TO ")[0].split(":")[0] );
        int startMinute = Integer.parseInt( time.split(" TO ")[0].split(":")[1] );
        int endHour = Integer.parseInt( time.split(" TO ")[1].split(":")[0] );
        int endMinute = Integer.parseInt( time.split(" TO ")[1].split(":")[1] );

        Calendar beginTime = Calendar.getInstance();
        beginTime.set(year, month, day, startHour, startMinute);
        Calendar endTime = Calendar.getInstance();
        endTime.set(year, month, day, endHour, endMinute);

        Intent intent = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTime.getTimeInMillis())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime.getTimeInMillis())
                .putExtra(CalendarContract.Events.TITLE, title)
                .putExtra(CalendarContract.Events.DESCRIPTION, title)
                .putExtra(CalendarContract.Events.EVENT_LOCATION, location);
//                .putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
//                .putExtra(Intent.EXTRA_EMAIL, "rowan@example.com, trevor@example.com");
        startActivity(intent);
    }
}