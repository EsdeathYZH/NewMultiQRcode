package com.example.qrcode;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.widget.Toast;

import com.dtr.zxing.decode.DecodeFormatManager;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.ximgproc.GraphSegmentation;
import org.opencv.ximgproc.Ximgproc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * Created by SHIYONG on 2018/1/25.
 */

public class CameraActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private CameraBridgeViewBase mOpenCvCameraView;

    private ArrayList<Rect> bboxes;
    private Mat[] segs ;
    private int cnt = 0;
    private ArrayList<String> results;
    private ArrayList<LuminanceSource> luminanceSources;

    private MultiFormatReader multiFormatReader;
    private Map<DecodeHintType, Object> hints;

    private int STATE_ADJUST = 0;
    private int STATE_DECODE = 1;
    private int STATE_FAILED = 2;

    private int state = STATE_DECODE;

    private Mat save_mat;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_camera);
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.cv_camera);
        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.enableView();

        bboxes = new ArrayList<Rect>();
        results = new ArrayList<String>();
        luminanceSources = new ArrayList<>();
        multiFormatReader = new MultiFormatReader();
        initHint();
        multiFormatReader.setHints(hints);
//        try{
//            com.example.qrcode.Utils.saveToSDCard(this,R.raw.svc_model,"svm_model.xml");
//        }catch(Exception e){
//            e.printStackTrace();
//        }
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {

    }
    public void onCameraViewStopped() {
        // Explicitly deallocate Mats

    }
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        /*

		In this part,we change data image to LBP image,and find the initial bounding boxes;
		After find bboxes,we store them in MyCaptureActivity.

		 */
        Mat image_mat = inputFrame.rgba();
        Mat gray_mat = inputFrame.gray();
        Bitmap image = Bitmap.createBitmap(image_mat.width(),image_mat.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(image_mat,image);

        if(state == STATE_DECODE) {
            results.clear();
            bboxes.clear();
            luminanceSources.clear();

            Mat seg = new Mat();
            int[] min_sizes = new int[]{1250, 1750, 2250, 2750, 3250};

            GraphSegmentation graph_seg = Ximgproc.createGraphSegmentation();
            if (graph_seg != null) {
                graph_seg.setSigma(0.8);
                graph_seg.setK(2);
                graph_seg.setMinSize(2250);
                graph_seg.processImage(image_mat, seg);
                Core.MinMaxLocResult minMaxLocResult = Core.minMaxLoc(seg);
                int nb_segs = (int) minMaxLocResult.maxVal + 1;

                QrRegion qrRegion = new QrRegion(this, image_mat, seg, nb_segs);
                qrRegion.process();
                cnt = qrRegion.get_bbox();
                segs = qrRegion.get_seg_mat();
                for(int i=0; i<segs.length;i++){
                    Bitmap temp_image = Bitmap.createBitmap(segs[i].cols(),segs[i].rows(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(segs[i],temp_image);
                    com.example.qrcode.Utils.saveBitmap("seg"+i,temp_image);
                }
            }
            buildLuminanceSource(image,image_mat.width(),image_mat.height());
            //if(bboxes.size()!=0) Toast.makeText(this,"Success!",Toast.LENGTH_SHORT).show();
            for (int i = 0; i < bboxes.size(); i++) {
                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(luminanceSources.get(i)));
                try {
                    Result result = multiFormatReader.decode(bitmap);
                    //Toast.makeText(this, result.getText(), Toast.LENGTH_SHORT).show();
                    results.add(result.getText());
                } catch (ReaderException re) {
                    results.add("Failed!");
                    re.printStackTrace();
                } finally {
                    multiFormatReader.reset();
                }
                Imgproc.rectangle(image_mat,
                        new Point(bboxes.get(i).left,bboxes.get(i).top),
                        new Point(bboxes.get(i).right,bboxes.get(i).bottom),
                        new Scalar(0,255,0));
                Imgproc.putText(image_mat,results.get(i),
                        new Point(bboxes.get(i).left,bboxes.get(i).top),
                        3,1,new Scalar(0,255,0));
            }
            save_mat = image_mat;
            //change the state
            if(bboxes.size() != 0){
                state = STATE_ADJUST;
            }else state = STATE_FAILED;

        }else if(state == STATE_ADJUST){
            for (int i = 0; i < bboxes.size(); i++) {
                Imgproc.rectangle(save_mat,
                        new Point(bboxes.get(i).left,bboxes.get(i).top),
                        new Point(bboxes.get(i).right,bboxes.get(i).bottom),
                        new Scalar(0,255,0));
//                Imgproc.putText(image_mat,results.get(i),
//                        new Point(bboxes.get(i).left,bboxes.get(i).top),
//                        3,1,new Scalar(255,255,255));
            }
            Imgproc.putText(image_mat,String.valueOf(cnt),
                    new Point(bboxes.get(0).left,bboxes.get(0).top),
                    3,1,new Scalar(0,255,0));
            return save_mat;
        }else{

        }
        return save_mat;
    }

    private void buildLuminanceSource(Bitmap data, int width, int height) {
        int[] pixels = new int[width * height];
        data.getPixels(pixels, 0, width, 0, 0, width, height);

        RGBLuminanceSource image = new RGBLuminanceSource(width, height, pixels);
        for (int i = 0; i < bboxes.size(); i++) {
            luminanceSources.add(image.crop(bboxes.get(i).left, bboxes.get(i).top,
                    bboxes.get(i).width(), bboxes.get(i).width()));
        }
    }

    private void initHint() {
        hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        Collection<BarcodeFormat> decodeFormats = new ArrayList<BarcodeFormat>();
        decodeFormats.addAll(EnumSet.of(BarcodeFormat.AZTEC));
        decodeFormats.addAll(EnumSet.of(BarcodeFormat.PDF_417));
        decodeFormats.addAll(DecodeFormatManager.getBarCodeFormats());
        decodeFormats.addAll(DecodeFormatManager.getQrCodeFormats());
        hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
    }

    public void addBbox(org.opencv.core.Rect bbox){
        Rect rect=new Rect(bbox.x,bbox.y,bbox.x+bbox.width,bbox.y+bbox.height);
        this.bboxes.add(rect);
    }

    public void clearBbox(){
        this.bboxes.clear();
    }
}
