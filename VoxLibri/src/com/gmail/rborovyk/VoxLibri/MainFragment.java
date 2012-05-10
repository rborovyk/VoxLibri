package com.gmail.rborovyk.VoxLibri;

import org.apache.commons.lang3.StringUtils;

import com.gmail.rborovyk.VoxLibri.AudioService.LocalBinder;
import com.gmail.rborovyk.ui.OnDialogListener;
import com.gmail.rborovyk.ui.RepeatingImageButton;
import com.gmail.rborovyk.ui.TextQueryDialogFragment;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainFragment extends Fragment {
	//----CONSTANTS----------------------------------------------------------------------------
	private static final String LOGTAG = "MainFragment";
	private static final String BOOKMARK_TAG = "new_bookmark";
	private static final int COVER_LOADER_ID = 0;
	private static final int MEDIA_PROGRESS_MAX = 1000;
	private static final int REFRESH_PROGRESS = 1;
	private static final int NOT_SEEKING = -1;
	
	static class DelayedMessage {
		public static final String MESSAGE_TYPE_EXTRA = "DelayedMessage.Type";
		public static final String BOOK_ID_EXTRA = "DelayedMessage.BookId";
		public static final String CHAPTER_ID_EXTRA = "DelayedMessage.ChapterId";
		public static final String CHAPTER_PROGRESS_EXTRA = "DelayedMessage.Progress";
		
		public static final int NEW_BOOK = 0;
		public static final int NEW_CHAPTER = 1;
		public static final int NEW_BOOKMARK = 2;
		
	}
	
	//----FIELDS-------------------------------------------------------------------------------
	private AudioService mService;
	
	private TextView mBookText;
	private TextView mChapterText;
	private TextView mChapterTime;
	private SeekBar mChapterProgress;
	private SeekBar mMediaProgress;
	private RepeatingImageButton mPrevButton;
	private ImageButton mPlayButton;
	private RepeatingImageButton mNextButton;
	private ImageButton mBookmarkButton;
	private ImageView mBookCover;
	private Bitmap mDefaultCover;
	private boolean mUIUpdateRequied;
	
	private long mBookId;
	private long mChapterId;
	private int mDuration;
	private int mStartSeekPos;
	private long mLastAppliedSeekTime;
	private int mSeekPosition = NOT_SEEKING;
	private boolean mTouchSeeking;
	private boolean mChapterTouchSeeking;
	
	private Bundle mDelayedMessage = new Bundle();
	
	private MainListener mMainListener;
	private ImageLoader mCoverLoader;
	
	//Instead of directly opening different activities we give our host activity
	//a chance to manage this and select best way (activity for the phone, fragment/tab for the tablet)
	public interface MainListener {
		void showBookmarks(long bookId);
		void showChapters(long bookId);
		void showLibrary();
		void showPreferences();
		void showAbout();
	}

	//----Fragment events-------------------------------------------------------------------------------
	@Override
	public void onAttach(Activity activity) {
		try{
			mMainListener = (MainListener) activity;
		} catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement MainFragment.MainListener");
        }
		super.onAttach(activity);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		setHasOptionsMenu(true);
		
		mCoverLoader = (ImageLoader)getLoaderManager().initLoader(COVER_LOADER_ID, null, mCoverLoaderCallbacks);
        mDefaultCover = BitmapFactory.decodeResource(getResources(), R.drawable.audiobook_icon);
        
        RestoreAftrConfigChange();
        
		super.onCreate(savedInstanceState);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.main_fragment, container, false);
		configurateView(view);
		return view;
	}
	
	@Override
	public void onStart() {
		super.onStart();
		mUIUpdateRequied = true;

		IntentFilter s = new IntentFilter();
		s.addAction(Intent.ACTION_SCREEN_ON);
		s.addAction(Intent.ACTION_SCREEN_OFF);
		s.addAction(AudioService.META_CHANGED_ACTION);
		s.addAction(AudioService.PLAYSTATE_CHANGED_ACTION);
		getActivity().registerReceiver(mBroadcastListener, new IntentFilter(s));

		scheduleNextRefresh(refreshProgress());

		Intent intent = new Intent(getActivity(), AudioService.class);
		getActivity().startService(intent);
		getActivity().bindService(intent, mConnection, 0);
	}

	@Override
	public void onStop() {
        mUIUpdateRequied = false;
        
        mHandler.removeMessages(REFRESH_PROGRESS);
        getActivity().unregisterReceiver(mBroadcastListener);
        
        if (mService != null) {
        	getActivity().unbindService(mConnection);
            mService = null;
        }
		super.onStop();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.main_menu, menu);
	}

	@Override
	public boolean onOptionsItemSelected (MenuItem item) {
		if(null == mMainListener)
			return true;
		
		 switch (item.getItemId()) {
	        case R.id.main_menu_bookmarks:
	        	mMainListener.showBookmarks(mBookId);
	            return true;
	        case R.id.main_menu_chapters:
	        	mMainListener.showChapters(mBookId);
	            return true;
	        case R.id.main_menu_library:
	        	mMainListener.showLibrary();
	            return true;
	        case R.id.main_menu_prefs:
	        	mMainListener.showPreferences();
	            return true;
	        case R.id.main_menu_about:
	        	mMainListener.showAbout();
	            return true;
	        default:
	            return super.onOptionsItemSelected(item);
	        }
	}
	
	public void onBookSelected(long bookId) {
		if(mBookId != bookId) {
			mDelayedMessage.clear();
			mDelayedMessage.putInt(DelayedMessage.MESSAGE_TYPE_EXTRA, DelayedMessage.NEW_BOOK);
			mDelayedMessage.putLong(DelayedMessage.BOOK_ID_EXTRA, bookId);
			processDelayedMessage();
		}
	}

	public void onChapterSelected(long chapterId) {
		mDelayedMessage.clear();
		mDelayedMessage.putInt(DelayedMessage.MESSAGE_TYPE_EXTRA, DelayedMessage.NEW_CHAPTER);
		mDelayedMessage.putLong(DelayedMessage.CHAPTER_ID_EXTRA, chapterId);
		processDelayedMessage();
	}

	public void onBookmarkSelected(long chapterId, int position) {
		mDelayedMessage.clear();
		mDelayedMessage.putInt(DelayedMessage.MESSAGE_TYPE_EXTRA, DelayedMessage.NEW_BOOKMARK);
		mDelayedMessage.putLong(DelayedMessage.CHAPTER_ID_EXTRA, chapterId);
		mDelayedMessage.putInt(DelayedMessage.CHAPTER_PROGRESS_EXTRA, position);
		processDelayedMessage();
	}
	
	//----UI methods & Listeners-------------------------------------------------------------------------------
	private void RestoreAftrConfigChange() {
		//Restore dialog callback after config. change
        final TextQueryDialogFragment dialog = (TextQueryDialogFragment) getFragmentManager().findFragmentByTag(BOOKMARK_TAG);
        if(null != dialog) {
        	dialog.setClickListener(getNewBookmarkListener(dialog));
        }
	}
	
	private void configurateView(View view) {
		mBookCover = (ImageView) view.findViewById(R.id.main_book_cover);
		mBookCover.setImageBitmap(mDefaultCover);
		
		mPrevButton = (RepeatingImageButton) view.findViewById(R.id.main_btn_prev);
		mPrevButton.setOnClickListener(mPrevListener);
		mPrevButton.setRepeatListener(mRewListener, 260);
		mPlayButton = (ImageButton) view.findViewById(R.id.main_btn_play);
		mPlayButton.requestFocus();
		mPlayButton.setOnClickListener(mPlayListener);
		mNextButton = (RepeatingImageButton) view.findViewById(R.id.main_btn_next);
		mNextButton.setOnClickListener(mNextListener);
		mNextButton.setRepeatListener(mFfwdListener, 260);
		mBookmarkButton = (ImageButton) view.findViewById(R.id.main_btn_bookmark);
		mBookmarkButton.setOnClickListener(mBookmarkListener);

		mBookText = (TextView) view.findViewById(R.id.main_book_title);
		mChapterText = (TextView) view.findViewById(R.id.main_chapter_title);
		mChapterTime = (TextView) view.findViewById(R.id.main_media_time);

		mChapterProgress = (SeekBar) view.findViewById(R.id.main_chapter_progress);
		mChapterProgress.setOnSeekBarChangeListener(mChapterSeekListener);
		mMediaProgress = (SeekBar) view.findViewById(R.id.main_media_progress);
		mMediaProgress.setOnSeekBarChangeListener(mMediaSeekListener);
		mMediaProgress.setMax(MEDIA_PROGRESS_MAX);
		mMediaProgress.setProgress(0);
		//        
		//        enableControls(false);
	}
   
