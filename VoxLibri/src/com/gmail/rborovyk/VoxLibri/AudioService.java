package com.gmail.rborovyk.VoxLibri;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;

import com.gmail.rborovyk.ui.R;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.widget.RemoteViews;
import android.widget.Toast;

public class AudioService extends Service {
	//----CONSTANTS----------------------------------------------------------------------------
	private static final String LOGTAG = "AudioService";
	private static final int PLAYER_RESTART_TIMEOUT = 2000; //timeout to restart media server if died
	private static final int IDLE_TIME = 60000;
	private static final int STEP_BACK_AFTER_PAUSE = 2000;
	private static final int SEEK_NOT_REQUIRED = -1;
	private static final float LOW_VOLUME_LEVEL = 0.2f;
	private static final float FULL_VOLUME_LEVEL = 1.0f;
	private static final float VOLUME_FADE_STEP = 0.05f;
	private static final long VOLUME_FADE_STEP_DELAY = 10;
	private static final int PENDING_NOTIFICATION_ID = 1;
	private static final String PREFERENCES_FILE = "settings";
	
	//----Media Player internal messages--------------------------
	private static final int MSG_PLAYER_READY = 0;
    private static final int MSG_TRACK_ENDED = 1;
    private static final int MSG_AUDIO_FOCUS_CHANGED = 2;
    private static final int MSG_MEDIA_SERVER_DIED = 3;
    private static final int MSG_RELEASE_WAKELOCK = 4;
    private static final int MSG_FADE_VOLUME_DOWN = 5;
    private static final int MSG_FADE_VOLUME_UP = 6;
    private static final int MSG_DELAYED_EXIT = 7;
    
    //----Media Player commands--------------------------
    public static final String SERVICECMD_ACTION = "com.gmail.rborovyk.voxlibri.servicecmd";
    public static final String CMD_NAME = "com.gmail.rborovyk.voxlibri.cmdname";
    public static final String CMD_SEEKOFFSET = "com.gmail.rborovyk.voxlibri.seek"; //int
    public static final String CMD_PAUSE = "pause";
    public static final String CMD_STOP = "stop";
    public static final String CMD_PLAYPAUSE = "playpause";
    public static final String CMD_NEXT = "next";
    public static final String CMD_PREV = "prev";
    public static final String CMD_FFWD = "ffwd";
    public static final String CMD_REWIND = "rewind";
    
    public static final String PLAYSTATE_CHANGED_ACTION = "com.gmail.rborovyk.voxlibri.playstatechanged";
    public static final String META_CHANGED_ACTION = "com.gmail.rborovyk.voxlibri.metachanged";
    public static final String PLAY_STATE_EXTRA = "com.gmail.rborovyk.voxlibri.playstate";
    public static final String BOOK_ID_EXTRA = "com.gmail.rborovyk.voxlibri.bookid";
    public static final String BOOK_TITLE_EXTRA = "com.gmail.rborovyk.voxlibri.booktitle";
    public static final String BOOK_COVER_EXTRA = "com.gmail.rborovyk.voxlibri.bookcover";
    public static final String CHAPTER_ID_EXTRA = "com.gmail.rborovyk.voxlibri.chapterid";
    public static final String CHAPTER_TITLE_EXTRA = "com.gmail.rborovyk.voxlibri.chaptertitle";
    public static final String CHAPTER_PROGRESS_EXTRA = "com.gmail.rborovyk.voxlibri.chapterprogress";
    public static final String CHAPTER_DURATION_EXTRA = "com.gmail.rborovyk.voxlibri.duration";
    public static final String CHAPTERS_COUNT_EXTRA = "com.gmail.rborovyk.voxlibri.chapterscnt";
    public static final String CHAPTER_INDEX_EXTRA = "com.gmail.rborovyk.voxlibri.chapteridx";
	    
    //---Player state---------------------------------
    private static final int PLAYER_IDLE = 0;
    private static final int PLAYER_INITIALIZING = 1;
    private static final int PLAYER_INITIALIZED = 2;
	
