package com.roiding.rterm;

import java.util.HashMap;
import java.util.Map;

import tw.loli.lunaTerm.R;
import tw.loli.lunaTerm.R.string;
import tw.loli.lunaTerm.R.xml;

import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.Preference.OnPreferenceChangeListener;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

import com.roiding.rterm.bean.FunctionButton;
import com.roiding.rterm.util.DBUtils;

public class EditFunctionButtonActivity extends PreferenceActivity {

	private FunctionButton btn;
	private Map<String, String> valuesMap = new HashMap<String, String>();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.addPreferencesFromResource(R.xml.functionbtn);

		valuesMap.put("name", "");
		valuesMap.put("keys", "");
		valuesMap.put("sortnumber", "0");

		btn = (FunctionButton) getIntent().getSerializableExtra("button");

		if (btn != null)
			extractValues(btn);

		updatePreferenceDisplay();

		System.out.println(getPreferenceScreen().getPreferenceCount());

		OnPreferenceChangeListener listener = new OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference,
					Object newValue) {
				String key = preference.getKey();
				String value = newValue.toString();
				Log.i("TT", "onPreferenceChange," + key + ":" + value);
				valuesMap.put(key, value);
				updatePreferenceDisplay();
				return false;
			};
		};

		for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
			Preference p = getPreferenceScreen().getPreference(i);

			if (p instanceof PreferenceCategory) {
				PreferenceCategory p2 = (PreferenceCategory) p;
				for (int j = 0; j < p2.getPreferenceCount(); j++) {
					p2.getPreference(j).setOnPreferenceChangeListener(listener);
				}
			} else {
				getPreferenceScreen().getPreference(i)
						.setOnPreferenceChangeListener(listener);
			}
		}
	}

	private void extractValues(FunctionButton btn) {
		Map<String, String> m = valuesMap;
		m.put("name", btn.getName());
		m.put("keys", btn.getKeys());
		m.put("sortnumber", String.valueOf(btn.getSortNumber()));
	}

	private void updatePreferenceDisplay() {
		// if h is null, then create a new Host, otherwise, update a exist Host
		for (String key : valuesMap.keySet()) {
			Preference pref = this.findPreference(key);
			if (pref != null) {
				String value = valuesMap.get(key);

				if (pref instanceof EditTextPreference) {
					EditTextPreference textPref = (EditTextPreference) pref;
					if (value != null && value.length() > 0)
						textPref.setTitle(value);
					textPref.setText(value);
				} else if (pref instanceof ListPreference) {
					ListPreference listPref = (ListPreference) pref;
					if (value != null && value.length() > 0)
						listPref.setTitle(value);
					listPref.setValue(value);
				}
			}

		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		MenuItem save = menu.add(R.string.editfunctionbtn_done).setIcon(
				android.R.drawable.ic_menu_save);

		save.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				autoSave = true;
				finish();
				return true;
			}
		});
		MenuItem cancel = menu.add(R.string.editfunctionbtn_revert).setIcon(
				android.R.drawable.ic_menu_close_clear_cancel);

		cancel.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				autoSave = false;
				finish();
				return true;
			}
		});

		MenuItem delete = menu.add(R.string.editfunctionbtn_delete).setIcon(
				android.R.drawable.ic_menu_delete);

		delete.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				autoSave = false;
				delete();
				finish();
				return true;
			}
		});

		return true;
	}

	private boolean autoSave = true;

	@Override
	public void onResume() {
		super.onResume();
		autoSave = true;
	}

	@Override
	public void onPause() {
		super.onPause();
		if (autoSave)
			save();
	}

	private void delete() {

		if (btn != null) {
			DBUtils dbUtils = new DBUtils(this);
			dbUtils.functionsButtonsDelegate.delete(btn);
			dbUtils.close();
		}
	}

	private void save() {
		DBUtils dbUtils = new DBUtils(this);

		String name = valuesMap.get("name");
		String keys = valuesMap.get("keys");
		String sortnumber = valuesMap.get("sortnumber");

		if (name == null || name.length() <= 0)
			return;
		if (keys == null || keys.length() <= 0)
			return;

		if (btn != null) {
			btn.setName(name);
			btn.setKeys(keys);

			try {
				btn.setSortNumber(Integer.parseInt(sortnumber));
			} catch (Exception e) {
				btn.setSortNumber(0);
			}

			dbUtils.functionsButtonsDelegate.update(btn);

		} else {
			btn = new FunctionButton();
			btn.setName(name);
			btn.setKeys(keys);

			try {
				btn.setSortNumber(Integer.parseInt(sortnumber));
			} catch (Exception e) {
				btn.setSortNumber(0);
			}

			dbUtils.functionsButtonsDelegate.insert(btn);
		}
		dbUtils.close();
	}
}