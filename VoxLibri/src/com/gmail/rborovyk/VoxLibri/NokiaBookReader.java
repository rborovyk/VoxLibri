package com.gmail.rborovyk.VoxLibri;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;

import android.util.Log;

public class NokiaBookReader {
	private static final String LOGTAG = "NokiaBookReader";
	
	private static final int DATA_UNDEFINED = 0;
	private static final int DATA_BOOK_TITLE = 1;
	private static final String DATA_BOOK_TITLE_STR = "#BOOK";
	private static final int DATA_BOOK_COVER = 2;
	private static final String DATA_BOOK_COVER_STR = "#PIC";
	private static final int DATA_BOOK_TRACK = 3;
	private static final String DATA_BOOK_TRACK_STR = "#TRACKS";
	private static final int DATA_BOOK_CHAPTER = 4;
	private static final String DATA_BOOK_CHAPTER_STR = "#CHAPTERS";
	private static final int DATA_FILE_VERSION = 5;
	private static final String DATA_FILE_VERSION_STR = "#VERSION";
	private static final int DATA_CONTENT_INFO = 6;
	private static final String DATA_CONTENT_INFO_STR = "#CONTENT_INFO";
	private int mDataType = DATA_UNDEFINED;
	
	private TreeMap<String, Integer> mTracks = new TreeMap<String, Integer>();
	private String mBaseDir;
	
	public static boolean isNokiaAudioBook(File directory) {
		return null != getInxFileName(directory);
	}
	
	public AudioBook read(File directory) {
		AudioBook book = new AudioBook();
		mTracks.clear();
		try {
			String data;
			mBaseDir = directory.getAbsolutePath()+"/";
			String inxFileName = getInxFileName(directory);
			if(null == inxFileName){
				return null; 
			}
			
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inxFileName), "UTF-16LE"));
			try{
				data = br.readLine();
				if(null == data)
					return null;
				data = data.substring(data.indexOf("#")); //skip BOM
				do {
					Log.d(LOGTAG, "data: "+data);
					if(data.startsWith("#") && !data.endsWith(";")){
						setDataType(data);
					}else{
						switch(mDataType){
						case DATA_BOOK_TITLE:
							book.Title = data.substring(0, data.length()-1).trim();
							if(StringUtils.isBlank(book.Title))
								book.Title = "Undefined";
							Log.d(LOGTAG, "DATA_BOOK_TITLE: "+book.Title);
							break;
						case DATA_BOOK_COVER:
							String cover = data.substring(0, data.length()-1).trim();
							if(StringUtils.isBlank(cover))
								book.Cover = null;
							else
								book.Cover = mBaseDir+cover;
							Log.d(LOGTAG, "DATA_BOOK_TITLE: "+book.Cover);
							break;
						case DATA_BOOK_TRACK:
							processTrack(data);
							break;
						case DATA_BOOK_CHAPTER:
							processChapter(book, data);
							break;
						case DATA_FILE_VERSION:
							Log.d(LOGTAG, "DATA_FILE_VERSION: "+data);
							break;
						case DATA_CONTENT_INFO:
							Log.d(LOGTAG, "DATA_CONTENT_INFO: "+data);
							break;
						}
					}
				}while ((data = br.readLine()) != null);
			} catch (Exception e) {
				Log.d(LOGTAG, "Exception: "+e.toString());
			}finally {
				br.close();
			}
		} catch (IOException e) {
			Log.d(LOGTAG, "IOException: "+e.toString());
		} catch (Exception e) {
			Log.d(LOGTAG, "Exception: "+e.toString());
		} 
		
		if(!validate(book))
			return null;
		return book;
	}
	
	public boolean validate(AudioBook book) {
		if(null == book)
			return false;
		if(book.Chapters.size() == 0)
			return false;
		for(AudioBook.Chapter c : book.Chapters){
			if(StringUtils.isBlank(c.MediaFileName))
				return false;
		}
		return true;
	}

	private void processTrack(String data) {
		int divider = data.indexOf(':');
		String name = data.substring(0, divider).trim();
		++divider;
		int duration = Integer.parseInt(data.substring(divider, data.length()-1));
		mTracks.put(name, duration);
	}

	private void processChapter(AudioBook book, String data) {
		AudioBook.Chapter chapter = new AudioBook.Chapter();
		int start = 0;
		int end = data.indexOf(':');
		String trackName = data.substring(start, end).trim();
		chapter.MediaFileName = mBaseDir + trackName;

		if(mTracks.containsKey(trackName)){
			chapter.Duration = mTracks.get(trackName)*1000;
		}

		start = end+1;
		end = data.indexOf(':', start);
		//"0s" data - no idea what's that

		start = end+1;
		end = data.indexOf(':', start);
		chapter.SeqNo = Integer.parseInt(data.substring(start, end));

		start = end+1;
		end = data.length()-1;
		chapter.Title = data.substring(start, end-1).trim();
		
		book.Chapters.add(chapter);
		
		Log.d(LOGTAG, "Chapter "+chapter.Title+":"+chapter.Duration+" Media:"+chapter.MediaFileName);
	}

	private void setDataType(String data) {
		if(DATA_BOOK_TITLE_STR.equals(data))
			mDataType = DATA_BOOK_TITLE;
		else if(DATA_BOOK_COVER_STR.equals(data))
			mDataType = DATA_BOOK_COVER;
		else if(DATA_BOOK_TRACK_STR.equals(data))
			mDataType = DATA_BOOK_TRACK;
		else if(DATA_BOOK_CHAPTER_STR.equals(data))
			mDataType = DATA_BOOK_CHAPTER;
		else if(DATA_FILE_VERSION_STR.equals(data))
			mDataType = DATA_FILE_VERSION;
		else if(DATA_CONTENT_INFO_STR.equals(data))
			mDataType = DATA_CONTENT_INFO;
		Log.d(LOGTAG, "setDataType: "+mDataType);
	}

	private static String getInxFileName(File directory) {
		File[] list = directory.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.isFile() && pathname.getName().toLowerCase().endsWith("inx");
			}
		});
		
		if(null == list || list.length <= 0){
			Log.d(LOGTAG, "getInxFileName: no inx file found");
			return null;
		}
		Log.d(LOGTAG, "getInxFileName: "+list[0].getAbsolutePath());
		return list[0].getAbsolutePath();
	}
}
