package com.roiding.rterm;

import tw.loli.lunaTerm.FunctionButtonActivity;
import tw.loli.lunaTerm.R;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;
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

		PreferenceScreen ps = (PreferenceScreen) getPreferenceScreen().findPreference("settings_function_button");
		Intent intent = new Intent();
		intent.setClass(this, FunctionButtonActivity.class);
		ps.setIntent(intent);
		
		/* There is no inversed dependency in Android, so we do it ourself */
		if(!PreferenceManager.getDefaultSharedPreferences(this).getBoolean("settings_magnifier_fullscreen", true)){
			getPreferenceScreen().findPreference("settings_magnifier_focus_width").setEnabled(true);
			getPreferenceScreen().findPreference("settings_magnifier_focus_height").setEnabled(true);
		}
			
		getPreferenceScreen().findPreference("settings_magnifier_fullscreen").setOnPreferenceChangeListener(new OnPreferenceChangeListener(){
			public boolean onPreferenceChange(Preference preference,
					Object newValue) {
					boolean depend = ! (Boolean) newValue;
					getPreferenceScreen().findPreference("settings_magnifier_focus_width").setEnabled(depend);
					getPreferenceScreen().findPreference("settings_magnifier_focus_height").setEnabled(depend);
				return true;
			}
		});
	}
}
