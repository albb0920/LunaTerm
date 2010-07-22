package com.roiding.rterm;

import tw.loli.lunaTerm.FunctionButtonActivity;
import tw.loli.lunaTerm.R;
import tw.loli.lunaTerm.R.xml;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;

public class SettingsActivity extends PreferenceActivity {
	private static final String TAG = "RTermSettings";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try {
			addPreferencesFromResource(R.xml.preferences);
		} catch (ClassCastException e) {
			Log.e(TAG, "reset default values");
			PreferenceManager.setDefaultValues(this, R.xml.preferences, true);
			addPreferencesFromResource(R.xml.preferences);
		}

		for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
			Preference p = getPreferenceScreen().getPreference(i);

			if (p.getKey().equals("settings_function_button")) {
				PreferenceScreen ps = (PreferenceScreen) p;
				Intent intent = new Intent();
				intent.setClass(this, FunctionButtonActivity.class);
				ps.setIntent(intent);
			}
		}

	}
}