//   private void enableControls(boolean enable) {
//   	mPrevButton.setEnabled(enable);
//      	mPlayButton.setEnabled(enable);
//   	mNextButton.setEnabled(enable);
//   	mBookmarkButton.setEnabled(enable);
//   }
	
	private View.OnClickListener mPlayListener = new View.OnClickListener() {
		public void onClick(View v) {
			onPlayPause();
		}
	};

	private View.OnClickListener mPrevListener = new View.OnClickListener() {
		public void onClick(View v) {
			onPrev();
		}
	};

	private View.OnClickListener mNextListener = new View.OnClickListener() {
		public void onClick(View v) {
			onNext();
		}
	};

	private RepeatingImageButton.RepeatListener mRewListener = new RepeatingImageButton.RepeatListener() {
		public void onRepeat(View view, int duration, int repeatCount) {
			onRewind(repeatCount, duration);
		}
	};

	private RepeatingImageButton.RepeatListener mFfwdListener = new RepeatingImageButton.RepeatListener() {
		public void onRepeat(View view, int duration, int repeatCount) {
			onFastForward(repeatCount, duration);
		}
	};

	private SeekBar.OnSeekBarChangeListener mChapterSeekListener = new SeekBar.OnSeekBarChangeListener() {
		public void onStartTrackingTouch(SeekBar bar) {
			mChapterTouchSeeking = true;
		}

		public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
			if (fromuser && null != mService)
				mChapterText.setText(mService.getChapterTitle(progress));
		}

		public void onStopTrackingTouch(SeekBar bar) {
			mChapterTouchSeeking = false;
			
			if(null != mService)
				mService.setChapterByIndex(bar.getProgress());
		}
	};

	private SeekBar.OnSeekBarChangeListener mMediaSeekListener = new SeekBar.OnSeekBarChangeListener() {
		public void onStartTrackingTouch(SeekBar bar) {
			mLastAppliedSeekTime = 0;
			mTouchSeeking = true;
			mHandler.removeMessages(REFRESH_PROGRESS);
		}

		public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
			if (!fromuser || (null == mService))
				return;

			mSeekPosition = mDuration * progress / MEDIA_PROGRESS_MAX;
			mService.seek(mSeekPosition);

			refreshProgress();

			if (!mTouchSeeking) { // trackball event, allow progress updates
				mSeekPosition = NOT_SEEKING;
			}
		}

		public void onStopTrackingTouch(SeekBar bar) {
			mSeekPosition = NOT_SEEKING;
			mTouchSeeking = false;
			mHandler.sendEmptyMessage(REFRESH_PROGRESS);
		}
	};

	private View.OnClickListener mBookmarkListener = new View.OnClickListener() {
		public void onClick(View v) {
			if(null == mService || mTouchSeeking || 0 == mBookId)
				return;
			if(mService.isPlaying())
				mService.pause();

			final TextQueryDialogFragment dialog = new TextQueryDialogFragment();
			dialog.setTitle(R.string.bookmark_dialog_title);
			dialog.setLabel(R.string.bookmark_dialog_label);
			dialog.setQueryText("");
			dialog.setIcon(R.drawable.icon);
			dialog.setClickListener(getNewBookmarkListener(dialog));
			
			dialog.show(getFragmentManager(), BOOKMARK_TAG);
		}
	};
	
	private OnDialogListener getNewBookmarkListener(final TextQueryDialogFragment dialog) {
		return new OnDialogListener() {
			@Override
			public void OnPositiveClick() {
				String title = dialog.getQueryText();
				mService.bookmark(title);
			}
			
			@Override
			public void OnNegativeClick() {
				
			}
		};
	}

	//--MAIN LOGIC--------------------------------------------------------------------------------
    private void onPlayPause() {
		if (mService != null) {
			if (mService.isPlaying()) {
				mService.pause();
				setPlayButtonImage(false);
			} else {
				mService.play();
				setPlayButtonImage(true);
			}
			refreshProgress();
		}
    }

    private void onPrev() {
        if (mService == null) return;
        mService.prevChapter();
    }

    private void onNext() {
        if (mService == null) return;
        mService.nextChapter();
    }
    
    private void onFastForward(int repeatCount, int pressDuration) {
    	if(mService == null) return;

		if (repeatCount == 0) {
			mStartSeekPos = mService.position();
			mLastAppliedSeekTime = 0;
		} else {
			if (pressDuration < 5000) {
				// seek at 10x speed for the first 5 seconds
				pressDuration = pressDuration * 10;
			} else {
				// seek at 40x after that
				pressDuration = 50000 + (pressDuration - 5000) * 40;
			}
			int newPos = mStartSeekPos + pressDuration;
			int duration = mService.duration();
			if (newPos >= duration) {
				newPos = duration;
			}

			if (((pressDuration - mLastAppliedSeekTime) > 250)
					|| RepeatingImageButton.LAST_EVENT == repeatCount) {
				mService.seek(newPos);
				mLastAppliedSeekTime = pressDuration;
			}

			if (RepeatingImageButton.LAST_EVENT == repeatCount) {
				mSeekPosition = NOT_SEEKING;
			} else {
				mSeekPosition = newPos;
			}

			refreshProgress();
		}
	}

    private void onRewind(int repeatCount, int pressDuration) {
    	if(null == mService) return;

		if (repeatCount == 0) {
			mStartSeekPos = mService.position();
			mLastAppliedSeekTime = 0;
		} else {
			if (pressDuration < 5000) {
				// seek at 10x speed for the first 5 seconds
				pressDuration = pressDuration * 10;
			} else {
				// seek at 40x after that
				pressDuration = 50000 + (pressDuration - 5000) * 40;
			}
			int newPos = mStartSeekPos - pressDuration;
			if (newPos < 0) {
				newPos = 0;
			}
			if (((pressDuration - mLastAppliedSeekTime) > 250)
					|| RepeatingImageButton.LAST_EVENT == repeatCount) {
				mService.seek(newPos);
				mLastAppliedSeekTime = pressDuration;
			}

			if (RepeatingImageButton.LAST_EVENT == repeatCount) {
				mSeekPosition = NOT_SEEKING;
			} else {
				mSeekPosition = newPos;
			}

			refreshProgress();
		}
    }
    
    private void setPlayButtonImage(boolean playing) {
		if(playing) {
			mPlayButton.setImageResource(R.drawable.main_btn_pause);
		}else{
			mPlayButton.setImageResource(R.drawable.main_btn_play);
		}
	}

    //---Progress refresh-------------------------------------------------------------
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REFRESH_PROGRESS:
                    scheduleNextRefresh(refreshProgress());
                    break;
                default:
                    break;
            }
        }
    };
    
    private long refreshProgress() {
    	if(mService == null)
    		return 500;
    	try {
    		long pos = (NOT_SEEKING == mSeekPosition) ? mService.position() : mSeekPosition;
    		long remaining = MEDIA_PROGRESS_MAX - (pos % MEDIA_PROGRESS_MAX);
    		if ((pos >= 0) && (mDuration > 0)) {
    			mChapterTime.setText(LocalUtils.secToTimeString(pos / MEDIA_PROGRESS_MAX));

    			if (!mService.isPlaying()) {
    				remaining = 500;
    			}

    			mMediaProgress.setProgress((int) (MEDIA_PROGRESS_MAX * pos / mDuration));
    		} else {
    			mChapterTime.setText("--:--");
    			mMediaProgress.setProgress(MEDIA_PROGRESS_MAX);
    		}
    		// return the number of milliseconds until the next full second, so
    		// the counter can be updated at just the right time
    		return remaining;
    	} catch (Exception ex) {
    		MyLog.d(LOGTAG, "refreshProgress exception: "+ex.toString());
    	}
    	return 500;
    }
    
    private void scheduleNextRefresh(long delay) {
        if (mUIUpdateRequied && !mTouchSeeking) {
            mHandler.removeMessages(REFRESH_PROGRESS);
            mHandler.sendMessageDelayed(mHandler.obtainMessage(REFRESH_PROGRESS), delay);
        }
    }
    
    //-----------------------------------------------------------------------------------
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
        	LocalBinder binder = (LocalBinder) service;
            mService = binder.getService();
            MyLog.d(LOGTAG, "service bound: "+mService);
            //Ask service to send current meta information (opened book, position, play state etc.)
            mService.requestMetaUpdate();
            
            processDelayedMessage();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        	mService = null;
        	MyLog.d(LOGTAG, "service un-bound");
        }
    };
    
    //-----------------------------------------------------------------------------------
    //Process messages only if service is bound. This method is called right after message 
    //is supplied and after service is bound
    private void processDelayedMessage() {
    	if(mDelayedMessage.isEmpty() || null == mService)
    		return;
    	try{
    		int type = mDelayedMessage.getInt(DelayedMessage.MESSAGE_TYPE_EXTRA);
    		MyLog.d(LOGTAG, "processDelayedMessage: "+type);
    		switch(type) {
    		case DelayedMessage.NEW_BOOK:
    			long newBookId = mDelayedMessage.getLong(DelayedMessage.BOOK_ID_EXTRA);
    			mService.openBook(newBookId);
    			break;
    		case DelayedMessage.NEW_CHAPTER:
    			long newChapterId = mDelayedMessage.getLong(DelayedMessage.CHAPTER_ID_EXTRA);
    			mService.setChapter(newChapterId);
    			break;
    		case DelayedMessage.NEW_BOOKMARK:
    			long chapterId = mDelayedMessage.getLong(DelayedMessage.CHAPTER_ID_EXTRA);
    			int timeOffset = mDelayedMessage.getInt(DelayedMessage.CHAPTER_PROGRESS_EXTRA);
    			mService.setBookmark(chapterId, timeOffset);
    			break;
    		default:
    			break;
    		}
    		mDelayedMessage.clear();
    	} catch (Exception ex) {
    		MyLog.d(LOGTAG, "processDelayedMessage exception: "+ex.toString());
    	}
	}
    
    //---------------------------------------------------------------------------------------
    private BroadcastReceiver mBroadcastListener = new BroadcastReceiver() {
    	@Override
    	public void onReceive(Context context, Intent intent) {
    		if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
    			scheduleNextRefresh(refreshProgress());
    		}else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
    			mUIUpdateRequied = false;
    			mHandler.removeMessages(REFRESH_PROGRESS);
    		}else if (AudioService.META_CHANGED_ACTION.equals(intent.getAction())) {
    			updateBookInfo(intent);
                setPlayButtonImage(intent.getBooleanExtra(AudioService.PLAY_STATE_EXTRA, false));
                scheduleNextRefresh(1);
    		}else if (AudioService.PLAYSTATE_CHANGED_ACTION.equals(intent.getAction())) {
    			setPlayButtonImage(intent.getBooleanExtra(AudioService.PLAY_STATE_EXTRA, false));
    		} 
    	}
    };
    
    private void updateBookInfo(Intent intent) {
		long newBookId = intent.getLongExtra(AudioService.BOOK_ID_EXTRA, 0L);
		boolean bookChanged = (mBookId != newBookId);
		if(bookChanged) {
			MyLog.d(LOGTAG, "updateBookInfo-book: "+newBookId);
			mBookId = newBookId;
			mBookText.setText(intent.getCharSequenceExtra(AudioService.BOOK_TITLE_EXTRA));
			int chaptersCount = intent.getIntExtra(AudioService.CHAPTERS_COUNT_EXTRA, 0);
			mChapterProgress.setMax((chaptersCount > 0) ? chaptersCount-1 : 0);
			mChapterProgress.setProgress(0);
			String coverPath = intent.getStringExtra(AudioService.BOOK_COVER_EXTRA);
			if(StringUtils.isBlank(coverPath))
				mBookCover.setImageBitmap(mDefaultCover);
			else
	    		mCoverLoader.loadImage(coverPath, null);
		}
		
		long newChapterId = intent.getLongExtra(AudioService.CHAPTER_ID_EXTRA, 0);
		if(mChapterId != newChapterId || bookChanged) {
			MyLog.d(LOGTAG, "updateBookInfo-chapter: "+newChapterId);
			mChapterId = newChapterId;
			mChapterText.setText(intent.getCharSequenceExtra(AudioService.CHAPTER_TITLE_EXTRA));
			mDuration = intent.getIntExtra(AudioService.CHAPTER_DURATION_EXTRA, 0);
			
			if(!mChapterTouchSeeking)
				mChapterProgress.setProgress(intent.getIntExtra(AudioService.CHAPTER_INDEX_EXTRA, 0));
		}
	}
	
	private LoaderManager.LoaderCallbacks<ImageLoader.Data> mCoverLoaderCallbacks = new 
			LoaderManager.LoaderCallbacks<ImageLoader.Data>() {
		@Override
		public Loader<ImageLoader.Data> onCreateLoader(int id, Bundle args) {
			ImageLoader loader = new ImageLoader(getActivity(), "CoverLoader");
			int width = getResources().getDimensionPixelSize(R.dimen.main_cover_width);
			int height = getResources().getDimensionPixelSize(R.dimen.main_cover_height);
			loader.setDimensions(width, height);
			loader.enableCache(false);
			return loader;
		}

		@Override
		public void onLoaderReset(Loader<ImageLoader.Data> loader) {
			
		}

		@Override
		public void onLoadFinished(Loader<ImageLoader.Data> loader, ImageLoader.Data data) {
			if(null != data.bitmap){
				mBookCover.setImageBitmap(data.bitmap);
			}else {
				mBookCover.setImageBitmap(mDefaultCover);
			}
		}
	};
}
