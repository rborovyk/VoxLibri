package com.gmail.rborovyk.VoxLibri;

import java.util.ArrayList;

import android.util.Log;

public class AudioBook {
	private static final String LOGTAG = "AudioBook";
	public long Id;
	public String Title;
	public String Cover;
	public long ChapterId;
	public int ChapterProgress;
	public ArrayList<Chapter> Chapters = new ArrayList<AudioBook.Chapter>();
	
	public long getChapterId(int chapterIdx) {
		return Chapters.get(chapterIdx).Id;
	}
	
	public CharSequence getChapterTitle(int chapterIdx) {
		return Chapters.get(chapterIdx).Title;
	}
	
	public int getChapterSeqNo(int chapterIdx) {
		return Chapters.get(chapterIdx).SeqNo;
	}
	
	public int getChapterIdx(long id) {
		for(int idx = 0; idx<Chapters.size(); ++idx) {
			if(Chapters.get(idx).Id == id) {
				return idx;
			}
		}
		return 0;
	}
	
	static public class Chapter {
		public long Id;
		public int SeqNo; 
		public String Title;
		public String MediaFileName;
		public int Duration;
		
		public Chapter(String title, String mediaFileName, int duration) {
			Title = title;
			MediaFileName = mediaFileName; 
			Duration = duration;
		}
		
		public Chapter() {
			this(null, null, 0); 
		}
		
		public void dump() {
			Log.d(LOGTAG, "Chapter Id: "+Id+"/"+Title+":"+Duration+" msec");
			Log.d(LOGTAG, "    Media: "+MediaFileName);
		}
	}
	
	public static class Bookmark {
		public long Id;
		public long BookId; 
		public long ChapterId;
		public int SeqNo; 
		public String Title;
		public String ChapterTitle;
		public int Position;
		
		public void MakeAutoTitle() {
			Title = ChapterTitle+":"+(Position/1000)+" sec.";
		}
		
		public void dump() {Log.d(LOGTAG, "Bookmark Id: "+Id+"/"+Title+":"+Position+" msec");}
	}
	
	public void dump() {
		Log.d(LOGTAG, "Book: "+Id+"/"+Title);
		Log.d(LOGTAG, "Last Chapter: "+ChapterId+"/"+ChapterProgress+" msec");
		for(Chapter c : Chapters) {
			c.dump();
		}
	}
}
