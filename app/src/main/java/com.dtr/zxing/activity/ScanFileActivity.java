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
import android.widget.ImageView;
import android.widget.Toast;

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
import java.nio.ByteBuffer;
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
    private ImageView backPreview;
    private SurfaceView drawPreview;
    private Bitmap imageFile;
    private final MultiFormatReader multiFormatReader=new MultiFormatReader();
    private ArrayList<PlanarYUVLuminanceSource> planarYUVLuminanceSources;
    private ArrayList<Result> rawResults;
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
        //drawPreview=(SurfaceView)findViewById(R.id.draw_preview_file);
        backPreview=(ImageView)findViewById(R.id.code_background);
        //drawPreview.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize=4;
        imageFile= BitmapFactory.decodeResource(getResources(), R.drawable.qrcode4,options).copy(Bitmap.Config.ARGB_8888, true);;
        //Bitmap scaleImage=Bitmap.createScaledBitmap(imageFile,(int)(imageFile.getWidth()*0.4),(int)(imageFile.getHeight()*0.4),true);

        Toast.makeText(this,"Width:"+imageFile.getWidth()
                +",Height:"+imageFile.getHeight(),Toast.LENGTH_SHORT).show();
        initHint();
        multiFormatReader.setHints(hints);
        xmlFile= getResources().openRawResource(R.raw.bboxinfo4);
        try {
            initBoxes();
        }catch(Exception e){
            e.printStackTrace();
        }
        //drawImage();
        drawRects();
        backPreview.setImageBitmap(imageFile);
        decode();
        //drawResults();
    }
    @Override
    protected void onResume(){
        super.onResume();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!isHasSurface) {
            isHasSurface = true;
        }
        if (isHasSurface) {
            // The activity was paused but not stopped, so the surface still
            // exists. Therefore
            // surfaceCreated() won't be called, so init the camera here.
            //initCamera(scanPreview.getHolder());
        } else {
            // Install the callback and wait for surfaceCreated() to init the
            // camera.
            //drawPreview.getHolder().addCallback(this);
            //drawPreview.getHolder().setFormat(PixelFormat.TRANSLUCENT);//My code
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
        boxes=new ArrayList<Box>();
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
                    if ("data".equals(pullParser.getName())){
                        break;
                    }else if("_".equals(pullParser.getName())){
                        break;
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
                    if ("_".equals(pullParser.getName())){
                        boxes.add(box);
                        box=new Box();
                    }else if("data".equals(pullParser.getName())){
                        return;
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
        imageFile.compress(Bitmap.CompressFormat.JPEG, 100, baos);

        int bytes = imageFile.getByteCount();
        ByteBuffer buf = ByteBuffer.allocate(bytes);
        imageFile.copyPixelsToBuffer(buf);

        rawResults=new ArrayList<Result>();
        Toast.makeText(this,"Width:"+size.getWidth()
                +",Height:"+size.getHeight(),Toast.LENGTH_SHORT).show();
        Toast.makeText(this,"Length should be:"+imageFile.getByteCount()+"Length:"+buf.array().length,Toast.LENGTH_SHORT).show();

        //buildLuminanceSource(baos.toByteArray(), size.getWidth(), size.getHeight());
        buildLuminanceSource(buf.array(), size.getWidth(), size.getHeight());

        for(int i=0;i<boxes.size();i++){
            //BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(planarYUVLuminanceSources.get(i)));
            /*try {
                Result result=multiFormatReader.decodeWithState(bitmap);
                rawResults.add(result);
            } catch (ReaderException re) {
                // continue
            } finally {
                multiFormatReader.reset();
            }*/
        }
    }

    private void drawResults(){
        Canvas canvas=null;
        //SurfaceHolder holder=drawPreview.getHolder();
        try{
            //synchronized (holder){
                canvas=new Canvas (imageFile) ;
                //canvas=holder.lockCanvas();
                Paint p = new Paint(); //创建画笔
                p.setColor(Color.BLUE);
                p.setTextSize(28);
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);//绘制透明色
                for(int i=0;i<boxes.size();i++){
                    canvas.drawText(rawResults.get(i).getText(),boxes.get(i).x, boxes.get(i).y, p);//画出结果
                }

            //}
        }catch(Exception e){
            e.printStackTrace();
        }finally {
            if(canvas!=null){
                //holder.unlockCanvasAndPost(canvas);
            }
        }
    }
    private void drawRects(){
        Canvas canvas=null;
        //SurfaceHolder holder=drawPreview.getHolder();
        try{
            //synchronized (holder){
                canvas=new Canvas (imageFile) ;
                //canvas=holder.lockCanvas();
                Paint p = new Paint(); //创建画笔
                p.setColor(Color.RED);
                p.setTextSize(28);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3);
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);//绘制透明色
                for(int i=0;i<boxes.size();i++){
                    canvas.drawRect(new Rect((int)(boxes.get(i).x*15/8),(int)(boxes.get(i).y*15/8),
                            (int)((boxes.get(i).x+boxes.get(i).width)*15/8),
                            (int)((boxes.get(i).y+boxes.get(i).width)*15/8)),p);//画出结果
                }

            //}
        }catch(Exception e){
            e.printStackTrace();
        }finally {
            if(canvas!=null){
                //holder.unlockCanvasAndPost(canvas);
            }
        }
    }
    private void buildLuminanceSource(byte[] data, int width, int height) {
        planarYUVLuminanceSources=new ArrayList<PlanarYUVLuminanceSource>();
        for(int i=0;i<boxes.size();i++){
            planarYUVLuminanceSources.add(new PlanarYUVLuminanceSource(data, width, height, boxes.get(i).x*15/8, boxes.get(i).y*15/8,
                    boxes.get(i).width*15/8,boxes.get(i).width*15/8, false));
        }
    }

    /*private void drawImage(){
        Canvas canvas=null;
        SurfaceHolder holder=drawPreview.getHolder();
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
    }*/
}


