package com.gmail.rborovyk.VoxLibri;

import java.io.File;
import java.lang.ref.WeakReference;

import org.apache.commons.lang3.StringUtils;

import com.gmail.rborovyk.content.SimpleCursorLoader;
import com.gmail.rborovyk.ui.ProgressDialogFragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Toast;

public class LibraryFragment extends ListFragment {
	private static final String LOGTAG = "LibraryFragment";
	private static final int DATA_LOADER_ID = 0;
	private static final int COVER_LOADER_ID = 1;
	
	private LibraryDB mLibrary;
	private BookListAdapter mListAdapter;
	private LibraryListener mLibraryListener;
	private long mSelectedBookId;
    private Bitmap mDefaultCoverBitmap;
    private ImageLoader mCoverLoader;
    private Handler mHandler;
	
	public interface LibraryListener {
		void openBook(long id);
		void getNewBookPath();
	}
	
	public void setLibrary(LibraryDB library) {
		mLibrary = library;
	}
	
	@Override
	public void onAttach(Activity activity) {
		try{
			mLibraryListener = (LibraryListener) activity;
		} catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement LibraryFragment.OnOpenBook");
        }
		super.onAttach(activity);
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Resources r = getResources();
        mDefaultCoverBitmap = BitmapFactory.decodeResource(r, R.drawable.icon);
        
        mListAdapter = new BookListAdapter(getActivity(), R.layout.library_item, null);
        
        BitmapDrawable defaultBookCover = new BitmapDrawable(r, mDefaultCoverBitmap);
        defaultBookCover.setFilterBitmap(false);
        defaultBookCover.setDither(false);
				
		getLoaderManager().initLoader(DATA_LOADER_ID, null, mLoaderCallbacks);
		mCoverLoader = (ImageLoader) getLoaderManager().initLoader(COVER_LOADER_ID, null, mCoverLoaderCallbacks);
		mListAdapter.setCoverLoader(mCoverLoader, defaultBookCover);
		
		setListAdapter(mListAdapter);
		
		setHasOptionsMenu(true);
		
		if(null != savedInstanceState) {
			mSelectedBookId = savedInstanceState.getLong("mSelectedBookId");
		}
		
