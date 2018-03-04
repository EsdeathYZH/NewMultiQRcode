package com.dtr.zxing.utils;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import com.dtr.zxing.activity.CaptureActivity;
import com.dtr.zxing.activity.MyCaptureActivity;
import com.dtr.zxing.camera.CameraManager;
import com.dtr.zxing.decode.DecodeThread;
import com.dtr.zxing.decode.MyDecodeThread;
import com.example.qrcode.R;
import com.google.zxing.Result;

/**
 * This class handles all the messaging which comprises the state machine for
 * capture.
 * 
 * @author YZH
 */
public class MyCaptureActivityHandler extends Handler {

	private final MyCaptureActivity activity;
	private final MyDecodeThread decodeThread;
	private final CameraManager cameraManager;
	private State state;

	private enum State {
		PREVIEW, SUCCESS, DONE ,ADJUST
	}

	public MyCaptureActivityHandler(MyCaptureActivity activity, CameraManager cameraManager, int decodeMode) {
		this.activity = activity;
		decodeThread = new MyDecodeThread(activity, decodeMode);
		decodeThread.start();
		state = State.SUCCESS;

		// Start ourselves capturing previews and decoding.
		this.cameraManager = cameraManager;
		cameraManager.startPreview();
		restartPreviewAndDecode();
	}

	@Override
	public void handleMessage(Message message) {
		switch (message.what) {
		case R.id.restart_preview:
			restartPreviewAndDecode();
			break;
		case R.id.decode_succeeded:
			state = State.SUCCESS;
			activity.handleDecode();
			startAdjust();
			break;
		case R.id.adjust_succeeded:
			state = State.ADJUST;
			activity.handleAdjust();
			startAdjust();
			break;
		case R.id.decode_failed:
			// We're decoding as fast as possible, so when one decode fails,
			// start another.
			state = State.SUCCESS;
			cameraManager.requestPreviewFrame(decodeThread.getHandler(), R.id.decode);
			break;
		}
	}

	public void quitSynchronously() {
		state = State.DONE;
		cameraManager.stopPreview();
		Message quit = Message.obtain(decodeThread.getHandler(), R.id.quit);
		quit.sendToTarget();
		try {
			// Wait at most half a second; should be enough time, and onPause()
			// will timeout quickly
			decodeThread.join(500L);
		} catch (InterruptedException e) {
			// continue
		}
		// Be absolutely sure we don't send any queued up messages
		removeMessages(R.id.adjust_succeeded);
		removeMessages(R.id.decode_succeeded);
		removeMessages(R.id.decode_failed);
	}

	private void restartPreviewAndDecode() {
		if (state == State.SUCCESS) {
			state = State.PREVIEW;
			cameraManager.requestPreviewFrame(decodeThread.getHandler(), R.id.decode);
		}
	}

	private void startAdjust(){
		if (state == State.ADJUST) {
			state = State.PREVIEW;
			cameraManager.requestPreviewFrame(decodeThread.getHandler(), R.id.adjust);
		}
	}

}
