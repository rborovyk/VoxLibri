package com.gmail.rborovyk.VoxLibri;

import java.util.Locale;
import java.util.Formatter;

import org.apache.commons.lang3.StringUtils;


public class LocalUtils {
	private static StringBuilder sFormatBuilder = new StringBuilder();
    private static Formatter sFormatter = new Formatter(sFormatBuilder, Locale.getDefault());
    private static final Object[] sTimeArgs = new Object[5];
    
	public static String secToTimeString(long secs) {
        sFormatBuilder.setLength(0);

        final Object[] timeArgs = sTimeArgs;
        timeArgs[0] = secs / 3600;
        timeArgs[1] = secs / 60;
        timeArgs[2] = (secs / 60) % 60;
        timeArgs[3] = secs;
        timeArgs[4] = secs % 60;

        return sFormatter.format("%2$d:%5$02d", timeArgs).toString();
    }
	
	public static String fixEmpty(String label, String textIfEmpty) {
		if(StringUtils.isBlank(label))
			return textIfEmpty;
		return label;
	}
}
