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
import com.example.qrcode.Bbox;
import com.example.qrcode.MainActivity;
import com.example.qrcode.R;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;
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

public class ScanFileActivity extends Activity implements SurfaceHolder.Callback {
    private ArrayList<Bbox> boxes;
    InputStream xmlFile;
    private ImageView backPreview;
    private SurfaceView drawPreview;
    private Bitmap imageFile;
    private final MultiFormatReader multiFormatReader = new MultiFormatReader();
    private ArrayList<LuminanceSource> luminanceSources;
    private ArrayList<Result> rawResults;
    private Map<DecodeHintType, Object> hints;
    private boolean isHasSurface;
    private Paint paint;
    private Canvas canvas;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_scanfile);
        backPreview = (ImageView) findViewById(R.id.code_background);
        paint = new Paint(); //创建画笔

        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4;
        //Bitmap rawFile=BitmapFactory.decodeResource(getResources(), R.drawable.qrcode4);
        imageFile = BitmapFactory.decodeResource(getResources(), R.drawable.qrcode4, options).copy(Bitmap.Config.ARGB_8888, true);;
        canvas=new Canvas(imageFile);

        Toast.makeText(this, "Width:" + imageFile.getWidth()
                + ",Height:" + imageFile.getHeight(), Toast.LENGTH_SHORT).show();

        initHint();
        multiFormatReader.setHints(hints);
        xmlFile = getResources().openRawResource(R.raw.bboxinfo4);
        try {
            initBoxes();
        } catch (Exception e) {
            e.printStackTrace();
        }
        decode();
        drawResults();
        drawRects();
        backPreview.setImageBitmap(imageFile);
    }

    @Override
    protected void onResume() {
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

    private void initHint() {
        hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        Collection<BarcodeFormat> decodeFormats = new ArrayList<BarcodeFormat>();
        decodeFormats.addAll(EnumSet.of(BarcodeFormat.AZTEC));
        decodeFormats.addAll(EnumSet.of(BarcodeFormat.PDF_417));
        decodeFormats.addAll(DecodeFormatManager.getBarCodeFormats());
        decodeFormats.addAll(DecodeFormatManager.getQrCodeFormats());
        hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
    }

    private void initBoxes() throws Exception {
        boxes = new ArrayList<Bbox>();

        Bbox box = new Bbox();
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlPullParser pullParser = factory.newPullParser();
        pullParser.setInput(xmlFile, "UTF-8");
        int eventType = pullParser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlPullParser.START_DOCUMENT:
                    break;
                case XmlPullParser.START_TAG:
                    if ("data".equals(pullParser.getName())) {
                        break;
                    } else if ("_".equals(pullParser.getName())) {
                        break;
                    } else if ("x".equals(pullParser.getName())) {
                        int x = Integer.parseInt(pullParser.nextText());
                        box.x=x;
                    } else if ("y".equals(pullParser.getName())) {
                        int y = Integer.parseInt(pullParser.nextText());
                        box.y=y;
                    } else if ("w".equals(pullParser.getName())) {
                        int width = Integer.parseInt(pullParser.nextText());
                        box.width=width;
                    }
                    break;
                case XmlPullParser.END_TAG:
                    if ("_".equals(pullParser.getName())) {
                        boxes.add(box);
                        box = new Bbox();
                    } else if ("data".equals(pullParser.getName())) {
                        return;
                    }
                    break;
            }
            eventType = pullParser.next();
        }
    }

    private void decode() {
        Size size = new Size(imageFile.getWidth(), imageFile.getHeight());
        //create byte[] from image file.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        imageFile.compress(Bitmap.CompressFormat.JPEG, 100, baos);

        int bytes = imageFile.getByteCount();
        ByteBuffer buf = ByteBuffer.allocate(bytes);
        imageFile.copyPixelsToBuffer(buf);

        rawResults = new ArrayList<Result>();
        //buildLuminanceSource(baos.toByteArray(), size.getWidth(), size.getHeight());
        buildLuminanceSource(buf.array(), size.getWidth(), size.getHeight());

        for (int i = 0; i < boxes.size(); i++) {
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(luminanceSources.get(i)));
            try {
                Result result = multiFormatReader.decode(bitmap);
                //Toast.makeText(this, result.getText(), Toast.LENGTH_SHORT).show();
                rawResults.add(result);
            } catch (ReaderException re) {
                rawResults.add(null);
                re.printStackTrace();
            } finally {
                multiFormatReader.reset();
            }
        }
    }

    private void drawResults() {
        try {
            paint.setColor(Color.WHITE);
            paint.setTextSize(28);
            for (int i = 0; i < boxes.size(); i++) {
                if (rawResults.get(i) != null) {
                    canvas.drawText(rawResults.get(i).getText(), boxes.get(i).y*15/8,
                            boxes.get(i).y*15/8+boxes.get(i).y*15/16, paint);//画出结果
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (canvas != null) {
                //holder.unlockCanvasAndPost(canvas);
            }
        }
    }

    private void drawRects() {
        try {
            paint.setColor(Color.RED);
            paint.setTextSize(28);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            for (int i = 0; i < boxes.size(); i++) {
                canvas.drawRect(new Rect((int) (boxes.get(i).x * 15 / 8), (int) (boxes.get(i).y * 15 / 8),
                        (int) ((boxes.get(i).x + boxes.get(i).width) * 15 / 8),
                        (int) ((boxes.get(i).y + boxes.get(i).width) * 15 / 8)), paint);//画出结果
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (canvas != null) {
                //holder.unlockCanvasAndPost(canvas);
            }
        }
    }

    private void buildLuminanceSource(byte[] data, int width, int height) {
        luminanceSources = new ArrayList<LuminanceSource>();

        int[] pixels = new int[width * height];
        imageFile.getPixels(pixels, 0, width, 0, 0, width, height);

        RGBLuminanceSource image = new RGBLuminanceSource(width, height, pixels);
        for (int i = 0; i < boxes.size(); i++) {
            luminanceSources.add(image.crop(boxes.get(i).x * 15 / 8, boxes.get(i).y * 15 / 8,
                    boxes.get(i).width * 15 / 8, boxes.get(i).width * 15 / 8));
        }
    }
}



