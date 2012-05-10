package com.gmail.rborovyk.VoxLibri;

import com.gmail.rborovyk.content.SimpleCursorLoader;
import com.gmail.rborovyk.ui.OnDialogListener;
import com.gmail.rborovyk.ui.TextQueryDialogFragment;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
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
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class BookmarksFragment extends ListFragment {
	private static final String LOGTAG = "BookmarksFragment";
	private static final String RENAME_TAG = "rename_bookmark";
	private static final int DATA_LOADER_ID = 0;
	
	private long mBookId;
	private LibraryDB mLibrary;
	private BookmarksListAdapter mListAdapter;
	private BookmarksListener mBookmarksListener;
	long mBookmarkId;
	long mBookmarkChapterId;
	int mBookmarkPos;
	String mBookmarkTitle; 
	
	public interface BookmarksListener {
		void openBookmark(long id, int position);
	}
	
	public void setBookId(long id) {
		mBookId = id;
	}
	
	public void setLibrary(LibraryDB library) {
		mLibrary = library;
	}
	
	@Override
	public void onAttach(Activity activity) {
		try{
			mBookmarksListener = (BookmarksListener) activity;
		} catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement BookmarksFragment.BookmarksListener");
        }
		super.onAttach(activity);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mListAdapter = new BookmarksListAdapter(
				getActivity().getApplicationContext(), R.layout.bookmarks_item, null, 
				new String[] {}, new int[] {});
		
		setListAdapter(mListAdapter);
		getLoaderManager().initLoader(DATA_LOADER_ID, null, mLoaderCallbacks);
		
		setHasOptionsMenu(true);
		
		if(null != savedInstanceState) {
			mBookmarkId = savedInstanceState.getLong("mBookmarkId");
			mBookmarkChapterId = savedInstanceState.getLong("mBookmarkChapterId");
			mBookmarkPos = savedInstanceState.getInt("mBookmarkPos");
			mBookmarkTitle = savedInstanceState.getString("mBookmarkTitle");
		}
		
		//Restore dialog callback after config. change
		final TextQueryDialogFragment dialog = (TextQueryDialogFragment) getFragmentManager().findFragmentByTag(RENAME_TAG);
		if(null != dialog) {
			dialog.setClickListener(getOnRenameListener(dialog));
		}
	}
		
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		registerForContextMenu(getListView());
		super.onActivityCreated(savedInstanceState);
	}

	@Override
	public void onPause() {
		super.onPause();
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putLong("mBookmarkId", mBookmarkId);
		outState.putLong("mBookmarkChapterId", mBookmarkChapterId);
		outState.putInt("mBookmarkPos", mBookmarkPos);
		outState.putString("mBookmarkTitle", mBookmarkTitle);
	}

	@Override
	public void onDestroy() {
		setListAdapter(null);
		mListAdapter = null;
		
		super.onDestroy();
	}
	
	@Override
	public void onListItemClick(ListView list, View view, int position, long id) {
		if(null == getSelectedData(position))
			return;
		onOpenClick();
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.bookmarks_menu, menu);
	}

	@Override
	public boolean onOptionsItemSelected (MenuItem item) {
		switch (item.getItemId()) {
		case R.id.bookmarks_menu_remove_all:
			onRemoveAllClick();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		MenuInflater inflater = getActivity().getMenuInflater();
		inflater.inflate(R.menu.bookmarks_context_menu, menu);

		AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfo;
		Cursor curs = getSelectedData(mi.position);
		if(null == curs)
			return;
		menu.setHeaderTitle(mBookmarkTitle);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.bookmarks_context_menu_open:
			onOpenClick();
			return true;
		case R.id.bookmarks_context_menu_rename:
			onRenameClick();
			return true;
		case R.id.bookmarks_context_menu_delete: 
			onRemoveClick();
			return true;
		}
		return super.onContextItemSelected(item);
	}

	//---------------------------------------------------------------------------
	private void onRemoveAllClick() {
		mLibrary.deleteAllBookmarks(mBookId);
		getLoaderManager().getLoader(DATA_LOADER_ID).onContentChanged();
	}
	
	private void onRemoveClick() {
		if(0 != mBookmarkId) {
			mLibrary.deleteBookmark(mBookmarkId);
			getLoaderManager().getLoader(DATA_LOADER_ID).onContentChanged();
		}
	}

	private void onOpenClick() {
		if(null != mBookmarksListener && 0 != mBookmarkChapterId)
			mBookmarksListener.openBookmark(mBookmarkChapterId, mBookmarkPos);
	}
	
	private void onRenameClick() {
		if(0 == mBookmarkId)
			return;
		
		final TextQueryDialogFragment dialog = new TextQueryDialogFragment();
		dialog.setTitle(R.string.bookmark_dialog_title);
		dialog.setLabel(R.string.bookmark_dialog_label);
		dialog.setQueryText(mBookmarkTitle);
		dialog.setClickListener(getOnRenameListener(dialog));
		dialog.show(getFragmentManager(), RENAME_TAG);
	}
	
	private OnDialogListener getOnRenameListener(final TextQueryDialogFragment dialog) {
		return new OnDialogListener() {
			@Override
			public void OnPositiveClick() {
				String title = dialog.getQueryText();
				mLibrary.updateBookmarkTitle(mBookmarkId, title);
				Log.d(LOGTAG, "title: "+title+" id: "+mBookmarkId);
				getLoaderManager().getLoader(DATA_LOADER_ID).onContentChanged();
			}
			
			@Override
			public void OnNegativeClick() {
				
			}
		};
	}
	
	private Cursor getSelectedData(int position) {
		Cursor curs = mListAdapter.getCursor();
		if(null != curs) {
			curs.moveToPosition(position);
			mBookmarkId = curs.getLong(curs.getColumnIndexOrThrow(LibraryDB.BOOKMARK_TABLE_ID));
			mBookmarkChapterId = curs.getLong(curs.getColumnIndexOrThrow(LibraryDB.BOOKMARK_TABLE_CHAPTER_ID));
			mBookmarkPos = curs.getInt(curs.getColumnIndexOrThrow(LibraryDB.BOOKMARK_TABLE_CHAPTER_POSITION));
			mBookmarkTitle = curs.getString(curs.getColumnIndexOrThrow(LibraryDB.BOOKMARK_TABLE_TITLE));
			
			Log.d(LOGTAG, "title: "+mBookmarkTitle+" id: "+mBookmarkId);
		}
		return curs;
	}
	
	//---------------------------------------------------------------------------
	static class BookmarksListAdapter extends SimpleCursorAdapter { 
		private int mTitleIdx;
		private int mChapterTitleIndex;
		private int mPositionIndex;
		private final String mSecondsString;
		private Cursor mCursor;

		static class ViewHolder {
			TextView title;
			TextView chapter;
			TextView position;
		}

		BookmarksListAdapter(Context context, int layout, Cursor cursor, String[] from, int[] to) {
			super(context, layout, cursor, from, to);
			mSecondsString = context.getResources().getString(R.string.bookmarks_seconds);
			getColumnIndices(cursor);
		}
		
		public Cursor getCursor() {
			return mCursor;
		}

		private void getColumnIndices(Cursor cursor) {
			mCursor = cursor;
			if (cursor != null) {
				mTitleIdx = cursor.getColumnIndexOrThrow(LibraryDB.BOOKMARK_TABLE_TITLE);
				mChapterTitleIndex = cursor.getColumnIndexOrThrow(LibraryDB.BOOKMARK_TABLE_CHAPTER_TITLE);
				mPositionIndex = cursor.getColumnIndexOrThrow(LibraryDB.BOOKMARK_TABLE_CHAPTER_POSITION);
			}
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			View v = super.newView(context, cursor, parent);
			ViewHolder vh = new ViewHolder();
			vh.title = (TextView) v.findViewById(R.id.bookmarks_item_title);
			vh.chapter = (TextView) v.findViewById(R.id.bookmarks_item_chapter);
			vh.position = (TextView) v.findViewById(R.id.bookmarks_item_pos);
			v.setTag(vh);
			return v;
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			ViewHolder vh = (ViewHolder) view.getTag();
			vh.title.setText(LocalUtils.fixEmpty(cursor.getString(mTitleIdx), ""));
			vh.chapter.setText(LocalUtils.fixEmpty(cursor.getString(mChapterTitleIndex), ""));
			vh.position.setText(cursor.getInt(mPositionIndex)/1000+mSecondsString);
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
					Cursor result = mLibrary.getAllBookmarks(mBookId);
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
}
