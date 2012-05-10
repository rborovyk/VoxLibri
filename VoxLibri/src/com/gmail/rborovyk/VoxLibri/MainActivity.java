package com.gmail.rborovyk.VoxLibri;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;


public class MainActivity extends FragmentActivity 
	implements MainFragment.MainListener {
	
	private static final String LOGTAG = "MainActivity";
		
	private static final int LIBRARY_QUERY = 0;
	private static final int CHAPTERS_QUERY = 1;
	private static final int BOOKMARKS_QUERY = 2;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }
    
    @Override
	public void onDestroy() { 
		super.onDestroy();
	}

    //----MainFragment.MainListener impl-------------------------------------------
    public void showBookmarks(long bookId) {
		Intent intent = new Intent(this, BookmarksActivity.class);
		intent.putExtra(AudioService.BOOK_ID_EXTRA, bookId);
		startActivityForResult(intent, BOOKMARKS_QUERY); 
	}
	
    public void showChapters(long bookId) {
		Intent intent = new Intent(this, ChaptersActivity.class);
		intent.putExtra(AudioService.BOOK_ID_EXTRA, bookId);
		startActivityForResult(intent, CHAPTERS_QUERY);
	}
	
    public void showLibrary() {
		Intent intent = new Intent(this, LibraryActivity.class);
		startActivityForResult(intent, LIBRARY_QUERY); 
	}
	
    public void showPreferences() {
		Intent intent = new Intent(this, PreferencesActivity.class);
		startActivity(intent);
	}
	
    public void showAbout() {
    	Intent intent = new Intent(this, AboutActivity.class);
		startActivity(intent);
	}
    
	
	//---UI LISTENERS---------------------------------------------------------------------------
    private MainFragment mainFragment() {
		return (MainFragment) getSupportFragmentManager().findFragmentById(R.id.main_fragment);
	}
    
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		MyLog.d(LOGTAG, "onActivityResult req:"+requestCode+" res:"+resultCode);
		//this method is called when mService is null
		//use mDelayedMessage to deliver message to the point where mService is ready
		switch(requestCode){ 
		case LIBRARY_QUERY: {
			if(RESULT_OK != resultCode)
				return;
			long bookId = data.getLongExtra(AudioService.BOOK_ID_EXTRA, 0);
			mainFragment().onBookSelected(bookId);
			break;
		}
		case CHAPTERS_QUERY:{
			if(RESULT_OK != resultCode)
				return;
			long chapterId = data.getLongExtra(AudioService.CHAPTER_ID_EXTRA, 0);
			mainFragment().onChapterSelected(chapterId);
			break;
		}
		case BOOKMARKS_QUERY:{
			if(RESULT_OK != resultCode)
				return;
			long chapterId = data.getLongExtra(AudioService.CHAPTER_ID_EXTRA, 0);
			int position = data.getIntExtra(AudioService.CHAPTER_PROGRESS_EXTRA, 0);
			mainFragment().onBookmarkSelected(chapterId, position);
			break;
		}
		}
		
		super.onActivityResult(requestCode, resultCode, data);
	}
}