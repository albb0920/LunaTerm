package com.roiding.rterm.bean;

import java.io.Serializable;

import android.content.ContentValues;

import com.roiding.rterm.util.DBUtils;

public class Host implements Serializable {

	private static final long serialVersionUID = 1L;

	private long id;
	private String name;
	private String protocal;
	
	public String getEncoding() {
		return encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	private String encoding;
	private String user;
	private String pass;
	private String host;
	private int port;
	private int autodelay;

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getProtocal() {
		return protocal;
	}

	public void setProtocal(String protocal) {
		this.protocal = protocal;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getPass() {
		return pass;
	}
	
	public void setPass(String pass) {
		this.pass = pass;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}
	
	public int getAutodelay() {
		return autodelay;
	}

	public void setAutodelay(int autodelay) {
		this.autodelay = autodelay;
	}

	public ContentValues getValues() {
		ContentValues values = new ContentValues();

		values.put(DBUtils.FIELD_HOSTS_NAME, name);
		values.put(DBUtils.FIELD_HOSTS_PROTOCAL, protocal);
		values.put(DBUtils.FIELD_HOSTS_ENCODING, encoding);
		values.put(DBUtils.FIELD_HOSTS_USER, user);
		values.put(DBUtils.FIELD_HOSTS_PASS, pass);
		values.put(DBUtils.FIELD_HOSTS_HOST, host);
		values.put(DBUtils.FIELD_HOSTS_PORT, port);
		values.put(DBUtils.FIELD_HOSTS_AUTODELAY, autodelay);

		return values;
	}

}
