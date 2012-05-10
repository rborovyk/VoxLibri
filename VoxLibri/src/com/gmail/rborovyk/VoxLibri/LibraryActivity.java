package com.gmail.rborovyk.VoxLibri;

import java.io.File;

import com.gmail.rborovyk.ui.FileDialog;
import com.gmail.rborovyk.ui.R;

import android.content.Intent;
import android.database.SQLException;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;
import android.widget.Toast;


public class LibraryActivity extends FragmentActivity 
	implements LibraryFragment.LibraryListener {
	//private static final String LOGTAG = "LibraryActivity";
	private static final int FILE_REQUEST = 1;
	
	private LibraryDB mLibrary;
	private boolean mRetained = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.library);
		
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
		
		LibraryFragment fragment = (LibraryFragment) getSupportFragmentManager().findFragmentById(R.id.library_fragment);
		fragment.setLibrary(mLibrary);
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
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch(requestCode){ 
		case FILE_REQUEST: 
			if(RESULT_OK != resultCode)
				return;
			String filePath = data.getStringExtra(FileDialog.RESULT_PATH_EXTRA);
			LibraryFragment fragment = (LibraryFragment) getSupportFragmentManager().findFragmentById(R.id.library_fragment);
			fragment.addBook(filePath);
			break; 
		}
	}

	@Override
	public void openBook(long id) {
		Intent intent = new Intent();
    	intent.putExtra(AudioService.BOOK_ID_EXTRA, id);
    	setResult(RESULT_OK, intent);
		finish();
	}

	@Override
	public void getNewBookPath() {
		File sdcard = Environment.getExternalStorageDirectory();
		Intent intent = new Intent(this, FileDialog.class);
		intent.putExtra(FileDialog.START_PATH_EXTRA, sdcard.getAbsolutePath());
		intent.putExtra(FileDialog.SELECTION_MODE_EXTRA, FileDialog.MODE_OPEN);
		intent.putExtra(FileDialog.SELECTION_TYPE_EXTRA, FileDialog.TYPE_DIRECTORY);
		startActivityForResult(intent, FILE_REQUEST);
	}
}
