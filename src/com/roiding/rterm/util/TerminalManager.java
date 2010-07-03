package com.roiding.rterm.util;

import java.io.IOException;
import java.util.HashMap;

import tw.loli.lunaTerm.TerminalView;

import android.os.Handler;
import android.os.Message;


public class TerminalManager {
	private static long anti_idle_delay = 1000 * 60 * 3;
	private static char anti_idle_char = 0x0C;

	private static final HashMap<Long, TerminalView> viewMap = new HashMap<Long, TerminalView>();

	private static TerminalManager instance;

	private TerminalManager() {
	}

	public static TerminalManager getInstance() {
		if (instance == null) {
			instance = new TerminalManager();
			instance.check();
		}
		return instance;
	}

	public void putView(TerminalView view) {
		viewMap.put(view.host.getId(), view);
	}

	public void removeView(long id) {
		viewMap.remove(id);
	}

	public TerminalView getView(long id) {
		return viewMap.get(id);
	}

	public TerminalView[] getViews() {
		return viewMap.values().toArray(new TerminalView[viewMap.size()]);
	}

	private static final RefreshHandler mHandler = new RefreshHandler();

	static class RefreshHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			TerminalManager.instance.check();
		}

		public void sleep(long delayMillis) {
			this.removeMessages(0);
			sendMessageDelayed(obtainMessage(0), delayMillis);
		}
	};

	private void check() {
		TerminalView[] views = getViews();

		for (final TerminalView view : views) {
			try {
				if (view.connected)
					view.connection.write(anti_idle_char);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		mHandler.sleep(anti_idle_delay);
	}

}
