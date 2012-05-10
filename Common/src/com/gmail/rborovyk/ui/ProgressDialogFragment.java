package com.gmail.rborovyk.ui;

import java.util.Set;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;

public class ProgressDialogFragment extends DialogFragment {
	public static final String TITLE_ARG = "title"; //String
	public static final String TITLE_ID_ARG = "titleid"; //int
	public static final String MESSAGE_ARG = "message"; //String
	public static final String INDETERMINATE_ARG = "indet"; //boolean
	public static final String NON_CANCELLABLE_ARG = "noncanc"; //boolean
	public static final String ICON_ID_ARG = "iconid"; //int
	
	@Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
		Bundle args = getArguments();
		Set<String> keySet = args.keySet();
		
		final ProgressDialog dialog = new ProgressDialog(getActivity());
		
		if(keySet.contains(TITLE_ARG))
			dialog.setTitle(args.getString(TITLE_ARG));
		else if(keySet.contains(TITLE_ID_ARG))
			dialog.setTitle(args.getInt(TITLE_ID_ARG));
		
		if(keySet.contains(MESSAGE_ARG))
			dialog.setMessage(args.getString(MESSAGE_ARG));
		
		if(keySet.contains(INDETERMINATE_ARG))
			dialog.setIndeterminate(true);
		
		if(keySet.contains(NON_CANCELLABLE_ARG))
			dialog.setCancelable(false);
		
		if(keySet.contains(ICON_ID_ARG))
			dialog.setIcon(args.getInt(ICON_ID_ARG));
		
		return dialog;
	}
	
	//--SIMPLE SETTERS--------------------------------------------------------------------------------
	public static ProgressDialogFragment getDialog(String title, String message, boolean indeterminate) {
		ProgressDialogFragment dialog = new ProgressDialogFragment();
		dialog.setTitle(title);
		dialog.setMessage(message);
		dialog.setIndeterminate(indeterminate);
		return dialog;
	}
	
	public void setTitle(String title) {
		Bundle args = getCreateArgs();
		args.putString(TITLE_ARG, title);
		args.remove(TITLE_ID_ARG);
	}

	public void setTitle(int id) {
		Bundle args = getCreateArgs();
		args.putInt(TITLE_ID_ARG, id);
		args.remove(TITLE_ARG);
	}

	public void setMessage(String message) {
		Bundle args = getCreateArgs();
		args.putString(MESSAGE_ARG, message);
	}

	public void setIndeterminate(boolean flag) {
		Bundle args = getCreateArgs();
		if(flag)
			args.putBoolean(INDETERMINATE_ARG, true);
		else
			args.remove(INDETERMINATE_ARG);
	}

	public void setCancelable(boolean flag) {
		Bundle args = getCreateArgs();
		if(flag)
			args.remove(NON_CANCELLABLE_ARG);
		else
			args.putBoolean(NON_CANCELLABLE_ARG, true);
	}

	public void setIcon(int id) {
		Bundle args = getCreateArgs();
		args.putInt(ICON_ID_ARG, id);
	}

	private Bundle getCreateArgs() {
		Bundle args = getArguments();
		if(null == args){
			args = new Bundle();
			setArguments(args);
		}

		return args;
	}
}
