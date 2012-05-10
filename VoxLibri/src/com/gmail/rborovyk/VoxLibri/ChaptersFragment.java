package com.gmail.rborovyk.VoxLibri;

import com.gmail.rborovyk.content.SimpleCursorLoader;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
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

public class ChaptersFragment extends ListFragment {
	//private static final String LOGTAG = "ChaptersFragment";
	private static final int DATA_LOADER_ID = 0;
	
	//on config change and fragment kill mBookId and mLibrary will be set again by host activity
	private long mBookId;
	private LibraryDB mLibrary;
	private ChaptersListAdapter mListAdapter;
	private long mSelectedChapterId;
	private ChaptersListener mChaptersListener;
	
	public interface ChaptersListener {
		void openChapter(long id);
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
			mChaptersListener = (ChaptersListener) activity;
		} catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement ChaptersFragment.ChaptersListener");
        }
		super.onAttach(activity);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mListAdapter = new ChaptersListAdapter(
				getActivity(), R.layout.chapters_item, null, 
				new String[] {}, new int[] {});
		
		setListAdapter(mListAdapter);
		getLoaderManager().initLoader(DATA_LOADER_ID, null, mLoaderCallbacks);
		
		setHasOptionsMenu(true);
		
		if(null != savedInstanceState) {
			mSelectedChapterId = savedInstanceState.getLong("mSelectedChapterId");
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
		outState.putLong("mSelectedChapterId", mSelectedChapterId);
	}
	
	@Override
	public void onDestroy() {
		setListAdapter(null);
		mListAdapter = null;
		
		super.onDestroy();
	}
	
	@Override
	public void onListItemClick(ListView list, View view, int position, long id) {
		if(null == getData(position))
			return;
		onOpenClick();
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.chapters_menu, menu);
	}

	@Override
	public boolean onOptionsItemSelected (MenuItem item) {
		switch (item.getItemId()) {
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		MenuInflater inflater = getActivity().getMenuInflater();
		inflater.inflate(R.menu.chapters_context_menu, menu);

		AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfo;
		Cursor curs = getData(mi.position);
		if(null == curs)
			return;
		String title = curs.getString(curs.getColumnIndexOrThrow(LibraryDB.CHAPTER_TABLE_TITLE));
		menu.setHeaderTitle(title);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.chapters_context_menu_open:
			onOpenClick();
			return true;
		}
		return super.onContextItemSelected(item);
	}

	//---------------------------------------------------------------------------
	private void onOpenClick() {
		if(null != mChaptersListener)
			mChaptersListener.openChapter(mSelectedChapterId);
	}
	
	private Cursor getData(int position) {
		Cursor curs = mListAdapter.getCursor();
		if(null != curs) {
			curs.moveToPosition(position);
			mSelectedChapterId = curs.getLong(curs.getColumnIndexOrThrow(LibraryDB.CHAPTER_TABLE_ID));
		}
		
		return curs;
	}

	//---------------------------------------------------------------------------
	static class ChaptersListAdapter extends SimpleCursorAdapter { 
		private int mTitleIdx;
		private Cursor mCursor;

		static class ViewHolder {
			TextView title;
		}

		ChaptersListAdapter(Context context, int layout, Cursor cursor, String[] from, int[] to) {
			super(context, layout, cursor, from, to);
			getColumnIndices(cursor);
		}
		
		public Cursor getCursor() {
			return mCursor;
		}

		private void getColumnIndices(Cursor cursor) {
			mCursor = cursor;
			if (cursor != null) {
				mTitleIdx = cursor.getColumnIndexOrThrow(LibraryDB.CHAPTER_TABLE_TITLE);
			}
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			View v = super.newView(context, cursor, parent);
			ViewHolder vh = new ViewHolder();
			vh.title = (TextView) v.findViewById(R.id.chapters_item_title);
			v.setTag(vh);
			return v;
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			ViewHolder vh = (ViewHolder) view.getTag();
			vh.title.setText(LocalUtils.fixEmpty(cursor.getString(mTitleIdx), ""));
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
					Cursor result = mLibrary.getAllChapters(mBookId);
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
