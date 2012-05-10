package com.gmail.rborovyk.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class FileDialog extends ListActivity {
	private static final String ITEM_KEY = "key";
	private static final String ITEM_IMAGE = "image";
	private static final String ROOT = "/";

	public static final String START_PATH_EXTRA = "START_PATH";
	public static final String RESULT_PATH_EXTRA = "RESULT_PATH";
	public static final String SELECTION_MODE_EXTRA = "SELECTION_MODE";
	public static final String SELECTION_TYPE_EXTRA = "SELECTION_TYPE";
	
	public static final int MODE_CREATE = 0;
	public static final int MODE_OPEN = 1;
	public static final int TYPE_FILE = 0;
	public static final int TYPE_DIRECTORY = 1;

	private List<String> mPath = null;
	private TextView myPath;
	private EditText mFileName;
	private ArrayList<HashMap<String, Object>> mList;

	private Button mSelectButton;

	private LinearLayout mLayoutSelect;
	private LinearLayout mLayoutCreate;
	private InputMethodManager mInputManager;
	private String mParentPath;
	private String mCurrentPath = ROOT;

	private int mSelectionMode = MODE_CREATE;
	private int mSelectionType = TYPE_FILE;

	private File mSelectedFile;
	private HashMap<String, Integer> mLastPositions = new HashMap<String, Integer>();

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setResult(RESULT_CANCELED, getIntent());

		setContentView(R.layout.file_dialog_main);
		myPath = (TextView) findViewById(R.id.path);
		mFileName = (EditText) findViewById(R.id.fdEditTextFile);

		mInputManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

		mSelectButton = (Button) findViewById(R.id.fdButtonSelect);
		mSelectButton.setEnabled(false);
		mSelectButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (null != mSelectedFile) {
					getIntent().putExtra(RESULT_PATH_EXTRA, mSelectedFile.getPath());
					setResult(RESULT_OK, getIntent());
					finish();
				}
			}
		});

		final Button newButton = (Button) findViewById(R.id.fdButtonNew);
		newButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				setCreateVisible(v);

				mFileName.setText("");
				mFileName.requestFocus();
			}
		});

		mSelectionMode = getIntent().getIntExtra(SELECTION_MODE_EXTRA, MODE_CREATE);
		if (MODE_OPEN == mSelectionMode) {
			newButton.setEnabled(false);
		}
		
		mSelectionType = getIntent().getIntExtra(SELECTION_TYPE_EXTRA, TYPE_FILE);

		mLayoutSelect = (LinearLayout) findViewById(R.id.fdLinearLayoutSelect);
		mLayoutCreate = (LinearLayout) findViewById(R.id.fdLinearLayoutCreate);
		mLayoutCreate.setVisibility(View.GONE);

		final Button cancelButton = (Button) findViewById(R.id.fdButtonCancel);
		cancelButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				setSelectVisible(v);
			}
		});
		
		final Button createButton = (Button) findViewById(R.id.fdButtonCreate);
		createButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mFileName.getText().length() > 0) {
					getIntent().putExtra(RESULT_PATH_EXTRA,
							mCurrentPath + "/" + mFileName.getText());
					setResult(RESULT_OK, getIntent());
					finish();
				}
			}
		});

		String startPath = getIntent().getStringExtra(START_PATH_EXTRA);
		if (startPath != null) {
			getDir(startPath);
		} else {
			getDir(ROOT);
		}
	}

	private void getDir(String dirPath) {
		boolean useAutoSelection = dirPath.length() < mCurrentPath.length();
		Integer position = mLastPositions.get(mParentPath);
		getDirImpl(dirPath);
		if (position != null && useAutoSelection) {
			getListView().setSelection(position);
		}
	}

	private void getDirImpl(final String dirPath) {
		mCurrentPath = dirPath;

		final List<String> item = new ArrayList<String>();
		mPath = new ArrayList<String>();
		mList = new ArrayList<HashMap<String, Object>>();

		File f = new File(mCurrentPath);
		File[] files = f.listFiles();
		if (files == null) {
			mCurrentPath = ROOT;
			f = new File(mCurrentPath);
			files = f.listFiles();
		}
		myPath.setText(getText(R.string.location) + ": " + mCurrentPath);

		if (!mCurrentPath.equals(ROOT)) {

			item.add(ROOT);
			addItem(ROOT, R.drawable.folder);
			mPath.add(ROOT);

			item.add("../");
			addItem("../", R.drawable.folder);
			mPath.add(f.getParent());
			mParentPath = f.getParent();

		}

		TreeMap<String, String> dirsMap = new TreeMap<String, String>();
		TreeMap<String, String> dirsPathMap = new TreeMap<String, String>();
		TreeMap<String, String> filesMap = new TreeMap<String, String>();
		TreeMap<String, String> filesPathMap = new TreeMap<String, String>();
		for (File file : files) {
			if (file.isDirectory()) {
				String dirName = file.getName();
				dirsMap.put(dirName, dirName);
				dirsPathMap.put(dirName, file.getPath());
			} else {
				filesMap.put(file.getName(), file.getName());
				filesPathMap.put(file.getName(), file.getPath());
			}
		}
		item.addAll(dirsMap.tailMap("").values());
		item.addAll(filesMap.tailMap("").values());
		mPath.addAll(dirsPathMap.tailMap("").values());
		mPath.addAll(filesPathMap.tailMap("").values());

		SimpleAdapter fileList = new SimpleAdapter(this, mList,
				R.layout.file_dialog_row,
				new String[] { ITEM_KEY, ITEM_IMAGE }, new int[] {
						R.id.fdrowtext, R.id.fdrowimage });

		for (String dir : dirsMap.tailMap("").values()) {
			addItem(dir, R.drawable.folder);
		}

		for (String file : filesMap.tailMap("").values()) {
			addItem(file, R.drawable.file);
		}

		fileList.notifyDataSetChanged();

		setListAdapter(fileList);

	}

	private void addItem(String fileName, int imageId) {
		HashMap<String, Object> item = new HashMap<String, Object>();
		item.put(ITEM_KEY, fileName);
		item.put(ITEM_IMAGE, imageId);
		mList.add(item);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		File file = new File(mPath.get(position));
		setSelectVisible(v);

		if (file.isDirectory()) {
			if(TYPE_DIRECTORY == mSelectionType && file.canRead()) {
				mSelectedFile = file;
				v.setSelected(true);
				mSelectButton.setEnabled(true);
			}else{
				mSelectButton.setEnabled(false);
			}
			
			if (file.canRead()) {
				mLastPositions.put(mCurrentPath, position);
				getDir(mPath.get(position));
			} else {
				notifyUnreadable(file);
			}
		} else if(TYPE_FILE == mSelectionType) {
			mSelectedFile = file;
			v.setSelected(true);
			mSelectButton.setEnabled(true);
		}
	}

	private void notifyUnreadable(File file) {
		AlertDialog.Builder dialog = new AlertDialog.Builder(this);
		dialog.setIcon(R.drawable.icon);
		dialog.setTitle("[" + file.getName() + "] "+ getText(R.string.cant_read_folder));
		dialog.setPositiveButton("OK",
				new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog,	int which) {}
		});
		dialog.show();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if ((keyCode == KeyEvent.KEYCODE_BACK)) {
			mSelectButton.setEnabled(false);

			if (mLayoutCreate.getVisibility() == View.VISIBLE) {
				mLayoutCreate.setVisibility(View.GONE);
				mLayoutSelect.setVisibility(View.VISIBLE);
			} else {
				if (!mCurrentPath.equals(ROOT)) {
					getDir(mParentPath);
				} else {
					return super.onKeyDown(keyCode, event);
				}
			}

			return true;
		} else {
			return super.onKeyDown(keyCode, event);
		}
	}

	private void setCreateVisible(View v) {
		mLayoutCreate.setVisibility(View.VISIBLE);
		mLayoutSelect.setVisibility(View.GONE);

		mInputManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
		mSelectButton.setEnabled(false);
	}

	private void setSelectVisible(View v) {
		mLayoutCreate.setVisibility(View.GONE);
		mLayoutSelect.setVisibility(View.VISIBLE);

		mInputManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
		mSelectButton.setEnabled(false);
	}
}