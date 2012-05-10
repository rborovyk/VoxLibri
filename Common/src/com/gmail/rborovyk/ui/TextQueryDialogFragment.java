package com.gmail.rborovyk.ui;

import java.util.Set;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

public class TextQueryDialogFragment extends DialogFragment {
	public static final String TITLE_ARG = "title";
	public static final String TITLE_ID_ARG = "titleid";
	public static final String LABEL_ARG = "label";
	public static final String LABEL_ID_ARG = "labelid";
	public static final String DEFAULT_TEXT_ARG = "text";
	public static final String DEFAULT_TEXT_ID_ARG = "textid";
	public static final String ICON_ID_ARG = "iconid";
	
	private OnDialogListener mClickListener;
	private EditText mTextWidget;

	public void setClickListener(OnDialogListener listener) {
		mClickListener = listener;
	}
	
	public String getQueryText() {
		return mTextWidget.getText().toString();
	}

	@Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
		Bundle args = getArguments();
		Set<String> keySet = args.keySet();
		LayoutInflater factory = LayoutInflater.from(getActivity());
		final View queryView = factory.inflate(R.layout.query_dialog_text, null);
		mTextWidget = (EditText) queryView.findViewById(R.id.query_dialog_text);
		final TextView textlabel = (TextView) queryView.findViewById(R.id.query_dialog_text_label);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
		.setView(queryView)
		.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				if(null != mClickListener)
					mClickListener.OnPositiveClick();
				dialog.dismiss();
			}
		})
		.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				if(null != mClickListener)
					mClickListener.OnNegativeClick();
				dialog.cancel();
			}
		});
		
		if(keySet.contains(TITLE_ARG))
			builder.setTitle(args.getString(TITLE_ARG));
		else if(keySet.contains(TITLE_ID_ARG))
			builder.setTitle(args.getInt(TITLE_ID_ARG));
		
		if(keySet.contains(LABEL_ARG))
			textlabel.setText(args.getString(LABEL_ARG));
		else if(keySet.contains(LABEL_ID_ARG))
			textlabel.setText(args.getInt(LABEL_ID_ARG));
		
		if(keySet.contains(DEFAULT_TEXT_ARG))
			mTextWidget.setText(args.getString(DEFAULT_TEXT_ARG));
		else if(keySet.contains(DEFAULT_TEXT_ID_ARG))
			mTextWidget.setText(args.getInt(DEFAULT_TEXT_ID_ARG));
		
		if(keySet.contains(ICON_ID_ARG))
			builder.setIcon(args.getInt(ICON_ID_ARG));
		
		return builder.create();
	}

	//--SIMPLE SETTERS--------------------------------------------------------------------------------
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
	
	public void setLabel(String label) {
		Bundle args = getCreateArgs();
		args.putString(LABEL_ARG, label);
		args.remove(LABEL_ID_ARG);
	}
	
	public void setLabel(int id) {
		Bundle args = getCreateArgs();
		args.putInt(LABEL_ID_ARG, id);
		args.remove(LABEL_ARG);
	}
	
	public void setQueryText(String text) {
		Bundle args = getCreateArgs();
		args.putString(DEFAULT_TEXT_ARG, text);
		args.remove(DEFAULT_TEXT_ID_ARG);
	}
	
	public void setQueryText(int id) {
		Bundle args = getCreateArgs();
		args.putInt(DEFAULT_TEXT_ID_ARG, id);
		args.remove(DEFAULT_TEXT_ARG);
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
