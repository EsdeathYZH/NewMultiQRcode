package com.dtr.zxing.activity;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;

import com.dtr.zxing.decode.DecodeFormatManager;
import com.dtr.zxing.decode.DecodeThread;
import com.example.qrcode.MainActivity;
import com.example.qrcode.R;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * Created by SHIYONG on 2017/11/23.
 */

public class ScanFileActivity extends Activity implements SurfaceHolder.Callback{
    private ArrayList<Box> boxes;
    InputStream xmlFile;
    private SurfaceView backPreview;
    private SurfaceView drawPreview;
    private Bitmap imageFile;
    private final MultiFormatReader multiFormatReader=new MultiFormatReader();
    ArrayList<PlanarYUVLuminanceSource> planarYUVLuminanceSources;
    ArrayList<Result> rawResults;
    Map<DecodeHintType, Object> hints;
    private boolean isHasSurface;
    class Box{
        public int x;
        public int y;
        public int width;

    }
    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_scanfile);
        drawPreview=(SurfaceView)findViewById(R.id.draw_preview_file);
        backPreview=(SurfaceView)findViewById(R.id.code_background);
        backPreview.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        imageFile= BitmapFactory.decodeResource(getResources(), R.drawable.qrcode);
        initHint();
        multiFormatReader.setHints(hints);
        xmlFile= getResources().openRawResource(R.raw.code_info);
        try {
            //initBoxes();
        }catch(Exception e){
            e.printStackTrace();
        }
        drawImage();
        //drawRects();
        /*decode();
        drawResults();*/
    }
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!isHasSurface) {
            isHasSurface = true;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isHasSurface = false;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }
    private void initHint(){
        hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        Collection<BarcodeFormat> decodeFormats = new ArrayList<BarcodeFormat>();
        decodeFormats.addAll(EnumSet.of(BarcodeFormat.AZTEC));
        decodeFormats.addAll(EnumSet.of(BarcodeFormat.PDF_417));
        decodeFormats.addAll(DecodeFormatManager.getBarCodeFormats());
        decodeFormats.addAll(DecodeFormatManager.getQrCodeFormats());
        hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
    }
    private void initBoxes()throws Exception{
        Box box = new Box();
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlPullParser pullParser = factory.newPullParser();
        pullParser.setInput(xmlFile, "UTF-8");
        int eventType = pullParser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT){
            switch (eventType){
                case XmlPullParser.START_DOCUMENT:
                    break;
                case XmlPullParser.START_TAG:
                    if ("box".equals(pullParser.getName())){

                    }else if ("x".equals(pullParser.getName())){
                        int x = Integer.parseInt(pullParser.nextText());
                        box.x=x;
                    }else if ("y".equals(pullParser.getName())){
                        int y = Integer.parseInt(pullParser.nextText());
                        box.y=y;
                    }else if ("w".equals(pullParser.getName())){
                        int width = Integer.parseInt(pullParser.nextText());
                        box.width=width;
                    }
                    break;
                case XmlPullParser.END_TAG:
                    if ("box".equals(pullParser.getName())){
                        boxes.add(box);

                    }
                    break;
            }
            eventType = pullParser.next();
        }
    }

    private void decode(){
        Size size = new Size(imageFile.getWidth(),imageFile.getHeight());
        //create byte[] from image file.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        imageFile.compress(Bitmap.CompressFormat.PNG, 100, baos);


        buildLuminanceSource(baos.toByteArray(), size.getWidth(), size.getHeight());
        for(int i=0;i<boxes.size();i++){
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(planarYUVLuminanceSources.get(i)));
            try {
                Result result=multiFormatReader.decodeWithState(bitmap);
                rawResults.add(result);
            } catch (ReaderException re) {
                // continue
            } finally {
                multiFormatReader.reset();
            }
        }
    }

    private void drawResults(){
        Canvas canvas=null;
        SurfaceHolder holder=drawPreview.getHolder();
        try{
            synchronized (holder){
                canvas=holder.lockCanvas();
                Paint p = new Paint(); //创建画笔
                p.setColor(Color.BLUE);
                p.setTextSize(28);
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);//绘制透明色
                for(int i=0;i<boxes.size();i++){
                    canvas.drawText(rawResults.get(i).getText(),boxes.get(i).x, boxes.get(i).y, p);//画出结果
                }

            }
        }catch(Exception e){
            e.printStackTrace();
        }finally {
            if(canvas!=null){
                holder.unlockCanvasAndPost(canvas);
            }
        }
    }
    private void drawRects(){
        Canvas canvas=null;
        SurfaceHolder holder=drawPreview.getHolder();
        try{
            synchronized (holder){
                canvas=holder.lockCanvas();
                Paint p = new Paint(); //创建画笔
                p.setColor(Color.RED);
                p.setTextSize(28);
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);//绘制透明色
                for(int i=0;i<boxes.size();i++){
                    canvas.drawRect(new Rect(boxes.get(i).x,boxes.get(i).y,
                            boxes.get(i).x+boxes.get(i).width,
                            boxes.get(i).y+boxes.get(i).width),p);//画出结果
                }

            }
        }catch(Exception e){
            e.printStackTrace();
        }finally {
            if(canvas!=null){
                holder.unlockCanvasAndPost(canvas);
            }
        }
    }
    private void buildLuminanceSource(byte[] data, int width, int height) {
        for(int i=0;i<boxes.size();i++){
            planarYUVLuminanceSources.add(new PlanarYUVLuminanceSource(data, width, height, boxes.get(i).x, boxes.get(i).y, boxes.get(i).width,boxes.get(i).width, false));
        }
    }

    private void drawImage(){
        Canvas canvas=null;
        SurfaceHolder holder=backPreview.getHolder();
        try{
            synchronized (holder){
                canvas=holder.lockCanvas();
                Paint p = new Paint(); //创建画笔
                p.setColor(Color.RED);
                p.setTextSize(28);
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);//绘制透明色
                canvas.drawBitmap(imageFile,0,0,p);
            }
        }catch(Exception e){
            e.printStackTrace();
        }finally {
            if(canvas!=null){
                holder.unlockCanvasAndPost(canvas);
            }
        }
    }
}


