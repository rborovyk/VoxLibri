package com.gmail.rborovyk.VoxLibri;

import android.content.Intent;
import android.database.SQLException;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.widget.Toast;

public class BookmarksActivity extends FragmentActivity 
	implements BookmarksFragment.BookmarksListener {
	
	//private static final String LOGTAG = "BookmarksActivity";
	
	private LibraryDB mLibrary;
	private boolean mRetained = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.bookmarks);
		
		mLibrary = (LibraryDB) getLastCustomNonConfigurationInstance();
		if(null == mLibrary) {
			mLibrary = new LibraryDB(getApplicationContext());
			
			try {
				mLibrary.open();
			}catch(SQLException ex) {
				Toast.makeText(this, R.string.db_open_error, Toast.LENGTH_SHORT);
				setResult(RESULT_CANCELED);
				finish();
			}
		}
		
		mRetained = false;
		
		Intent intent = getIntent();
		long bookId = intent.getLongExtra(AudioService.BOOK_ID_EXTRA, 0);
		BookmarksFragment fragment = (BookmarksFragment) getSupportFragmentManager().findFragmentById(R.id.bookamarks_fragment);
		fragment.setLibrary(mLibrary);
		fragment.setBookId(bookId);
	}
	
	@Override
    protected void onStart() {
        super.onStart();
	}
	
	@Override
    protected void onStop() {
        super.onStop();
    }
	
	@Override
	public Object onRetainCustomNonConfigurationInstance() {
		mRetained = true;
        return mLibrary;
    }
		
	@Override
	public void onDestroy() { 
		if(!mRetained)
			mLibrary.close();
		super.onDestroy();
	}

	@Override
	public void openBookmark(long id, int position) {
		Intent intent = new Intent();
		intent.putExtra(AudioService.CHAPTER_ID_EXTRA , id);
		intent.putExtra(AudioService.CHAPTER_PROGRESS_EXTRA , position);
		setResult(RESULT_OK, intent);
		finish();
	}
}

