package com.gmail.rborovyk.content;


import java.util.LinkedList;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.Loader;

public abstract class QueueLoader<IN_DATA, OUT_DATA> extends Loader<OUT_DATA>
	implements Runnable {
	//private static final String LOGTAG = "QueueLoader";
	
	private String mThreadName;
	private Thread mThread;
	private ResultHandler mResultReceiver;
	private LinkedList<IN_DATA> mQueue = new LinkedList<IN_DATA>();
	
	public QueueLoader(Context context, String threadName) {
		super(context);
		mThreadName = threadName;
		mResultReceiver = new ResultHandler();
	}
	
	public boolean isAlreadyInQueue(IN_DATA data) {
		boolean res =  false;
		synchronized(mQueue) {
			res = mQueue.contains(data);
		}
		return res;
	}
	
	protected void putRequest(IN_DATA data) {
		synchronized(mQueue) {
			mQueue.offer(data);
			mQueue.notifyAll();
		}
	}
	
	//background processing method
	protected abstract OUT_DATA processRequest(IN_DATA data);
	
	//------------------------------------------
	//Loader stuff
	@Override
	protected void onStartLoading() {
		super.onStartLoading();

		if(null == mThread) {
			mThread = new Thread(null, this, mThreadName);
			mThread.setPriority(Thread.MIN_PRIORITY);
			mThread.start();
		}
	}

	@Override
	protected void onStopLoading() {
		if(null != mThread) {
			mThread.interrupt();
			mThread = null;
		}
		super.onStopLoading();
	}

	@Override
	protected void onForceLoad() {
		super.onForceLoad();
	}

	@Override
	protected void onReset() {
		super.onReset();
		onStopLoading();
	}

	public void run() {
		try {
			while(true) {
				IN_DATA inData;
				synchronized(mQueue) {
					if(mQueue.isEmpty())
						mQueue.wait();
					inData = mQueue.poll(); 
				}
				
				if(null != inData){
					OUT_DATA outData = processRequest(inData);
					mResultReceiver.obtainMessage(0, outData).sendToTarget();
				}
				
				if(Thread.interrupted())
					break;
			}
		}catch(InterruptedException ex) {}
    }
	
	private class ResultHandler extends Handler {
		@Override
        public void handleMessage(Message msg) {
			@SuppressWarnings("unchecked")
			OUT_DATA data = (OUT_DATA) msg.obj;
			deliverResult(data);
        }
	}
}
