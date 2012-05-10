package com.gmail.rborovyk.VoxLibri;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.view.KeyEvent;

public class MediaButtonReceiver extends BroadcastReceiver {
	private static final int LONG_PRESS_DELAY = 1000;
	private static final int DOUBLE_CLICK_DELAY = 300;
	
	private boolean mIsDown = false;
	private long mLastClickTime = 0;
	private String mLastCommand = null;
	private long mLastAppliedSeekTime;

	@Override
	public void onReceive(Context context, Intent intent) {
		String command  = null;
		String intentAction = intent.getAction();
		
		if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intentAction)) {
			sendCommand(context, AudioService.CMD_PAUSE);
			
		} else if (Intent.ACTION_MEDIA_BUTTON.equals(intentAction)) {
			KeyEvent event = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);

			command = getCommand(event);
			if (null == command) {
				return;
			}
			
			long eventtime = event.getEventTime();
			if (event.getAction() == KeyEvent.ACTION_DOWN) {
				if (mIsDown) {
					onRepeatingPress(context, event, command);
					
					if (mLastClickTime != 0 && 
							(eventtime - mLastClickTime) > LONG_PRESS_DELAY &&
							command.equals(mLastCommand)) {
						onLongPress(context, event, command);
					}
				} else {
					// initial press event
					mIsDown = true;

					if ((eventtime - mLastClickTime) < DOUBLE_CLICK_DELAY &&
							command.equals(mLastCommand)) {
						onDoubleClick(context, event, command);
					} else {
						onSinglePress(context, event, command);
					}
				}
			} else {
				mIsDown = false;
			}

			if (isOrderedBroadcast()) {
				abortBroadcast();
			}
		}
	}

	private void onSinglePress(Context context, KeyEvent event, String command) {
		mLastClickTime = event.getEventTime();
		mLastCommand = command;
		
		if(AudioService.CMD_FFWD.equals(command) || AudioService.CMD_REWIND.equals(command)) {
			//do nothing
		}else{
			sendCommand(context, command);
		}
	}
	
	private void onDoubleClick(Context context, KeyEvent event, String command) {
		if (AudioService.CMD_PLAYPAUSE.equals(command)) {
			sendCommand(context, AudioService.CMD_NEXT);
			mLastClickTime = 0;
		}
	}
	
	private void onRepeatingPress(Context context, KeyEvent event, String command) {
		long eventTime = event.getEventTime();
		
		if(AudioService.CMD_FFWD.equals(command) || AudioService.CMD_REWIND.equals(command)) {
			long duration = eventTime - mLastClickTime;
			if (duration < 5000) {
				duration = duration * 10; // seek at 10x speed for the first 5 seconds 
	        } else {
	        	duration = 50000 + (duration - 5000) * 40; // seek at 40x after that
	        }
			
			int offset = (int)(duration - mLastAppliedSeekTime);
			if (offset > 250) {
				Intent i = new Intent(context, AudioService.class);
				i.setAction(AudioService.SERVICECMD_ACTION);
				i.putExtra(AudioService.CMD_NAME, command);
				i.putExtra(AudioService.CMD_SEEKOFFSET, Integer.toString(offset));
				context.startService(i);
				
	            mLastAppliedSeekTime = duration;
	        }
		}
	}
	
	private void onLongPress(Context context, KeyEvent event, String command) {
		
	}
	
	private void sendCommand(Context context, String command) {
		if(null == command)
			return;
		
		Intent i = new Intent(context, AudioService.class);
		i.setAction(AudioService.SERVICECMD_ACTION);
		i.putExtra(AudioService.CMD_NAME, command);
		context.startService(i);
	}
	
	private String getCommand(KeyEvent event) {
		if (null == event) {
			return null;
		}

		String command = null;
		switch (event.getKeyCode()) {
		case KeyEvent.KEYCODE_HEADSETHOOK:
		case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
			command = AudioService.CMD_PLAYPAUSE;
			break;
		case KeyEvent.KEYCODE_MEDIA_STOP:
			command = AudioService.CMD_STOP;
			break;
		case KeyEvent.KEYCODE_MEDIA_NEXT:
			command = AudioService.CMD_NEXT;
			break;
		case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
			command = AudioService.CMD_PREV;
			break;
		case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
			command = AudioService.CMD_FFWD;
			break;
		case KeyEvent.KEYCODE_MEDIA_REWIND:
			command = AudioService.CMD_REWIND;
			break;
		}
		return command;
	}
}
