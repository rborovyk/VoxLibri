package com.gmail.rborovyk.VoxLibri;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class LibraryDB {
	//---CONSTS---------------------------------------------------
	private static final String TAG = "LibraryDB";
	
	private static final String DATABASE_NAME = "audio_library";
    private static final int DATABASE_VERSION = 1;
    private static final String BOOK_TABLE = "book";
    private static final String CHAPTER_TABLE = "chapter";
    private static final String BOOKMARK_TABLE = "bookmark";
    
    public static final String BOOK_TABLE_ID = "_id";
    public static final String BOOK_TABLE_TITLE = "title";
    public static final String BOOK_TABLE_COVER = "cover";
    public static final String BOOK_TABLE_CHAPTER_ID = "chapterid";
    public static final String BOOK_TABLE_CHAPTER_PROGRESS = "chapterprogress";
    
    public static final String CHAPTER_TABLE_ID = "_id";
    public static final String CHAPTER_TABLE_BOOKID = "bookid";
    public static final String CHAPTER_TABLE_TITLE = "title";
    public static final String CHAPTER_TABLE_FILENAME = "filename";
    public static final String CHAPTER_TABLE_DURATION = "duration";
    public static final String CHAPTER_TABLE_SEQNO = "seqno";
    
    public static final String BOOKMARK_TABLE_ID = "_id";
    public static final String BOOKMARK_TABLE_TITLE = "title";
    public static final String BOOKMARK_TABLE_BOOKID = "bookid";
    public static final String BOOKMARK_TABLE_CHAPTER_ID = "chapterid";
    public static final String BOOKMARK_TABLE_CHAPTER_TITLE = "chaptertitle";
    public static final String BOOKMARK_TABLE_CHAPTER_SEQNO = "seqno";
    public static final String BOOKMARK_TABLE_CHAPTER_POSITION = "chapterposition";
    
    private static final String DATABASE_CREATE1 =
            "create table book (_id integer primary key autoincrement, title text not null, cover text null, chapterprogress integer null);";
    private static final String DATABASE_CREATE2 =
            "create table chapter (_id integer primary key autoincrement, bookid integer not null, title text not null, filename text not null, duration int not null, seqno integer not null, FOREIGN KEY(bookid) REFERENCES book(_id) ON DELETE CASCADE);";
    private static final String DATABASE_CREATE3 =
            "alter table book add chapterid integer null REFERENCES chapter(_id)";
    private static final String DATABASE_CREATE4 =
            "create table bookmark (_id integer primary key autoincrement, bookid integer not null REFERENCES book(_id) ON DELETE CASCADE, chapterid integer null REFERENCES chapter(_id), chaptertitle text not null, seqno integer not null, chapterposition integer null, title text not null);";
    
    private DBHelper mDBHelper;
    private SQLiteDatabase mDB;
    
    
    public LibraryDB(Context context) {
    	mDBHelper = new DBHelper(context);
    }
    
    public LibraryDB open() throws SQLException {
    	try{
    		mDB = mDBHelper.getWritableDatabase();
    	}catch(SQLException ex) {
    		Log.d(TAG, "Error opening DB: "+ex.getStackTrace());
    		throw ex;
    	}
    	return this;
    }
    
    public void close() {
    	mDBHelper.close();
    	mDB = null;
    }
    
    public void addBook(AudioBook book) {
    	ContentValues values = new ContentValues();
    	values.put(BOOK_TABLE_TITLE, book.Title);
    	if(0 != book.ChapterId) {
    		values.put(BOOK_TABLE_CHAPTER_ID, book.ChapterId);
    		values.put(BOOK_TABLE_CHAPTER_PROGRESS, book.ChapterProgress);
    	}
    	
    	if(null != book.Cover)
			values.put(BOOK_TABLE_COVER, book.Cover);
    	
    	book.Id = mDB.insert(BOOK_TABLE, null, values);
    	
    	for(AudioBook.Chapter chapter : book.Chapters) {
    		values.clear();
    		values.put(CHAPTER_TABLE_BOOKID, book.Id);
    		values.put(CHAPTER_TABLE_TITLE, chapter.Title);
    		values.put(CHAPTER_TABLE_FILENAME, chapter.MediaFileName);
    		values.put(CHAPTER_TABLE_DURATION, chapter.Duration);
    		values.put(CHAPTER_TABLE_SEQNO, chapter.SeqNo);
    		chapter.Id = mDB.insert(CHAPTER_TABLE, null, values);
    	}
    }
    
    public void deleteBook(AudioBook book) {
        deleteBook(book.Id);
    }
    
    public void deleteBook(long bookId) {
        mDB.delete(BOOK_TABLE, BOOK_TABLE_ID + "=" + bookId, null);
    }
    
    public Cursor getAllBooks() {
    	return mDB.query(BOOK_TABLE, 
    			new String[] {BOOK_TABLE_ID, BOOK_TABLE_TITLE, BOOK_TABLE_COVER}, 
    			null, 
    			null, 
    			null, 
    			null, 
    			null);
    }
    
    public AudioBook LoadBook(long id) {
    	if(0 == id)
    		return null;
    	
    	AudioBook book = new AudioBook();
    	book.Id = id;
    	LoadBook(book);
    	return book;
    }
    
    public void LoadBook(AudioBook book) throws SQLException {
        Cursor cursor = mDB.query(BOOK_TABLE, new String[] {
        		BOOK_TABLE_ID,
        		BOOK_TABLE_TITLE,
        		BOOK_TABLE_COVER,
        		BOOK_TABLE_CHAPTER_ID,
        		BOOK_TABLE_CHAPTER_PROGRESS}, 
        		BOOK_TABLE_ID + "=" + book.Id, 
        		null,
        		null, 
        		null, 
        		null, 
        		null);
        if (null == cursor)
        	throw new SQLException("LoadBook: book not found, id: "+ book.Id);
        
        cursor.moveToFirst();
        book.Title = cursor.getString(cursor.getColumnIndex(BOOK_TABLE_TITLE));
        book.Cover = cursor.getString(cursor.getColumnIndex(BOOK_TABLE_COVER));
        book.ChapterId = cursor.getInt(cursor.getColumnIndex(BOOK_TABLE_CHAPTER_ID));
        book.ChapterProgress = cursor.getInt(cursor.getColumnIndex(BOOK_TABLE_CHAPTER_PROGRESS));
        
        cursor = mDB.query(CHAPTER_TABLE, new String[] {
        		CHAPTER_TABLE_ID,
        		CHAPTER_TABLE_TITLE, 
        		CHAPTER_TABLE_FILENAME,
        		CHAPTER_TABLE_DURATION,
        		CHAPTER_TABLE_SEQNO}, 
        		CHAPTER_TABLE_BOOKID + "=" + book.Id, 
        		null,
        		null, 
        		null, 
        		CHAPTER_TABLE_SEQNO, 
        		null);
        if (null == cursor)
        	throw new SQLException("LoadBook: no chapters found for book " + book.Title + ": " + book.Id);
        
        cursor.moveToFirst();
        book.Chapters.clear();
        int chapterId = cursor.getColumnIndex(CHAPTER_TABLE_ID);
        int chapterTitle = cursor.getColumnIndex(CHAPTER_TABLE_TITLE);
        int chapterMedia = cursor.getColumnIndex(CHAPTER_TABLE_FILENAME);
        int chapterDuration = cursor.getColumnIndex(CHAPTER_TABLE_DURATION);
        int chapterSeqno = cursor.getColumnIndex(CHAPTER_TABLE_SEQNO);
        do {
        	AudioBook.Chapter chapter = new AudioBook.Chapter();
        	chapter.Id = cursor.getInt(chapterId);
        	chapter.Title = cursor.getString(chapterTitle);
        	chapter.MediaFileName = cursor.getString(chapterMedia);
        	chapter.Duration = cursor.getInt(chapterDuration);
        	chapter.SeqNo = cursor.getInt(chapterSeqno);
        	book.Chapters.add(chapter);
        } while(cursor.moveToNext());
    }
    
    public void updateBookProgress(AudioBook book) {
    	ContentValues values = new ContentValues();
    	if(0 != book.ChapterId) {
    		values.put(BOOK_TABLE_CHAPTER_ID, book.ChapterId);
    		values.put(BOOK_TABLE_CHAPTER_PROGRESS, book.ChapterProgress);
    	}else{
    		values.putNull(BOOK_TABLE_CHAPTER_ID);
    		values.putNull(BOOK_TABLE_CHAPTER_PROGRESS);
    	}
    	book.Id = mDB.update(BOOK_TABLE, values, BOOK_TABLE_ID + "=" + book.Id, null);
    }
    
  //----chapters-----------------------------------------
    public Cursor getAllChapters(long bookId) {
    	Cursor c = mDB.query(CHAPTER_TABLE, 
    			new String[] {
    				CHAPTER_TABLE_ID, 
    				CHAPTER_TABLE_TITLE, 
    				CHAPTER_TABLE_SEQNO}, 
    				CHAPTER_TABLE_BOOKID +"="+bookId, 
    			null, 
    			null, 
    			null, 
    			CHAPTER_TABLE_SEQNO);
    	Log.d(TAG, "getAllChapters: " + c.getCount());
    	return c;
    }
    
    //----bookmark-----------------------------------------
    public Cursor getAllBookmarks(long bookId) {
    	Cursor c = mDB.query(BOOKMARK_TABLE, 
    			new String[] {
    				BOOKMARK_TABLE_ID, 
    				BOOKMARK_TABLE_TITLE, 
    				BOOKMARK_TABLE_CHAPTER_ID,
    				BOOKMARK_TABLE_CHAPTER_TITLE, 
    				BOOKMARK_TABLE_CHAPTER_SEQNO, 
    				BOOKMARK_TABLE_CHAPTER_POSITION}, 
    				BOOKMARK_TABLE_BOOKID +"="+bookId, 
    			null, 
    			null, 
    			null, 
    			BOOKMARK_TABLE_CHAPTER_SEQNO +","+BOOKMARK_TABLE_CHAPTER_POSITION);
    	return c;
    }
    
    public AudioBook.Bookmark loadBookmark(long id) throws SQLException {
    	AudioBook.Bookmark bookmark = new AudioBook.Bookmark();
    	bookmark.Id = id;

    	Cursor cursor = mDB.query(BOOKMARK_TABLE, new String[] {
        		BOOKMARK_TABLE_ID,
        		BOOKMARK_TABLE_TITLE, 
        		BOOKMARK_TABLE_BOOKID,
        		BOOKMARK_TABLE_CHAPTER_ID,
        		BOOKMARK_TABLE_CHAPTER_TITLE,
        		BOOKMARK_TABLE_CHAPTER_SEQNO,
        		BOOKMARK_TABLE_CHAPTER_POSITION}, 
        		BOOKMARK_TABLE_ID + "=" + bookmark.Id, 
        		null,
        		null, 
        		null, 
        		null, 
        		null);
        if (null == cursor)
        	throw new SQLException("LoadBookmark: bookmark not found, id: "+ bookmark.Id);
        
        cursor.moveToFirst();
        bookmark.Title = cursor.getString(cursor.getColumnIndex(BOOKMARK_TABLE_TITLE));
        bookmark.BookId = cursor.getInt(cursor.getColumnIndex(BOOKMARK_TABLE_BOOKID));
        bookmark.ChapterId = cursor.getInt(cursor.getColumnIndex(BOOKMARK_TABLE_CHAPTER_ID));
        bookmark.ChapterTitle = cursor.getString(cursor.getColumnIndex(BOOKMARK_TABLE_CHAPTER_TITLE));
        bookmark.SeqNo = cursor.getInt(cursor.getColumnIndex(BOOKMARK_TABLE_CHAPTER_SEQNO));
        bookmark.Position = cursor.getInt(cursor.getColumnIndex(BOOKMARK_TABLE_CHAPTER_POSITION));
    	
    	return bookmark;
    }
    
    public void addBookmark(AudioBook.Bookmark bookmark) {
    	ContentValues values = new ContentValues();
    	values.put(BOOKMARK_TABLE_TITLE, bookmark.Title);
    	values.put(BOOKMARK_TABLE_BOOKID, bookmark.BookId);
    	values.put(BOOKMARK_TABLE_CHAPTER_ID, bookmark.ChapterId);
    	values.put(BOOKMARK_TABLE_CHAPTER_SEQNO, bookmark.SeqNo);
    	values.put(BOOKMARK_TABLE_CHAPTER_TITLE, bookmark.ChapterTitle);
    	values.put(BOOKMARK_TABLE_CHAPTER_POSITION, bookmark.Position);
    	
    	bookmark.Id = mDB.insert(BOOKMARK_TABLE, null, values);
    }
    
    public void deleteBookmark(long id) {
        mDB.delete(BOOKMARK_TABLE, BOOKMARK_TABLE_ID + "=" + id, null);
    }
    
    public void deleteAllBookmarks(long bookId) {
        mDB.delete(BOOKMARK_TABLE, BOOKMARK_TABLE_BOOKID + "=" + bookId, null);
    }
    
    public void updateBookmarkTitle(long id, String title) {
    	ContentValues values = new ContentValues();
    	values.put(BOOKMARK_TABLE_TITLE, title);
    	mDB.update(BOOKMARK_TABLE, values, BOOKMARK_TABLE_ID+"="+id, null);
	} 
    
    private static class DBHelper extends SQLiteOpenHelper {
    	DBHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) throws SQLException {
        	try{
        		db.execSQL(DATABASE_CREATE1);
        		db.execSQL(DATABASE_CREATE2);
        		db.execSQL(DATABASE_CREATE3);
        		db.execSQL(DATABASE_CREATE4);
        	}catch(SQLException ex) {
        		Log.d(TAG, "db.onCreate exception: " + ex.toString());
        		throw ex;
        	}
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) throws SQLException {
            Log.d(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);
        }
    }

	   
}
