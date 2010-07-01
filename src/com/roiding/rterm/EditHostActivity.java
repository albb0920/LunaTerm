package com.roiding.rterm;

import java.util.HashMap;
import java.util.Map;

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

import com.roiding.rterm.bean.Host;
import com.roiding.rterm.util.DBUtils;

public class EditHostActivity extends PreferenceActivity {

	private Host h;
	private Map<String, String> valuesMap = new HashMap<String, String>();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.addPreferencesFromResource(R.xml.host);

		valuesMap.put("name", "");
		valuesMap.put("host", "");
		valuesMap.put("port", "23");
		valuesMap.put("protocal", "Telnet");
		valuesMap.put("encoding", "GBK");
		valuesMap.put("user", "");
		valuesMap.put("pass", "");

		h = (Host) getIntent().getSerializableExtra("host");

		if (h != null)
			extractValuesFromHost(h);

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

	private void extractValuesFromHost(Host host) {
		Map<String, String> m = valuesMap;
		m.put("name", host.getName());
		m.put("host", host.getHost());
		m.put("port", String.valueOf(host.getPort()));
		m.put("protocal", host.getProtocal());
		m.put("encoding", host.getEncoding());
		m.put("user", host.getUser());
		m.put("pass", host.getPass());
	}

	private void updatePreferenceDisplay() {
		// if h is null, then create a new Host, otherwise, update a exist Host
		for (String key : valuesMap.keySet()) {
			Preference pref = this.findPreference(key);
			if (pref != null) {
				String value = valuesMap.get(key);

				if (pref instanceof EditTextPreference) {
					EditTextPreference textPref = (EditTextPreference) pref;
					if (!key.equalsIgnoreCase("pass") && value != null
							&& value.length() > 0)
						textPref.setTitle(value);
					textPref.setText(value);
				} else if (pref instanceof ListPreference) {
					ListPreference listPref = (ListPreference) pref;
					if (!key.equalsIgnoreCase("pass") && value != null
							&& value.length() > 0)
						listPref.setTitle(value);
					listPref.setValue(value);
				}
			}

		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		MenuItem save = menu.add(R.string.edithost_done).setIcon(
				android.R.drawable.ic_menu_save);

		save.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				autoSave = true;
				finish();
				return true;
			}
		});
		MenuItem cancel = menu.add(R.string.edithost_revert).setIcon(
				android.R.drawable.ic_menu_close_clear_cancel);

		cancel.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				autoSave = false;
				finish();
				return true;
			}
		});

		MenuItem delete = menu.add(R.string.addressbook_delete_host).setIcon(
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

		if (h != null) {
			DBUtils dbUtils = new DBUtils(this);
			dbUtils.hostDelegate.delete(h);
			dbUtils.close();
		}
	}

	private void save() {
		DBUtils dbUtils = new DBUtils(this);

		String hostName = valuesMap.get("name");
		String hostHost = valuesMap.get("host");
		String hostProtocal = valuesMap.get("protocal");
		String hostPort = valuesMap.get("port");
		String hostEncoding = valuesMap.get("encoding");
		String hostUser = valuesMap.get("user");
		String hostPass = valuesMap.get("pass");

		if (h != null) {
			h.setName(hostName);
			h.setHost(hostHost);
			h.setProtocal(hostProtocal);
			h.setEncoding(hostEncoding);
			h.setUser(hostUser);
			h.setPass(hostPass);

			try {
				h.setPort(Integer.parseInt(hostPort));
			} catch (Exception e) {
				if (hostProtocal.equalsIgnoreCase("telnet"))
					h.setPort(23);
				else if (hostProtocal.equalsIgnoreCase("ssh"))
					h.setPort(22);
			}

			dbUtils.hostDelegate.update(h);

		} else {
			h = new Host();
			h.setName(hostName);
			h.setHost(hostHost);
			h.setProtocal(hostProtocal);
			h.setEncoding(hostEncoding);
			h.setUser(hostUser);
			h.setPass(hostPass);

			try {
				h.setPort(Integer.parseInt(hostPort));
			} catch (Exception e) {
				if (hostProtocal.equalsIgnoreCase("telnet"))
					h.setPort(23);
				else if (hostProtocal.equalsIgnoreCase("ssh"))
					h.setPort(22);
			}

			dbUtils.hostDelegate.insert(h);
		}
		dbUtils.close();
	}
}