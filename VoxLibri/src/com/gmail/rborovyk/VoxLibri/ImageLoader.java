package com.gmail.rborovyk.VoxLibri;

import java.util.TreeMap;

import com.gmail.rborovyk.content.QueueLoader;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class ImageLoader extends QueueLoader<ImageLoader.Data, ImageLoader.Data> {
	//private static final String LOGTAG = "ImageLoader";
	private int mWidth;
	private int mHeight;
	private float mAspect;
	private boolean mScale;
	private boolean mCacheEnabled;
	private TreeMap<String, Bitmap> mBitmapCache = new TreeMap<String, Bitmap>();
	
	public class Data {
		String path;
		Object data;
		Bitmap bitmap;
		
		Data(String path, Object data) {
			this.path = path; this.data = data;
		}
	}
	
	public ImageLoader(Context context, String threadName) {
		super(context, threadName);
	}
	
	public void enableCache(boolean enable) {mCacheEnabled = enable;}
	
	public void loadImage(String path, Object data) {
		if(null == path)
			return;
		
		Data imgData = new Data(path, data);
		
		if(mCacheEnabled){
			synchronized(mBitmapCache) {
				imgData.bitmap = mBitmapCache.get(path);
			}
			if(null != imgData.bitmap){
				deliverResult(imgData);
				return;
			}
    	}
		
		putRequest(imgData);
	}
	
	public void setDimensions(int width, int height) {
		if(width > 0 && height > 0){
			mWidth = width;
			mHeight = height;
			mAspect = (float)mWidth / mHeight;
			mScale = true;
		}else{
			mScale = false;
		}
	}
	
	@Override
	protected Data processRequest(Data data) {
		//Check if previous requests loaded the image 
		if(mCacheEnabled){
			data.bitmap = mBitmapCache.get(data.path);
		}
		
		if(null == data.bitmap) {
			data.bitmap = BitmapFactory.decodeFile(data.path);
			if(mScale && null != data.bitmap){
				int width = mWidth;
				int height = mHeight;

				float srcAspect = (float)data.bitmap.getWidth() / data.bitmap.getHeight();
				if(mAspect > srcAspect) {
					width = (int)(height * srcAspect);
				}else{
					height = (int)(width / srcAspect);
				}

				Bitmap tmp = Bitmap.createScaledBitmap(data.bitmap, width, height, true);
				data.bitmap.recycle();
				data.bitmap = tmp;
			}
		}
		
		if(mCacheEnabled && null != data.bitmap)
			synchronized(mBitmapCache) {
				mBitmapCache.put(data.path, data.bitmap);
			}
		
		return data;
	}
}
