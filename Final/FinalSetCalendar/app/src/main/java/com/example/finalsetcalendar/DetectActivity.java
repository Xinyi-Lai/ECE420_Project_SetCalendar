package com.example.finalsetcalendar;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.List;

import static org.opencv.features2d.MSER.create;
import static org.opencv.imgproc.Imgproc.INTER_AREA;
import static org.opencv.imgproc.Imgproc.PROJ_SPHERICAL_EQRECT;
import static org.opencv.imgproc.Imgproc.resize;


public class DetectActivity extends AppCompatActivity {

    ImageView textImage;
    Button stepBtn, resetBtn;
    Bitmap bmp;
    int height, width;
    int stepFlag;

    Mat origMat, rgbaMat, grayMat, bwMat;
    List<Rect> rects, strong_text, weak_text, non_text, text;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect);
        textImage = findViewById(R.id.textImageView);
        stepBtn = findViewById(R.id.stepBtn);
        resetBtn = findViewById(R.id.resetBtn);
        reset();        // Initialization

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
                    mser_detect();
                    Log.d("tag", "step" + stepFlag + ": extract MSER => number of MSER regions: " + rects.size());
                    Toast.makeText(DetectActivity.this, "MSER extracted", Toast.LENGTH_SHORT).show();
                }
                // Step 2: reduce MSER
                else if (stepFlag == 2) {
                    mser_reduce();
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

                    //String mlbp= encodeImg(rects.get(7));   //"M"
                    //String mlbp= encodeImg(rects.get(5));   //"6"
                    //CannyClassifier myCanny = new CannyClassifier(mlbp);
                    //double sim = myCanny.compare_mlbp(mlbp, myCanny.mlbp_dict.get("6"));
                    //Log.d("tag", "simwithM: " + sim);
                    //Log.d("tag", "letter: " + myCanny.letter);
                    //Log.d("tag", "sim: " + myCanny.sim);
                    //Log.d("tag", "class: " + myCanny.theclass);

                //hysteresis
                } else if(stepFlag == 4) {
                    Log.d("tag", "step" + stepFlag + ": Hysteresis");
                    Toast.makeText(DetectActivity.this, "Hysteresis...", Toast.LENGTH_SHORT).show();
                    hysteresis_tracking();

                    //goruping
                } else if(stepFlag == 5) {
                    Log.d("tag", "step" + stepFlag + ": Grouping");
                    Toast.makeText(DetectActivity.this, "Grouping...", Toast.LENGTH_SHORT).show();
                    grouping();
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

        // Bitmap to Mat
        Utils.bitmapToMat(bmp, origMat);
        Imgproc.cvtColor(origMat, grayMat, Imgproc.COLOR_RGB2GRAY);
        Imgproc.threshold(grayMat, bwMat, 128, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        // Display orig image
        textImage.setImageBitmap(bmp);
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
        return rect1.x <= rect2.x && rect1.x + rect1.width >= rect2.x + rect2.width &&
                rect1.y <= rect2.y && rect1.y + rect1.height >= rect2.y + rect2.height;
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
        double AREA_MAX = (double) (height*width*0.1);
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


    public String encodeImg( Rect roi ) {

        // to see which rect is being encoded
        origMat.copyTo(rgbaMat);
        Imgproc.rectangle(rgbaMat, roi.tl(), roi.br(), new Scalar(0, 255, 0), 2);
//        // FIXME: why??
//        Utils.matToBitmap(rgbaMat, bmp);
//        textImage.setImageBitmap(bmp);


        ////////////////////// first resize ////////////////////////////////////////////
        int size = 32;
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

        ////////////////////// then encode ////////////////////////////////////////////
        String mlbp = mlbp_encode(resizeMat, size, false);
        //Log.d("tag", mlbp);
        return mlbp;
    }

    public String mlbp_encode(Mat img, int size, boolean inv){
        String img_mlbp = "";

        for (int i=1; i<size-1; i++){
            for (int j=1; j<size-1; j++){
                String mlbp = "";
                Mat roi = img.rowRange(i-1, i+2).colRange(j-1, j+2);
                double avg = mat_mean(roi);
                int[] neigh_row_idx = {i-1, i-1, i-1, i,   i+1, i+1, i+1, i};
                int[] neigh_col_idx = {j-1, j,   j+1, j+1, j+1, j,   j-1, j-1};

                for (int k=0; k<neigh_col_idx.length; k++){
                    if (!inv){
                        if (img.get(neigh_row_idx[k], neigh_col_idx[k])[0] > avg) mlbp += "1";
                        else mlbp += "0";
                    }else{
                        if (img.get(neigh_row_idx[k], neigh_col_idx[k])[0] < avg) mlbp += "1";
                        else mlbp += "0";
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


    public void cannyClassify (){

        strong_text = new ArrayList<Rect>();
        weak_text = new ArrayList<Rect>();
        non_text = new ArrayList<Rect>();

        for (int i=0; i<rects.size(); i++){

            String mlbp= encodeImg(rects.get(i));
            CannyClassifier myCanny = new CannyClassifier(mlbp);

            Log.d("tag", "classifying: " + i*100 / rects.size() + "%");
            Log.d("tag", "letter: " + myCanny.letter + "; sim: " + myCanny.sim + "; class: " + myCanny.theclass);

            if (myCanny.theclass == myCanny.STRONG) {
                strong_text.add(rects.get(i));
            } else if (myCanny.theclass == myCanny.WEAK) {
                weak_text.add(rects.get(i));
            } else {
                non_text.add(rects.get(i));
            }
        }

        Log.d("tag", "# of strong: " + strong_text.size());
        Log.d("tag", "# of weak: " + weak_text.size());

        // mark strong text with green boxes, weak text with red boxes
        origMat.copyTo(rgbaMat);
        for (int i=0; i<strong_text.size(); i++)
            Imgproc.rectangle(rgbaMat, strong_text.get(i).tl(), strong_text.get(i).br(), new Scalar(0, 255, 0), 2);
        for (int i=0; i<weak_text.size(); i++)
            Imgproc.rectangle(rgbaMat, weak_text.get(i).tl(), weak_text.get(i).br(), new Scalar(255, 0, 0), 2);
        for (int i=0; i<non_text.size(); i++)
            Imgproc.rectangle(rgbaMat, non_text.get(i).tl(), non_text.get(i).br(), new Scalar(0, 0, 255), 2);

    }
    /*
        helper for hysteresis tracking

     */
    public boolean close(Rect a, Rect b) {
        double align_ratio = .2;
        double h_space_ratio = .75;
        double v_space_ratio = .05;
        //dimensions of a

        int xa = height- (a.y+a.height);
        int ya = a.x;

        int wa = a.height;
        int ha = a.width;

        //dimensions of b
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
    /*
        compare weak and strong text
     */
    public void hysteresis_tracking() {

        text = new ArrayList<Rect>();

        // criteria for removing noisy strong text
        int top_bound = (int) ((float)height / 8.0);
        int bottom_bound = (int) ((float)height * 7.0 / 8.0);
        int left_bound = (int) ((float)width / 8.0);
        int right_bound = (int) ((float)width * 7.0 / 8.0);

        for(int i = 0; i < strong_text.size(); i++) {
            text.add(strong_text.get(i));
        }
        while(!strong_text.isEmpty()) {
            for(int i = 0; i < weak_text.size(); i++) {
                if(!text.contains(weak_text.get(i)) && close(weak_text.get(i),strong_text.get(0))) {
                    text.add(weak_text.get(i));
                    strong_text.add(weak_text.get(i));
                }
            }
            strong_text.remove(0);
        }

        Log.d("tag", "text size: " + text.size());

        for (int i=0; i<text.size(); i++){
            // restrict the central part to be the valid text
            if (text.get(i).tl().x < left_bound || text.get(i).tl().y < top_bound ||
                text.get(i).br().x > right_bound || text.get(i).br().y > bottom_bound) {
                text.remove(i);
            }
        }


        origMat.copyTo(rgbaMat);
        for (int i=0; i<text.size(); i++)
            Imgproc.rectangle(rgbaMat, text.get(i).tl(), text.get(i).br(), new Scalar(0, 255, 0), 2);

//        origMat.copyTo(rgbaMat);
//        for (int i=0; i<strong_text.size(); i++)
//            Imgproc.rectangle(rgbaMat, strong_text.get(i).tl(), strong_text.get(i).br(), new Scalar(0, 255, 0), 2);
//        for (int i=0; i<weak_text.size(); i++)
//            Imgproc.rectangle(rgbaMat, weak_text.get(i).tl(), weak_text.get(i).br(), new Scalar(255, 0, 0), 2);

    }

    public int find_merge_idx(Rect a) {

        for(int i = 0; i < text.size(); i++) {
            if(text.get(i) != a && close(a,text.get(i))) {
                return i;
            }
        }

        return -1;
    }


    public void grouping() {

        //sort text
        Collections.sort(text, new Comparator<Rect>() {
            @Override
            public int compare(Rect a, Rect b) {
                //sort by x//which is sorting by y of actual
                int xa = height- (a.y+a.height);
                int ya = a.x;

                int wa = a.height;
                int ha = a.width;

                //dimensions of b
                int xb = height - (b.y+b.height);
                int yb = b.x;

                int wb = b.height;
                int hb = b.width;
//                int xa = a.x;
//                int ya = a.y;
//
//                int wa = a.width;
//                int ha = a.height;
//
//                //dimensions of b
//                int xb = b.x;
//                int yb = b.y;
//
//                int wb = b.width;
//                int hb = b.height;

                //sort by y
                if(ya < yb) {

                    return -1;
                } else if(ya == yb) {
                    //sort by x
                    if(xa < xb) {
                        return -1;
                    } else {
                        return 1;
                    }
                } else {

                    return 1;
                }
            }
        });

        //sorts the text by x first(which is y for actual flipped image)
        for(int i = 0; i < text.size();i++) {
            Log.d("tag", "sorted text: " + text.get(i));
        }
        //Log.d("tag", "space");
        //Log.d("tag", "space");
        int count = 0;

        while(true) {
            count++;
            //Log.d("tag", "infinite loop 1");
            //all possible merges
            while(true) {
                int merge_index = find_merge_idx(text.get(0));
                if(merge_index == -1)
                    break;

                //merging rectangle

                int x = Math.min(text.get(0).x,text.get(merge_index).x);
                int y = Math.min(text.get(0).y,text.get(merge_index).y);
                int w = Math.max(text.get(0).x + text.get(0).width,text.get(merge_index).x+ text.get(merge_index).width) - x;
                int h = Math.max(text.get(0).y +text.get(0).height,text.get(merge_index).y+ text.get(merge_index).height) - y;

                Log.d("tag", " initial index "+ text.get(0));
                Log.d("tag", " merge index "+ text.get(merge_index));

                Log.d("tag", " x "+ text.get(0).x);
                Log.d("tag", " y "+ text.get(0).y);
                Log.d("tag", " m_x "+ text.get(merge_index).x);
                Log.d("tag", " m_y "+ text.get(merge_index).y);

                Log.d("tag", " min_x "+ x);
                Log.d("tag", " min_y "+ y);

                text.set(0,new Rect(x,y,w,h));
                Log.d("tag", " new rect "+ text.get(merge_index));
                //Log.d("tag", " prior to text size change: "+ text.size());
                text.remove(merge_index);
                //Log.d("tag", "text size change: "+ text.size());
                //Log.d("tag", "infinite loop 2");
            }
            //move element to the end so we can compare the next element
            //Rect temp = text.get(0);
            text.add(text.get(0));
            text.remove(0);
            int stop = 1;
            //Log.d("tag", "check for stop");
            for(int i = 0; i < text.size();i++) {
                if(find_merge_idx(text.get(i)) != -1) {
                    stop = 0;
                    break;
                }
            }
            if(stop==1)
                break;
            //Log.d("tag", "stop failed, text size: " + text.size());
        }
        Log.d("tag", "final grouped text size: " + text.size());
        origMat.copyTo(rgbaMat);
        for (int i=0; i<text.size(); i++)
            Imgproc.rectangle(rgbaMat, text.get(i).tl(), text.get(i).br(), new Scalar(0, 255, 0), 2);

    }




}