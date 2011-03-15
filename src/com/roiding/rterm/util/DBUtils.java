package com.roiding.rterm.util;

import java.util.LinkedList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.roiding.rterm.bean.FunctionButton;
import com.roiding.rterm.bean.Host;

public class DBUtils extends SQLiteOpenHelper {

	public DBUtils(Context context) {
		super(context, DB_NAME, null, DB_VERSION);
	}

	public final static String DB_NAME = "rterm";
	public final static int DB_VERSION = 4;

	public final static String TABLE_HOSTS = "hosts";
	public final static String FIELD_HOSTS_ID = "_id";
	public final static String FIELD_HOSTS_NAME = "name";
	public final static String FIELD_HOSTS_PROTOCAL = "protocal";
	public final static String FIELD_HOSTS_ENCODING = "encoding";
	public final static String FIELD_HOSTS_USER = "user";
	public final static String FIELD_HOSTS_PASS = "pass";
	public final static String FIELD_HOSTS_HOST = "host";
	public final static String FIELD_HOSTS_PORT = "port";
	public final static String FIELD_HOSTS_AUTODELAY = "autodelay";

	public final static String TABLE_FUNCBTNS = "functionbtns";
	public final static String FIELD_FUNCBTNS_ID = "_id";
	public final static String FIELD_FUNCBTNS_NAME = "name";
	public final static String FIELD_FUNCBTNS_KEYS = "keys";
	public final static String FIELD_FUNCBTNS_SORTNUMBER = "sortnumber";

	@Override
	public void onCreate(SQLiteDatabase db) {

		db.execSQL("CREATE TABLE " + TABLE_HOSTS
				+ " (_id INTEGER PRIMARY KEY, " + FIELD_HOSTS_NAME + " TEXT, "
				+ FIELD_HOSTS_PROTOCAL + " TEXT, " + FIELD_HOSTS_ENCODING
				+ " TEXT DEFAULT 'GBK'," + FIELD_HOSTS_USER + " TEXT, "
				+ FIELD_HOSTS_PASS + " TEXT, " + FIELD_HOSTS_HOST + " TEXT, "
				+ FIELD_HOSTS_PORT + " INTEGER, " + FIELD_HOSTS_AUTODELAY + " INTEGER )");

		db.execSQL("CREATE TABLE " + TABLE_FUNCBTNS
				+ " (_id INTEGER PRIMARY KEY, " + FIELD_FUNCBTNS_NAME
				+ " TEXT, " + FIELD_FUNCBTNS_KEYS + " TEXT, "
				+ FIELD_FUNCBTNS_SORTNUMBER + " INTEGER DEFAULT 0)");

	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		switch (oldVersion) {
		case 2:
			db.execSQL("ALTER TABLE " + TABLE_HOSTS + " ADD COLUMN "
					+ FIELD_HOSTS_ENCODING + " TEXT DEFAULT 'GBK'");
		case 3:
			db.execSQL("CREATE TABLE " + TABLE_FUNCBTNS
					+ " (_id INTEGER PRIMARY KEY, " + FIELD_FUNCBTNS_NAME
					+ " TEXT, " + FIELD_FUNCBTNS_KEYS + " TEXT, "
					+ FIELD_FUNCBTNS_SORTNUMBER + " INTEGER DEFAULT 0)");

		}
	}

	public HostsDelegate hostDelegate = new HostsDelegate();
	public FunctionButtonsDelegate functionsButtonsDelegate = new FunctionButtonsDelegate();

	public class HostsDelegate {
		public void delete(Host host) {
			if (host.getId() < 0)
				return;

			SQLiteDatabase db = getWritableDatabase();
			db.delete(TABLE_HOSTS, "_id = ?", new String[] { String
					.valueOf(host.getId()) });
			db.close();

		}

		public void update(Host host) {
			SQLiteDatabase db = getReadableDatabase();

			ContentValues values = host.getValues();

			db.update(TABLE_HOSTS, values, "_id =?", new String[] { String
					.valueOf(host.getId()) });

			db.close();
		}

		public Host insert(Host host) {
			SQLiteDatabase db = getWritableDatabase();

			long id = db.insert(TABLE_HOSTS, null, host.getValues());
			db.close();

			host.setId(id);
			return host;
		}

		public List<Host> get() {
			List<Host> hosts = new LinkedList<Host>();

			SQLiteDatabase db = getReadableDatabase();
			Cursor c = db.query(TABLE_HOSTS, null, null, null, null, null,
					FIELD_HOSTS_NAME + " ASC");

			while (c.moveToNext()) {
				Host host = new Host();

				host.setId(c.getLong(c.getColumnIndexOrThrow(FIELD_HOSTS_ID)));
				host.setName(c.getString(c
						.getColumnIndexOrThrow(FIELD_HOSTS_NAME)));
				host.setProtocal(c.getString(c
						.getColumnIndexOrThrow(FIELD_HOSTS_PROTOCAL)));
				host.setEncoding(c.getString(c
						.getColumnIndexOrThrow(FIELD_HOSTS_ENCODING)));
				host.setUser(c.getString(c
						.getColumnIndexOrThrow(FIELD_HOSTS_USER)));
				host.setPass(c.getString(c
						.getColumnIndexOrThrow(FIELD_HOSTS_PASS)));
				host.setHost(c.getString(c
						.getColumnIndexOrThrow(FIELD_HOSTS_HOST)));
				host.setPort(c
						.getInt(c.getColumnIndexOrThrow(FIELD_HOSTS_PORT)));
				host.setAutodelay(c
						.getInt(c.getColumnIndexOrThrow(FIELD_HOSTS_AUTODELAY)));

				hosts.add(host);
			}

			c.close();
			db.close();

			return hosts;
		}
	}

	public class FunctionButtonsDelegate {
		public void delete(FunctionButton btn) {
			if (btn.getId() < 0)
				return;

			SQLiteDatabase db = getWritableDatabase();
			db.delete(TABLE_FUNCBTNS, "_id = ?", new String[] { String
					.valueOf(btn.getId()) });
			db.close();

		}

		public void update(FunctionButton btn) {
			SQLiteDatabase db = getReadableDatabase();

			ContentValues values = btn.getValues();

			db.update(TABLE_FUNCBTNS, values, "_id =?", new String[] { String
					.valueOf(btn.getId()) });

			db.close();
		}

		public FunctionButton insert(FunctionButton btn) {
			SQLiteDatabase db = getWritableDatabase();

			long id = db.insert(TABLE_FUNCBTNS, null, btn.getValues());
			db.close();

			btn.setId(id);
			return btn;
		}

		public List<FunctionButton> get() {
			List<FunctionButton> btns = new LinkedList<FunctionButton>();

			SQLiteDatabase db = getReadableDatabase();
			Cursor c = db.query(TABLE_FUNCBTNS, null, null, null, null, null,
					FIELD_FUNCBTNS_SORTNUMBER + " ASC");

			while (c.moveToNext()) {
				FunctionButton btn = new FunctionButton();

				btn
						.setId(c.getLong(c
								.getColumnIndexOrThrow(FIELD_FUNCBTNS_ID)));
				btn.setName(c.getString(c
						.getColumnIndexOrThrow(FIELD_FUNCBTNS_NAME)));
				btn.setKeys(c.getString(c
						.getColumnIndexOrThrow(FIELD_FUNCBTNS_KEYS)));
				btn.setSortNumber(c.getInt(c
						.getColumnIndexOrThrow(FIELD_FUNCBTNS_SORTNUMBER)));

				btns.add(btn);
			}

			c.close();
			db.close();

			return btns;
		}
	}

}
