package com.example.qrcode;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;

import com.dtr.zxing.activity.CaptureActivity;
import com.dtr.zxing.activity.ScanFileActivity;

/**
 * Created by SHIYONG on 2017/10/22.
 */

public class MainActivity extends Activity {
    private Button start_scan;
    private Button file_scan;
    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        start_scan=(Button)findViewById(R.id.startscan);
        file_scan=(Button) findViewById(R.id.scan_file);
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
}
