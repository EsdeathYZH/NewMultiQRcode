package com.example.qrcode;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import com.dtr.zxing.activity.CaptureActivity;
import com.dtr.zxing.activity.ScanFileActivity;

import static android.content.ContentValues.TAG;

/**
 * Created by SHIYONG on 2017/10/22.
 */

public class MainActivity extends Activity {
    Button btnProcess;
    Bitmap srcBitmap;
    Bitmap grayBitmap;
    ImageView imgHuaishi;
    private Button start_scan;
    private Button file_scan;
    private static boolean flag = true;
    private static final String TAG = "MainActivity";

    //OpenCV库加载并初始化成功后的回调函数
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {

        @Override
        public void onManagerConnected(int status) {
            // TODO Auto-generated method stub
            switch (status){
                case BaseLoaderCallback.SUCCESS:
                    Log.i(TAG, "成功加载");
                    //Toast.makeText(MainActivity.this,"成功加载",Toast.LENGTH_SHORT).show();
                    break;
                default:
                    super.onManagerConnected(status);
                    Log.i(TAG, "加载失败");
                    //Toast.makeText(MainActivity.this,"加载失败",Toast.LENGTH_SHORT).show();
                    break;
            }

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        start_scan=(Button)findViewById(R.id.startscan);
        file_scan=(Button) findViewById(R.id.scan_file);
        btnProcess = (Button)findViewById(R.id.btn_gray_process);
        imgHuaishi = (ImageView)findViewById(R.id.img_huaishi);
        btnProcess.setOnClickListener(new ProcessClickListener());
        file_scan.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                Intent intent=new Intent(MainActivity.this, ScanFileActivity.class);
                startActivity(intent);
            }
        });
        start_scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view){
                Intent intent=new Intent(MainActivity.this, CaptureActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void procSrc2Gray(){
        Mat rgbMat = new Mat();
        Mat grayMat = new Mat();
        srcBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.small_code);
        grayBitmap = Bitmap.createBitmap(srcBitmap.getWidth(), srcBitmap.getHeight(), Bitmap.Config.RGB_565);
        Utils.bitmapToMat(srcBitmap, rgbMat);//convert original bitmap to Mat, R G B.
        Imgproc.cvtColor(rgbMat, grayMat, Imgproc.COLOR_RGB2GRAY);//rgbMat to gray grayMat
        Utils.matToBitmap(grayMat, grayBitmap); //convert mat to bitmap
        Log.i(TAG, "procSrc2Gray sucess...");
    }

    private class ProcessClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            // TODO Auto-generated method stub
//            if(isFirst)
//            {
            procSrc2Gray();
//                isFirst = false;
//            }
            if(flag){
                imgHuaishi.setImageBitmap(grayBitmap);
                btnProcess.setText("查看原图");
                flag = false;
            }
            else{
                imgHuaishi.setImageBitmap(srcBitmap);
                btnProcess.setText("灰度化");
                flag = true;
            }
        }

    }
}
