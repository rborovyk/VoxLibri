package com.gmail.rborovyk.content;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class SimpleSQLiteCursorLoader extends SimpleCursorLoader {
	private SQLiteDatabase mDB;
	private String mTable;
	private String[] mColumns;
	private String mSelection;
	private String[] mSelectionArgs;
	private String mSortOrder;
		
	public SimpleSQLiteCursorLoader(Context context, SQLiteDatabase db, String table, String[] columns, 
			String selection, String[] selectionArgs, String sortOrder) {
		super(context);
		mDB = db;
		mTable = table;
		mColumns = columns;
        mSelection = selection;
        mSelectionArgs = selectionArgs;
        mSortOrder = sortOrder;
	}

	@Override
    public Cursor performLoad() {
        Cursor cursor = mDB.query(mTable, mColumns, mSelection, 
        		mSelectionArgs, null, null, mSortOrder);
        return cursor;
    }
}
