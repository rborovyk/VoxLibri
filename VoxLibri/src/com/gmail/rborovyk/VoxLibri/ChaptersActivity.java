package com.gmail.rborovyk.VoxLibri;

import android.content.Intent;
import android.database.SQLException;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.widget.Toast;

public class ChaptersActivity extends FragmentActivity 
	implements ChaptersFragment.ChaptersListener {
	
	//private static final String LOGTAG = "ChaptersActivity";
	
	private LibraryDB mLibrary;
	private boolean mRetained = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.chapters);
		
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
		ChaptersFragment fragment = (ChaptersFragment) getSupportFragmentManager().findFragmentById(R.id.chapters_fragment);
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
	public void openChapter(long id) {
		Intent intent = new Intent();
		intent.putExtra(AudioService.CHAPTER_ID_EXTRA , id);
		setResult(RESULT_OK, intent);
		finish();
	}
}