	//----FIELDS-------------------------------------------------------------------------------
    private int mServiceStartId;
    private boolean mIsServiceBound = false;
    private Notification mNotification;
    
    private WakeLock mWakeLock;
	private AudioManager mAudioManager;
	private MediaPlayer mPlayer;
	private int mPlayerState = PLAYER_IDLE;
	
	private boolean mIsPlaying = false;
	private float mCurrentVolume = 1.0f;
	private boolean mTransientLossOfFocus = false;
	
	LibraryDB mLibrary;
	private AudioBook mBook;
	private int mCurrentChapter = -1;
	private int mSeekPos = SEEK_NOT_REQUIRED;
	
	private boolean mIsRunning = false;
	
	//------SERVICE EVENTS-------------------------------------------------------------------------------
	@Override
	public void onCreate() {
		super.onCreate();
		
		MyLog.d(LOGTAG, "onCreate");
		PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.getClass().getName());
        mWakeLock.setReferenceCounted(false);
        
		mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		
		
		registerExternalStorageListener();
		
		initMediaPlayer();
		
		mLibrary = new LibraryDB(this);
		try {
			mLibrary.open();
		}catch(SQLException ex) {
			MyLog.d(LOGTAG, "Error opening library DB");
			mLibrary = null;
		}
				
		//make sure service will stop if there no activity
		mPlayerHandler.sendEmptyMessageDelayed(MSG_DELAYED_EXIT, IDLE_TIME);
	}

	@Override
	public void onDestroy () {
		MyLog.d(LOGTAG, "onDestroy");
		
		if (null != mStorageListener) {
            unregisterReceiver(mStorageListener);
            mStorageListener = null;
        }
		
		mAudioManager.unregisterMediaButtonEventReceiver(getMediaButtonReceiverComponent());
		mAudioManager.abandonAudioFocus(mAudioFocusListener);
		
		TelephonyManager phoneManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		phoneManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
		
		mPlayerHandler.removeCallbacksAndMessages(null);
		
		//save book position and last book played
		SharedPreferences.Editor prefs = getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE).edit();
		if (null != mBook) {
			mBook.ChapterId = mBook.getChapterId(mCurrentChapter);
			mBook.ChapterProgress = position();
			mLibrary.updateBookProgress(mBook);
			prefs.putLong(BOOK_ID_EXTRA, mBook.Id);
			prefs.putBoolean(PLAY_STATE_EXTRA, mIsPlaying);
		} else {
			prefs.putLong(BOOK_ID_EXTRA, 0);
			prefs.putBoolean(PLAY_STATE_EXTRA, false);
		}
		prefs.commit();
		
		releaseMediaPlayer();
		
		if(null != mLibrary) {
			mLibrary.close();
			mLibrary = null;
		}
	
		mWakeLock.release();
		
		super.onDestroy();
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if(null == mLibrary) {
			//we were unable to open the library
			NotifyUser(R.string.error_opening_library);
			stopSelf();
		}
			
		mServiceStartId = startId;
		mPlayerHandler.removeMessages(MSG_DELAYED_EXIT);
		
		if(!mIsRunning) {
			SharedPreferences prefs = getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE);
			long lastPlayedBook = prefs.getLong(BOOK_ID_EXTRA, 0);
			if(0 != lastPlayedBook)
				openBook(lastPlayedBook);
			else
				notifyChange(META_CHANGED_ACTION);
			
			if(null == intent && prefs.getBoolean(PLAY_STATE_EXTRA, false)) {
				play(); //service was killed by the system, restore play state
			}
			
			mIsRunning = true;
		}
		
		if(null != intent && SERVICECMD_ACTION.equals(intent.getAction())) {
			processServiceCmd(intent);
		}
		
		return START_STICKY;
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		mPlayerHandler.removeMessages(MSG_DELAYED_EXIT);
		mIsServiceBound = true;
		return mBinder;
	}
	
	@Override
    public void onRebind(Intent intent) {
		mPlayerHandler.removeMessages(MSG_DELAYED_EXIT);
        mIsServiceBound = true;
    }
	
	@Override
	public boolean onUnbind (Intent intent) {
		mIsServiceBound = false;
		if (!mIsPlaying) {
			mPlayerHandler.sendEmptyMessageDelayed(MSG_DELAYED_EXIT, IDLE_TIME);
		}
		return true;
	}

	
	//------EVENT LISTENERS-------------------------------------------------------------------------------
	private BroadcastReceiver mStorageListener =  new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                stop();
            } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {

            }
        }
    };

	MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {
		@Override
		public void onPrepared(MediaPlayer mp) {
            mPlayerHandler.sendEmptyMessage(MSG_PLAYER_READY);
		}
	};
	
	MediaPlayer.OnCompletionListener mCompletionListener = new MediaPlayer.OnCompletionListener() {
		public void onCompletion(MediaPlayer mp) {
			mWakeLock.acquire(30000); //make sure we process message before going to sleep
			mPlayerHandler.sendEmptyMessage(MSG_TRACK_ENDED);
			mPlayerHandler.sendEmptyMessage(MSG_RELEASE_WAKELOCK);
		}
	};

	MediaPlayer.OnErrorListener mErrorListener = new MediaPlayer.OnErrorListener() {
		public boolean onError(MediaPlayer mp, int what, int extra) {
			switch (what) {
			case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
				mPlayerHandler.sendMessageDelayed(
						mPlayerHandler.obtainMessage(MSG_MEDIA_SERVER_DIED), PLAYER_RESTART_TIMEOUT);
				return true;
			default:
				MyLog.d(LOGTAG, "MediaPlayer Error: " + what + "," + extra);
				NotifyUser(R.string.playback_failed);
				stop();
				break;
			}
			return false;
		}
	};
	
	private PhoneStateListener mPhoneStateListener =  new PhoneStateListener() {
		public void onCallStateChanged(int state, String incomingNumber) {
			switch (state) {
			case TelephonyManager.CALL_STATE_RINGING:
				mPlayerHandler.obtainMessage(MSG_AUDIO_FOCUS_CHANGED, AudioManager.AUDIOFOCUS_LOSS, 0).sendToTarget();
				break;
			case TelephonyManager.CALL_STATE_OFFHOOK:
				mPlayerHandler.obtainMessage(MSG_AUDIO_FOCUS_CHANGED, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, 0).sendToTarget();
				break;
			case TelephonyManager.CALL_STATE_IDLE:
				mPlayerHandler.obtainMessage(MSG_AUDIO_FOCUS_CHANGED, AudioManager.AUDIOFOCUS_GAIN, 0).sendToTarget();
				break;
			}
        }
	};
	
	private OnAudioFocusChangeListener mAudioFocusListener = new OnAudioFocusChangeListener() {
		@Override
		public void onAudioFocusChange(int focusChange) {
			mPlayerHandler.obtainMessage(MSG_AUDIO_FOCUS_CHANGED, focusChange, 0).sendToTarget();
		}
	};
	
	//--MEDIA PLAYER HANDLER------------------------------------------------------------------------------
	private Handler mPlayerHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_RELEASE_WAKELOCK:
				mWakeLock.release();
				break;
			case MSG_FADE_VOLUME_DOWN:
				fadeVolume(true);
				break;
			case MSG_FADE_VOLUME_UP:
				fadeVolume(false);
				break;
			case MSG_AUDIO_FOCUS_CHANGED:
				handleAudioFocusChanges(msg.arg1);
				break;
			case MSG_MEDIA_SERVER_DIED:
				//restart player & proceed playing
				releaseMediaPlayer();
				initMediaPlayer();
				openMedia(currentMediaPath());
				break;
			case MSG_TRACK_ENDED:
				nextChapter();
				break;
			case MSG_PLAYER_READY:
				mPlayerState = PLAYER_INITIALIZED;
				if (SEEK_NOT_REQUIRED != mSeekPos) {
					seek(mSeekPos);
					mSeekPos = SEEK_NOT_REQUIRED;
				}
				notifyChange(META_CHANGED_ACTION);
				if (mIsPlaying){
					startMediaPlayer();
				}
				break;
			case MSG_DELAYED_EXIT:
				if (mIsPlaying || mTransientLossOfFocus || mIsServiceBound) {
					return;
				}
				MyLog.d(LOGTAG, "IDLE TIMEOUT - exiting");
				stopSelf(mServiceStartId);
				break;
			default:
				super.handleMessage(msg);
			}
		}
	};

	private void handleAudioFocusChanges(int focusChange) {
		switch (focusChange) {
		case AudioManager.AUDIOFOCUS_LOSS:
			pause(false);
			break;
		case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
			fadeVolume(true);
			break;
		case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
			pause(true);
			break;
		case AudioManager.AUDIOFOCUS_GAIN:
			if (mTransientLossOfFocus) {
				endTransientPause();
			} else {
				fadeVolume(false);
			}
			break;
		default:
			MyLog.d(LOGTAG, "Unknown audio focus change code");
		}
	}
	
	private void processServiceCmd(Intent intent) {
    	String command = intent.getStringExtra(CMD_NAME);
		if(CMD_PLAYPAUSE.equals(command)) {
			if(isPlaying()) {
				pause();
			}else{
				play();
			}
		}else if(CMD_PAUSE.equals(command)) {
			pause();
		}else if(CMD_STOP.equals(command)) {
			stop();
		}else if(CMD_NEXT.equals(command)) {
			nextChapter();
		}else if(CMD_PREV.equals(command)) {
			prevChapter();
		}else if(CMD_FFWD.equals(command)) {
			int offset = Integer.getInteger(intent.getStringExtra(CMD_SEEKOFFSET), 0);
			int position = position();
			int duration = duration();
			
			position = position+offset;
			if(position > duration)
				position = duration;
			
			if(position != position()) {
				seek(position);
			}
		}else if(CMD_REWIND.equals(command)) {
			int offset = Integer.getInteger(intent.getStringExtra(CMD_SEEKOFFSET), 0);
			int position = position();
			
			position = position-offset;
			if(position < 0)
				position = 0;
			
			if(position != position()) {
				seek(position);
			}
		}else {
			MyLog.d(LOGTAG, "processServiceCmd Error: unknown command: "+command);
		}
	}
	
	//------PUBLIC PLAYER METHODS-------------------------------------------------------------------------------
	public void openBook(long bookId) {
		synchronized(this){
			stop();

			mBook = mLibrary.LoadBook(bookId);
	    	mCurrentChapter = 0;

	    	//load last saved auto bookmark
	    	if(null != mBook && 0 != mBook.ChapterId) {
	    		mCurrentChapter = mBook.getChapterIdx(mBook.ChapterId);
	    		mSeekPos = mBook.ChapterProgress;
	    	}
	    	
	    	openMedia(currentMediaPath());
		}
	}
	
	public void play() {
		synchronized(this){
			if(null == mBook)
    			return;
			
			if(!gainAndListenAudioFocus())
				return;
			
			mPlayerHandler.removeMessages(MSG_DELAYED_EXIT);
			mIsPlaying = true;
			
			//reopen media in case player is not initialized. Normally this shouldn't happen
			if(PLAYER_IDLE == mPlayerState) {
				MyLog.d(LOGTAG, "play() - player was not initialized");
				openMedia(currentMediaPath());
			}
			
			if(mTransientLossOfFocus)
				return;
			
			if(IsInitialized())
				startMediaPlayer();
			
			notifyChange(PLAYSTATE_CHANGED_ACTION);
		}
	}

	public boolean isPlaying() { return mIsPlaying; }
	
	public void pause() {
		pause(false);
	}
	
	public void stop() {
		synchronized(this){
			if(null == mBook)
    			return;
			mPlayerState = PLAYER_IDLE;
			mIsPlaying = false;
			mPlayer.reset();
			notifyChange(PLAYSTATE_CHANGED_ACTION);
			mPlayerHandler.sendEmptyMessageDelayed(MSG_DELAYED_EXIT, IDLE_TIME);
		}
	}
	
	public void nextChapter() {
    	synchronized(this){
    		if(null == mBook)
    			return;
    		
    		if(mCurrentChapter < mBook.Chapters.size()-1) {
    			++mCurrentChapter;
    			openMedia(currentMediaPath());
    		}else if(mIsPlaying) {
    			pause(false);
    		}
    	}
    }
    
    public void prevChapter() {
    	synchronized(this){
    		if(null == mBook)
    			return;
    		if(mCurrentChapter > 0 ) {
    			--mCurrentChapter;
    			openMedia(currentMediaPath());
    		}
    	}
    }
    
    public int duration() {
    	synchronized(this){
    		if(null != mBook && IsInitialized()){
    			int duration = mBook.Chapters.get(mCurrentChapter).Duration;
    			if(duration > 0)
    				return duration;
    			return mPlayer.getDuration();
    		}
    	}
    	return 0;
    }

    public int position() {
    	synchronized(this){
    		if(IsInitialized())
    			return mPlayer.getCurrentPosition();
    	}
    	return 0;
    }
    
    public int seek(int whereto) {
    	synchronized(this){
    		if(IsInitialized()) {
    			mPlayer.seekTo(whereto);
    			return whereto;
    		}
    	}
    	return 0;
    }
    
    public void bookmark(String title) {
    	if(!IsInitialized() || null == mBook)
    		return;
    	
    	AudioBook.Bookmark bm = new AudioBook.Bookmark();
    	synchronized(this) {
    		bm.BookId = mBook.Id;
    		bm.ChapterId = mBook.getChapterId(mCurrentChapter);
    		bm.ChapterTitle = (String) mBook.getChapterTitle(mCurrentChapter);
    		bm.SeqNo = mBook.getChapterSeqNo(mCurrentChapter);
    		bm.Position = position();
    		
    		if(StringUtils.isBlank(title))
    			bm.MakeAutoTitle();
    		else
    			bm.Title = title;
    	}
    			
		mLibrary.addBookmark(bm);
    }
    
    public int getChaptersCount() {
		if(null != mBook) 
			return mBook.Chapters.size();
		return 0;
	}
	
	public int getChapterIndex(long id) {
		if(null != mBook) 
			return mBook.getChapterIdx(id);
		return 0;
	}
	
	public CharSequence getChapterTitle(int index) {
		if(null != mBook) 
			return mBook.getChapterTitle(index);
		return null;
	}
	
	public void setChapterByIndex(int index) {
    	synchronized(this){
    		if(null == mBook)
    			return;
    		if(index<0 || index>(mBook.Chapters.size()-1))
    			return;
    		mCurrentChapter = index;
    		openMedia(currentMediaPath());
    	}
	}
	
	public void setChapter(long chapterId) {
    	synchronized(this){
    		if(null == mBook)
    			return;
    		int index = mBook.getChapterIdx(chapterId);
    		setChapterByIndex(index);
    	}
    }
	
	public void setBookmark(long chapterId, int timeOffset) {
		synchronized(this){
			mCurrentChapter = mBook.getChapterIdx(chapterId);
			mSeekPos = timeOffset;
			openMedia(currentMediaPath());
		}
	}
	
	//Service client uses this to get current meta info 
	public void requestMetaUpdate() {
		//sendNotifycation(META_CHANGED_ACTION);
	}
    
  //------LOW LEVEL PLAYER METHODS-------------------------------------------------------------------------------
	private boolean IsInitialized() {
		return mPlayerState == PLAYER_INITIALIZED;
	}
    private String currentMediaPath() {
    	return (null == mBook) ? null : mBook.Chapters.get(mCurrentChapter).MediaFileName;
    }
    
    public void registerExternalStorageListener() {
		IntentFilter iFilter = new IntentFilter();
		iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
		iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
		iFilter.addDataScheme("file");
		registerReceiver(mStorageListener, iFilter);
		MyLog.d(LOGTAG, "Storage listener registered");
    }
    
    private boolean gainAndListenAudioFocus() {
		TelephonyManager phoneManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		if (phoneManager.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
			return false;
		}
		
		int result = mAudioManager.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
		if(AudioManager.AUDIOFOCUS_REQUEST_GRANTED != result)
			return false;
		
		phoneManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
		mAudioManager.registerMediaButtonEventReceiver(getMediaButtonReceiverComponent());
		
		return true;
	}
	
	private void initMediaPlayer() {
		mPlayerState = PLAYER_IDLE;
		mPlayer = new MediaPlayer();
		mPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
		mPlayer.setOnCompletionListener(mCompletionListener);
        mPlayer.setOnErrorListener(mErrorListener);
        mPlayer.setOnPreparedListener(mPreparedListener);
        MyLog.d(LOGTAG, "MediaPlayer initialized");
	}
	
	private void releaseMediaPlayer() {
		mPlayerState = PLAYER_IDLE;
		mPlayer.release();
		mPlayer = null;
        MyLog.d(LOGTAG, "MediaPlayer released");
	}
	
	private ComponentName getMediaButtonReceiverComponent() {
		return new ComponentName(getPackageName(), MediaButtonReceiver.class.getName());
	}
	
	private void openMedia(String path) {
        try {
        	mPlayerState = PLAYER_IDLE;
        	mPlayer.reset();
        	if(null == path)
        		return;
        	
            mPlayer.setDataSource(path);
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mPlayerState = PLAYER_INITIALIZING;
            mPlayer.prepareAsync();
        } catch (IOException ex) {
        	MyLog.d(LOGTAG, "open media exception: " + ex.getMessage());
        	NotifyUser(R.string.playback_failed);
        	stop();
        } catch (IllegalArgumentException ex) {
        	MyLog.d(LOGTAG, "open media exception: " + ex.getMessage());
        	NotifyUser(R.string.playback_failed);
        	stop();
        }
    }
    
    private void startMediaPlayer() {
    	mPlayerHandler.removeMessages(MSG_FADE_VOLUME_DOWN);

		int pos = mPlayer.getCurrentPosition();
		if (pos >= STEP_BACK_AFTER_PAUSE) {
			mPlayer.seekTo(pos-STEP_BACK_AFTER_PAUSE); //always go little back to compensate fade in/out
		}else if(pos != 0) {
			mPlayer.seekTo(0);
		}
		
		mCurrentVolume = LOW_VOLUME_LEVEL;
		mPlayer.setVolume(mCurrentVolume, mCurrentVolume);
		mPlayerHandler.sendEmptyMessage(MSG_FADE_VOLUME_UP);
		mPlayer.start();
    }
    
    private void pause(boolean isTransient) {
		synchronized(this){
			if(null == mBook)
    			return;
			if(IsInitialized() && mIsPlaying) {
				mPlayer.pause();
			}
			if(isTransient)
				mTransientLossOfFocus = true;
			else {
				mIsPlaying = false;
				notifyChange(PLAYSTATE_CHANGED_ACTION);
				mPlayerHandler.sendEmptyMessageDelayed(MSG_DELAYED_EXIT, IDLE_TIME);
			}
		}
	}
    
    private void endTransientPause() {
		synchronized(this){
			mTransientLossOfFocus = false;
			if(mIsPlaying) {
				play();
			}
		}
	}
    
    private void fadeVolume(boolean fadeDown) {
		if(fadeDown){
			mCurrentVolume -= VOLUME_FADE_STEP;
			if (mCurrentVolume > LOW_VOLUME_LEVEL) {
				mPlayerHandler.sendEmptyMessageDelayed(MSG_FADE_VOLUME_DOWN, VOLUME_FADE_STEP_DELAY);
			} else {
				mCurrentVolume = LOW_VOLUME_LEVEL;
			}
		}else{
			mCurrentVolume += VOLUME_FADE_STEP;
			if (mCurrentVolume < FULL_VOLUME_LEVEL) {
				mPlayerHandler.sendEmptyMessageDelayed(MSG_FADE_VOLUME_UP, VOLUME_FADE_STEP_DELAY);
			} else {
				mCurrentVolume = FULL_VOLUME_LEVEL;
			}
		}
		mPlayer.setVolume(mCurrentVolume, mCurrentVolume);
	}
    
    private void notifyChange(String action) {
        if(PLAYSTATE_CHANGED_ACTION.equals(action)){
        	if(mIsPlaying)
        		UpdateForeground(); //set foreground mode
        	else
        		stopForeground(true);
        }else if(META_CHANGED_ACTION.equals(action)) {
        	if(mIsPlaying)
        		UpdateForeground(); //update notification meta info
        }
       
        sendNotifycation(action);
    }
    
    private void sendNotifycation(String action) {
    	Intent intent = new Intent(action);
        intent.putExtra(PLAY_STATE_EXTRA, isPlaying());
        
        if(META_CHANGED_ACTION.equals(action)) {
        	if(null != mBook) {
        		intent.putExtra(BOOK_ID_EXTRA, mBook.Id);
        		intent.putExtra(BOOK_TITLE_EXTRA, mBook.Title);
        		intent.putExtra(BOOK_COVER_EXTRA, mBook.Cover);
        		intent.putExtra(CHAPTERS_COUNT_EXTRA, mBook.Chapters.size());
        		intent.putExtra(CHAPTER_INDEX_EXTRA, mCurrentChapter);
        		intent.putExtra(CHAPTER_ID_EXTRA, mBook.Chapters.get(mCurrentChapter).Id);
        		intent.putExtra(CHAPTER_TITLE_EXTRA, mBook.Chapters.get(mCurrentChapter).Title);
        		intent.putExtra(CHAPTER_DURATION_EXTRA, duration());
        	}else{
        		intent.putExtra(BOOK_ID_EXTRA, 0L);
            	intent.putExtra(BOOK_TITLE_EXTRA, "");
            	intent.putExtra(BOOK_COVER_EXTRA, "");
            	intent.putExtra(CHAPTERS_COUNT_EXTRA, 0);
            	intent.putExtra(CHAPTER_INDEX_EXTRA, 0);
            	intent.putExtra(CHAPTER_ID_EXTRA, 0L);
            	intent.putExtra(CHAPTER_TITLE_EXTRA, "");
            	intent.putExtra(CHAPTER_DURATION_EXTRA, 0);
        	}
        }
        
        sendStickyBroadcast(intent);
    }
    
    private void UpdateForeground() {
		Intent intent = new Intent(this, MainActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, PENDING_NOTIFICATION_ID, intent, Intent.FLAG_ACTIVITY_NEW_TASK);
		
		CharSequence notificationText = getResources().getText(R.string.app_name);
		if(null != mBook) {
    		notificationText = mBook.Title+" - "+mBook.getChapterTitle(mCurrentChapter);
    	}
		
		RemoteViews rview = new RemoteViews(getPackageName(), R.layout.notification_layout); 
		rview.setImageViewResource(R.id.notification_icon, R.drawable.icon);
		rview.setTextViewText(R.id.notification_text, notificationText);
		
		mNotification = new Notification(R.drawable.icon, notificationText, System.currentTimeMillis());
		mNotification.flags |= Notification.FLAG_ONGOING_EVENT;
		mNotification.contentIntent = pendingIntent;
		mNotification.contentView = rview;
		
		startForeground(PENDING_NOTIFICATION_ID, mNotification);
	}
    
    private void NotifyUser(int resource) {
    	Toast.makeText(this, getResources().getText(resource), Toast.LENGTH_SHORT).show();
    }
    
    
  //------SERVICE BINDER-------------------------------------------------------------------------------
    private LocalBinder mBinder = new LocalBinder();
    class LocalBinder extends Binder {
    	public AudioService getService() {
            return AudioService.this;
        }
	};
}
