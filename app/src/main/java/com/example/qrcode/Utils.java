package com.example.qrcode;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by zhyao on 18-3-4.
 */

public class Utils {
    public static void saveToSDCard(Context context,int id,String name) throws Exception{
        InputStream inStream = context.getResources().openRawResource(id);
        File file = new File(Environment.getExternalStorageDirectory(), name);
        FileOutputStream fileOutputStream = new FileOutputStream(file);//存入SDCard
        byte[] buffer = new byte[10];
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        int len = 0;
        while((len = inStream.read(buffer)) != -1) {
            outStream.write(buffer, 0, len);
        }
        byte[] bs = outStream.toByteArray();
        fileOutputStream.write(bs);
        outStream.close();
        inStream.close();
        fileOutputStream.flush();
        fileOutputStream.close();
    }

    public static String saveBitmap(String bitName,Bitmap mBitmap) {
        File file = new File(Environment.getExternalStorageDirectory(),bitName + ".jpg");

//        try {
//            f.createNewFile();
//        } catch (IOException e) {
//            Log.d("SAVEFILE","在保存图片时出错：" + e.toString());
//        }
        FileOutputStream fOut = null;
        try {
            fOut = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
        } catch (Exception e) {
            return "create_bitmap_error";
        }
        try {
            fOut.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            fOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return bitName + ".jpg";
    }
}