		mHandler = new Handler();
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		registerForContextMenu(getListView());
		super.onActivityCreated(savedInstanceState);
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putLong("mSelectedBookId", mSelectedBookId);
	}
	
	@Override
	public void onDestroy() {
		setListAdapter(null);
		mListAdapter = null;
		super.onDestroy();
	}
	
	@Override
	public void onListItemClick(ListView list, View view, int position, long id) {
		mSelectedBookId = id;
		onOpenBookClick();
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.library_menu, menu);
	}

	@Override
	public boolean onOptionsItemSelected (MenuItem item) {
		switch (item.getItemId()) {
		case R.id.library_menu_add:
			onAddBookClick();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		MenuInflater inflater = getActivity().getMenuInflater();
		inflater.inflate(R.menu.library_context_menu, menu);

		String bookTitle = null;
		AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfo;
		Cursor curs = mListAdapter.getCursor();
		if(null == curs)
			return;
		
		curs.moveToPosition(mi.position);
		mSelectedBookId = curs.getLong(curs.getColumnIndexOrThrow(LibraryDB.BOOK_TABLE_ID));
		bookTitle = curs.getString(curs.getColumnIndexOrThrow(LibraryDB.BOOK_TABLE_TITLE));
        
        if (StringUtils.isBlank(bookTitle)) {
            menu.setHeaderTitle(getString(R.string.library_selected_book_title)); 
        } else {
            menu.setHeaderTitle(bookTitle);
        }
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		 switch (item.getItemId()) {
         case R.id.library_context_menu_open: {
         	onOpenBookClick();
             return true;
         }
         case R.id.library_context_menu_delete: {
         	//TODO: stop if playing
         	onRemoveBookClick();
             return true;
         }
     }
     return super.onContextItemSelected(item);
	}
	
	private void onAddBookClick() {
		if(null != mLibraryListener) 
			mLibraryListener.getNewBookPath();
	}
	
	private void onRemoveBookClick() {
		mLibrary.deleteBook(mSelectedBookId);
		getLoaderManager().getLoader(DATA_LOADER_ID).onContentChanged();
	}
	
	private void onOpenBookClick() {
		Intent intent = new Intent();
    	intent.putExtra(AudioService.BOOK_ID_EXTRA, mSelectedBookId);
    	if(null != mLibraryListener)
			mLibraryListener.openBook(mSelectedBookId);
	}
	
	public void addBook(final String dirPath) {
//		File directory = new File(dirPath);
//		AudioBook book = null;
//		if(NokiaBookReader.isNokiaAudioBook(directory)){
//			book = new NokiaBookReader().read(directory);
//		}
//		
//		if(null == book)
//			return;
//		mLibrary.addBook(book);
		mHandler.post(new Runnable() {
		    @Override
		    public void run() {
		    	new AddBookTask(getActivity(), dirPath).execute((Object)null);
				getLoaderManager().getLoader(DATA_LOADER_ID).onContentChanged();
		    }
		});
		
	}
	

	//------------------------------------------------------------------------------------------------------
	static class BookListAdapter extends SimpleCursorAdapter {
        private Drawable mDefaultBookIcon;
        private final String mUnknownBookTitle;
        private final String mUndefinedDetails;
        
        private ImageLoader mImageLoader;
        private int mTitleIdx;
        private int mCoverIndex;
        
        static class ViewHolder {
            TextView title;
            TextView details;
            ImageView coverIcon;
        }

        BookListAdapter(Context context, int layout, Cursor cursor) {
            super(context, layout, cursor, new String[] {}, new int[] {});
                       
            mUnknownBookTitle = "Untitled book";//context.getString(R.string.unknown_book_name);
            mUndefinedDetails = "some info here";//context.getString(R.string.unknown_book_info);
            
            getColumnIndices(cursor);
        }
        
        public void setCoverLoader(ImageLoader loader, Drawable defaultCover ) {
        	mImageLoader = loader;
        	mDefaultBookIcon = defaultCover;
        }

        private void getColumnIndices(Cursor cursor) {
            if (cursor != null) {
            	mTitleIdx = cursor.getColumnIndexOrThrow(LibraryDB.BOOK_TABLE_TITLE);
                mCoverIndex = cursor.getColumnIndexOrThrow(LibraryDB.BOOK_TABLE_COVER);
            }
        }
        
        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
        	View v = super.newView(context, cursor, parent);
        	ViewHolder vh = new ViewHolder();
        	vh.title = (TextView) v.findViewById(R.id.library_item_title);
        	vh.details = (TextView) v.findViewById(R.id.library_item_details);
        	vh.coverIcon = (ImageView) v.findViewById(R.id.library_item_icon);
        	vh.coverIcon.setBackgroundDrawable(mDefaultBookIcon);
        	v.setTag(vh);
        	return v;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ViewHolder vh = (ViewHolder) view.getTag();

            String title = cursor.getString(mTitleIdx);
            if (StringUtils.isBlank(title)) {
            	title = mUnknownBookTitle;
            }
            vh.title.setText(title);
            
            vh.details.setText(mUndefinedDetails);
            
            String path = cursor.getString(mCoverIndex);
            
        	mImageLoader.loadImage(path, new WeakReference<ImageView>(vh.coverIcon));
        }
        
        @Override
        public void changeCursor(Cursor cursor) {
            getColumnIndices(cursor);
            super.changeCursor(cursor);
        }
    }
	
	private LoaderManager.LoaderCallbacks<Cursor> mLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {
		@Override
		public Loader<Cursor> onCreateLoader(int id, Bundle args) {
			return new SimpleCursorLoader(getActivity()) {
				@Override
				public Cursor performLoad() {
					Cursor result = mLibrary.getAllBooks();
					return result;
				}
			};
		}
		
		@Override
		public void onLoaderReset(Loader<Cursor> loader) {
			if(null != mListAdapter)
				mListAdapter.changeCursor(null);
		}
		
		@Override
		public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
			if(null != mListAdapter)
				mListAdapter.changeCursor(data);
		}
	};
	
	private LoaderManager.LoaderCallbacks<ImageLoader.Data> mCoverLoaderCallbacks = new 
				LoaderManager.LoaderCallbacks<ImageLoader.Data>() {
		@Override
		public Loader<ImageLoader.Data> onCreateLoader(int id, Bundle args) {
			ImageLoader loader = new ImageLoader(getActivity(), "CoverLoader");
			int width = getResources().getDimensionPixelSize(R.dimen.library_cover_width);
	        int height = getResources().getDimensionPixelSize(R.dimen.library_cover_height);
	        loader.setDimensions(width, height);
	        loader.enableCache(true);
	        return loader;
		}
		
		@Override
		public void onLoaderReset(Loader<ImageLoader.Data> loader) {
			
		}
		
		@Override
		public void onLoadFinished(Loader<ImageLoader.Data> loader, ImageLoader.Data data) {
			@SuppressWarnings("unchecked")
			ImageView coverIcon = ((WeakReference<ImageView>)data.data).get();
        	if(null != coverIcon && null != data.bitmap) {
        		Log.d(LOGTAG, "onLoadFinished: set bitmap");
        		coverIcon.setImageBitmap(data.bitmap);
        	}
		}
	};
	
	class AddBookTask extends AsyncTask<Object, Object, Object> {
		private static final String DIALOG_TAG = "add_book_dlg";
		
		private String mPath;
		private LibraryDB mLibrary;
		private Context mContext;
		private boolean mError = false;

		public AddBookTask(Context context, String path) {
			mContext = context.getApplicationContext();
			mPath = path;
		}
		
		@Override
		protected void onPreExecute() {
			String msg = mContext.getResources().getString(R.string.library_book_adding_wait);
			ProgressDialogFragment dlg = ProgressDialogFragment.getDialog("", msg, true);
			dlg.show(getFragmentManager(), DIALOG_TAG);
		}

		protected Object doInBackground(Object... params) {
			File directory = new File(mPath);
			AudioBook book = null;
			if(NokiaBookReader.isNokiaAudioBook(directory)){
				book = new NokiaBookReader().read(directory);
			}
			
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			if(null == book){
				mError = true;
				return null;
			}
			
			mLibrary = new LibraryDB(mContext);
			try {
				mLibrary.open();
				mLibrary.addBook(book);
				mLibrary.close();
			}catch(SQLException ex) {
				mError = true;
			}

			return null;
		}

		protected void onPostExecute(Object result) {
			final ProgressDialogFragment dialog = (ProgressDialogFragment) getFragmentManager().findFragmentByTag(DIALOG_TAG);
			if(null != dialog) {
				dialog.dismiss();
			}
			
			if(mError)
				Toast.makeText(mContext, R.string.library_book_add_error, Toast.LENGTH_SHORT);
			else
				Toast.makeText(mContext, R.string.library_book_added, Toast.LENGTH_SHORT);
		}
	}
}
