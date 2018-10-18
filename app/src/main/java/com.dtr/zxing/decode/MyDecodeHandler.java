package com.dtr.zxing.decode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.dtr.zxing.activity.MyCaptureActivity;
import com.example.qrcode.Bbox;
import com.example.qrcode.QrRegion;
import com.example.qrcode.R;
import com.dtr.zxing.activity.CaptureActivity;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.ximgproc.GraphSegmentation;
import org.opencv.ximgproc.Ximgproc;

public class MyDecodeHandler extends Handler {

	private final MyCaptureActivity activity;
	private final MultiFormatReader multiFormatReader;
	private boolean running = true;

	public MyDecodeHandler(MyCaptureActivity activity, Map<DecodeHintType, Object> hints) {
		multiFormatReader = new MultiFormatReader();
		multiFormatReader.setHints(hints);
		this.activity = activity;
	}

	@Override
	public void handleMessage(Message message) {
		if (!running) {
			return;
		}
		switch (message.what) {
			case R.id.decode:
				decode((byte[]) message.obj, message.arg1, message.arg2);
				break;
			case R.id.adjust:
				adjust((byte[]) message.obj, message.arg1, message.arg2);
				break;
			case R.id.quit:
				running = false;
				Looper.myLooper().quit();
				break;
		}
	}

	private void adjust(byte[] data,int width,int height){

		/*
		In this part,we must have get the positions of bounding boxes,
		What we will do next is to adjust these position,
		and if we discover any exception,we 'll restart the algorithm of decode.
		 */

		Handler handler = activity.getHandler();
		Message message = Message.obtain(handler, R.id.adjust_succeeded);
		message.sendToTarget();
	}
	/**
	 * Decode the data within the viewfinder rectangle, and time how long it
	 * took. For efficiency, reuse the same reader objects from one decode to
	 * the next.
	 *
	 * @param data
	 *            The YUV preview frame.
	 * @param width
	 *            The width of the preview frame.
	 * @param height
	 *            The height of the preview frame.
	 */
	private void decode(byte[] data, int width, int height) {
		Size size = activity.getCameraManager().getPreviewSize();

		YuvImage yuvImage=new YuvImage(data, ImageFormat.YUY2,size.width,size.height,null);


		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
		if(!yuvImage.compressToJpeg(new Rect(0,0,width,height),100,outputStream)){
			return;
		}
		byte[] tmp = outputStream.toByteArray();
		Bitmap image = BitmapFactory.decodeByteArray(tmp,0,tmp.length);
		Mat image_mat = new Mat(width,height, CvType.CV_8UC4);
		Utils.bitmapToMat(image,image_mat);

		/*

		In this part,we change data image to LBP image,and find the initial bounding boxes;
		After find bboxes,we store them in MyCaptureActivity.

		 */
		Mat seg = new Mat();
		int []min_sizes=new int[]{1250,1750,2250,2750,3250};

		GraphSegmentation graph_seg= Ximgproc.createGraphSegmentation();
		if(graph_seg!=null){
			graph_seg.setSigma(0.8);
			graph_seg.setK(2);
			graph_seg.setMinSize(min_sizes[0]);
			graph_seg.processImage(image_mat,seg);
			Core.MinMaxLocResult minMaxLocResult=Core.minMaxLoc(seg);
			int nb_segs=(int)minMaxLocResult.maxVal+1;

//			QrRegion qrRegion=new QrRegion(activity,image_mat,seg,nb_segs);
//			qrRegion.process();
//			qrRegion.get_bbox();
		}

		Result rawResult = null;
		RGBLuminanceSource[] sources = buildLuminanceSource(image, size.width, size.height);
		if (sources != null) {
			for(int i=0;i<sources.length;i++){
				if(sources[i]==null){
					activity.addResult("");
					continue;
				}
				BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(sources[i]));
				try {
					rawResult = multiFormatReader.decodeWithState(bitmap);
					activity.addResult(rawResult.getText());
				} catch (ReaderException re) {
					activity.addResult("Failed!");
				} finally {
					multiFormatReader.reset();
				}
			}
		}

		Handler handler = activity.getHandler();
		if (rawResult != null) {
			// Don't log the barcode contents for security.
			if (handler != null) {
				Message message = Message.obtain(handler, R.id.decode_succeeded);
				message.sendToTarget();
			}
		} else {
			if (handler != null) {
				Message message = Message.obtain(handler, R.id.decode_failed);
				message.sendToTarget();
			}
		}

	}

	/**
	 * A factory method to build the appropriate LuminanceSource object based on
	 * the format of the preview buffers, as described by Camera.Parameters.
	 *
	 * @param image
	 *            A preview frame.
	 * @param width
	 *            The width of the image.
	 * @param height
	 *            The height of the image.
	 * @return Some RGBLuminanceSource instances.
	 */
	public RGBLuminanceSource[] buildLuminanceSource(Bitmap image, int width, int height) {
		ArrayList<Rect> bboxes = activity.getBoundingbox();
		if (bboxes == null) {
			return null;
		}
		int[] pixels = new int[width * height];
		image.getPixels(pixels, 0, width, 0, 0, width, height);
		RGBLuminanceSource[] sources=new RGBLuminanceSource[bboxes.size()];

		for(int i=0;i<bboxes.size();i++){
			try {
				RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
				sources[i] = (RGBLuminanceSource)source.crop(bboxes.get(i).left, bboxes.get(i).top,
						bboxes.get(i).width(), bboxes.get(i).width());
			}catch (Exception e){
				sources[i]=null;
			}
		}
		return sources;
	}

}
