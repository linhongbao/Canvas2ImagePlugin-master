package org.devgeeks.Canvas2ImagePlugin;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Hashtable;

/**
 * Canvas2ImagePlugin.java
 *
 * Android implementation of the Canvas2ImagePlugin for iOS.
 * Inspirated by Joseph's "Save HTML5 Canvas Image to Gallery" plugin
 * http://jbkflex.wordpress.com/2013/06/19/save-html5-canvas-image-to-gallery-phonegap-android-plugin/
 *
 * @author Vegard Løkken <vegard@headspin.no>
 */
public class Canvas2ImagePlugin extends CordovaPlugin {

	public static final String ACTION = "saveImageDataToLibrary";


	public CallbackContext currentCallbackContext;

	private static final int MISSING_BASESTR = 1;
	private static final int IMAGE_CLOUD_DECODE = 2;
	private static final int ERROR_SAVING_IMAGE = 3;
	private static final int SUCCESS_SAVING_IMAGE = 4;

	public boolean executeOld(String action, JSONArray data,
						   CallbackContext callbackContext) throws JSONException {

		if (action.equals(ACTION)) {

			String base64 = data.optString(0);
			if (base64.equals("")) // isEmpty() requires API level 9
				callbackContext.error("Missing base64 string");

			// Create the bitmap from the base64 string
			Log.d("Canvas2ImagePlugin", base64);
			byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
			Bitmap bmp = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
			if (bmp == null) {
				callbackContext.error("The image could not be decoded");
			} else {

				// Save the image
				File imageFile = savePhoto(bmp);
				if (imageFile == null)
					callbackContext.error("Error while saving image");

				// Update image gallery
				scanPhoto(imageFile);

				callbackContext.success(imageFile.toString());
			}

			return true;
		} else {
			return false;
		}
	}

	@SuppressLint("HandlerLeak")
	private Handler mHandler = new Handler() {
		@SuppressWarnings("unused")
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case MISSING_BASESTR: {
					currentCallbackContext.error("Missing base64 string");
				}
				case IMAGE_CLOUD_DECODE: {
					currentCallbackContext.error("The image could not be decoded");
				}
				case ERROR_SAVING_IMAGE: {
					currentCallbackContext.error("Error while saving image");
				}
				case SUCCESS_SAVING_IMAGE: {
					currentCallbackContext.success(msg.obj.toString());
				}
			}
		}
	};

	public void save(String base64){
		byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
		Bitmap bmp = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
		if (bmp == null) {
			currentCallbackContext.error("The image could not be decoded");
		} else {
			File imageFile = savePhoto(bmp);
			if (imageFile == null) {
				currentCallbackContext.error("Error while saving image");
			}else{
				scanPhoto(imageFile);
				currentCallbackContext.success(imageFile.toString());
			}
		}
	}

	//这个是没有走js插件的接口条用的
	//参数里只有一个照片的数据
	@Override public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {

		currentCallbackContext = callbackContext;
		String base64 = data.optString(0);
		if (action.equals(ACTION)) {
			save(base64);
		}
		return true;
	}

	public boolean executeT(String action, final JSONArray data,
			CallbackContext callbackContext) throws JSONException {
		currentCallbackContext = callbackContext;

		final JSONArray pdata = data;
		if (action.equals(ACTION)) {
			Runnable payRunnable = new Runnable() {
				@Override
				public void run() {
					String base64 = pdata.optString(0);
					if (base64.equals("")){
						Message msg = new Message();
						msg.what = MISSING_BASESTR;
						mHandler.sendMessage(msg);
					}
					// Create the bitmap from the base64 string
					//Log.d("Canvas2ImagePlugin", base64);
					byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
					Bitmap bmp = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
					if (bmp == null) {
						Message msg = new Message();
						msg.what = IMAGE_CLOUD_DECODE;
						mHandler.sendMessage(msg);
					} else {
						// Save the image
						File imageFile = savePhoto(bmp);
						if (imageFile == null) {
							Message msg = new Message();
							msg.what = ERROR_SAVING_IMAGE;
							mHandler.sendMessage(msg);
						}else {
							//Update image gallery
							scanPhoto(imageFile);
							Message msg = new Message();
							msg.what = SUCCESS_SAVING_IMAGE;
							msg.obj = imageFile.toString();
							mHandler.sendMessage(msg);
						}

					}

				}
			};
			Thread payThread = new Thread(payRunnable);
			payThread.start();
		}
		return true;

	}

	/***
	 * Android 7.0开始需要在应用管理里面设置权限,这个会在页面提示处要获取权限的
	 * @param activity
	 * @return
	 */
	 public  boolean isGrantExternalRW() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && cordova.getActivity().checkSelfPermission(
				Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			cordova.getActivity().requestPermissions(new String[]{
					Manifest.permission.READ_EXTERNAL_STORAGE,
					Manifest.permission.WRITE_EXTERNAL_STORAGE
			}, 1);

			return false;
		}

		return true;
	}

	private File savePhoto(Bitmap bmp) {
		File retVal = null;
		
		try {
			Calendar c = Calendar.getInstance();
			String date = "" +
					+ c.get(Calendar.YEAR)
					+ c.get(Calendar.MONTH)
					+ c.get(Calendar.DAY_OF_MONTH)
					+ c.get(Calendar.HOUR_OF_DAY)
					+ c.get(Calendar.MINUTE)
					+ c.get(Calendar.SECOND)
					+ c.get(Calendar.MILLISECOND);

			String deviceVersion = Build.VERSION.RELEASE;
			Log.i("Canvas2ImagePlugin", "文件名" + date.toString());
			Log.i("Canvas2ImagePlugin", "Android version " + deviceVersion);
			int check = deviceVersion.compareTo("2.3.3");

			File folder;
			/*
			 * File path = Environment.getExternalStoragePublicDirectory(
			 * Environment.DIRECTORY_PICTURES ); //this throws error in Android
			 * 2.2
			 */
			if (check >= 1) {
				folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
				
				if(!folder.exists()) {
					folder.mkdirs();
				}
			} else {
				folder = Environment.getExternalStorageDirectory();
			}

			/***
			 * Andorid 7.0 以上,要权限
			 */
			isGrantExternalRW();

			File imageFile = new File(folder, "iyb_" + date.toString() + ".png");

			boolean result = imageFile.createNewFile();

			if(result) {
				FileOutputStream out = new FileOutputStream(imageFile);
				bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
				out.flush();
				out.close();
				retVal = imageFile;
			}
		} catch (Exception e) {
			Log.e("Canvas2ImagePlugin", "An exception occured while saving image: " + e.toString());
			e.printStackTrace();
		}
		return retVal;
	}
	
	/* Invoke the system's media scanner to add your photo to the Media Provider's database, 
	 * making it available in the Android Gallery application and to other apps. */
	private void scanPhoto(File imageFile)
	{
		Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
	    Uri contentUri = Uri.fromFile(imageFile);
	    mediaScanIntent.setData(contentUri);	      		  
	    cordova.getActivity().sendBroadcast(mediaScanIntent);
	} 
}
