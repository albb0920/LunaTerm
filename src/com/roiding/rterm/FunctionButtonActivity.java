package com.roiding.rterm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.AdapterView;
import android.widget.SimpleAdapter;
import android.widget.AdapterView.OnItemClickListener;

import com.roiding.rterm.bean.FunctionButton;
import com.roiding.rterm.util.DBUtils;

public class FunctionButtonActivity extends ListActivity {
	private static final String TAG = "rterm.funcbtn";
	private static List<FunctionButton> btns;
	private DBUtils dbUtils;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_functionbtns);
	}

	@Override
	public void onResume() {
		super.onResume();

		if (dbUtils == null)
			dbUtils = new DBUtils(this);

		this.getListView().setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {

				System.out.println("onItemClick...");
				FunctionButton btn = btns.get(position);
				Intent intent = new Intent();
				intent.setClass(FunctionButtonActivity.this,
						EditFunctionButtonActivity.class);
				intent.putExtra("button", btn);
				FunctionButtonActivity.this.startActivityForResult(intent, 0);

			}

		});
		this.registerForContextMenu(this.getListView());

		update();

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.i(TAG, "onActivityResult");
		update();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		MenuItem add = menu.add(R.string.editfunctionbtn_add).setIcon(
				android.R.drawable.ic_menu_add);

		add.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				Intent intent = new Intent();
				intent.setClass(FunctionButtonActivity.this,
						EditFunctionButtonActivity.class);
				FunctionButtonActivity.this.startActivityForResult(intent, 0);
				return true;
			}
		});

		return true;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenu.ContextMenuInfo menuInfo) {

		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		final FunctionButton btn = btns.get(info.position);

		menu.setHeaderTitle(btn.getName());

		MenuItem edit = menu.add(R.string.editfunctionbtn_edit);
		edit.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				Intent intent = new Intent();
				intent.setClass(FunctionButtonActivity.this,
						EditFunctionButtonActivity.class);
				intent.putExtra("button", btn);
				FunctionButtonActivity.this.startActivityForResult(intent, 0);
				return true;
			}
		});

		MenuItem delete = menu.add(R.string.editfunctionbtn_delete);
		delete.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				dbUtils.functionsButtonsDelegate.delete(btn);
				FunctionButtonActivity.this.update();
				return true;
			}
		});

	}

	protected void update() {
		if (dbUtils == null) {
			dbUtils = new DBUtils(this);
		}

		btns = dbUtils.functionsButtonsDelegate.get();

		SimpleAdapter adapter = new SimpleAdapter(this, getList(btns),
				R.layout.item_functionbtn, new String[] { "name", "key" },
				new int[] { android.R.id.text1, android.R.id.text2 });

		this.setListAdapter(adapter);
	}

	private List<Map<String, String>> getList(List<FunctionButton> list) {
		ArrayList<Map<String, String>> btnList = new ArrayList<Map<String, String>>();
		for (FunctionButton btn : list) {
			Map<String, String> map = new HashMap<String, String>();
			map.put("name", btn.getName());
			map.put("key", btn.getKeys());

			btnList.add(map);
		}
		return btnList;
	}

	@Override
	public void onStop() {
		super.onStop();

		if (dbUtils != null) {
			dbUtils.close();
			dbUtils = null;
		}
	}
}