package tw.loli.lunaTerm;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Gallery;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.widget.AdapterView.OnItemClickListener;

import com.roiding.rterm.GestureView;
import com.roiding.rterm.OnGestureListener;
import com.roiding.rterm.bean.FunctionButton;
import com.roiding.rterm.bean.Host;
import com.roiding.rterm.util.Constants;
import com.roiding.rterm.util.DBUtils;
import com.roiding.rterm.util.TerminalManager;

public class TerminalActivity extends Activity {

	private static final String TAG = "TerminalActivity";
	protected static final int DIALOG_INPUT_HELP = 0;
	private DBUtils dbUtils;
	private Gallery functionKeyGallery;
	private Map<String, Gesture> gestureMap = new HashMap<String, Gesture>();
	private List<FunctionButton> functionBtnList;
	protected PowerManager.WakeLock m_wake_lock;
	private SharedPreferences pref;
	
	
	class Gesture {
		public Gesture(String type, String desc) {
			this.type = type;
			this.desc = desc;
		}

		public String type;
		public String desc;
		public int[] keycode;
	}

	private static long currentViewId = -1;

	private RefreshHandler mHandler;

	class RefreshHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			close((Exception) msg.obj);
		}

		public void dispatch(Exception ex) {
			this.removeMessages(0);
			Message.obtain(this, -1, ex).sendToTarget();
		}
	};

	private String[] gestureKey;
	private String[] gestureDesc;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		pref = PreferenceManager
				.getDefaultSharedPreferences(this);

		if (!pref.getBoolean(Constants.SETTINGS_SHOW_STATUSBAR, false))
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
					WindowManager.LayoutParams.FLAG_FULLSCREEN);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.act_terminal);

		final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		this.m_wake_lock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ON_AFTER_RELEASE, "rTerm");
		this.m_wake_lock.acquire();
		
		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		gestureKey = getResources().getStringArray(R.array.gestures_key);
		gestureDesc = getResources().getStringArray(R.array.gestures_desc);

		for (int i = 0; i < gestureKey.length; i++) {
			Gesture g = new Gesture(gestureKey[i], gestureDesc[i]);
			gestureMap.put(g.type, g);
		}

		GestureView mGestureView = (GestureView) findViewById(R.id.gestureView);
		mGestureView.setTerminalActivity(this);
		mGestureView.setOnGestureListener(new OnGestureListener() {

			public void onGestureEvent(String gesture) {
				if (gesture == null || gesture.length() == 0)
					return;

				if (gesture.equals("U")) {
					pressKey(KeyEvent.KEYCODE_DPAD_UP);
				} else if (gesture.equals("D")) {
					pressKey(KeyEvent.KEYCODE_DPAD_DOWN);
				} else if (gesture.equals("L")) {
					pressKey(KeyEvent.KEYCODE_DPAD_LEFT);
				} else if (gesture.equals("R")) {
					pressKey(KeyEvent.KEYCODE_DPAD_RIGHT);
				} else if (gesture.equals("D,L")) {
					pressKey(KeyEvent.KEYCODE_ENTER);
				} else if (gesture.equals("D,R,U")) {
					pressKey(KeyEvent.KEYCODE_SPACE);
				} else if (gesture.equals("R,U")) {
					// page up
					pressKey(new byte[] { 27, 91, 53, 126 });
				} else if (gesture.equals("R,D")) {
					// page down
					pressKey(new byte[] { 27, 91, 54, 126 });
				} else if (gesture.equals("R,D,R") || gesture.equals("R,L,R")) {
					// input helper
					LayoutInflater factory = LayoutInflater
							.from(TerminalActivity.this);
					final View textEntryView = factory.inflate(
							R.layout.act_input_helper, null);
					new AlertDialog.Builder(TerminalActivity.this).setTitle(
							R.string.terminal_inputhelper).setView(
							textEntryView).setPositiveButton(R.string.ok,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									String text = ((EditText) textEntryView
											.findViewById(R.id.text)).getText()
											.toString();
									if (text != null && text.length() > 0)
										pressKey(text);

								}
							}).setNegativeButton(R.string.cancel,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
								}
							}).create().show();

				}
			}

			public String getGestureText(String gesture) {
				String desc = "Unknown Gesture";
				Gesture r = gestureMap.get(gesture);
				if (r != null)
					desc = r.desc;

				StringBuffer t = new StringBuffer();
				t.append("Gesture:").append(gesture).append(" (").append(desc)
						.append(")");

				return t.toString();
			}
		});

		if (dbUtils == null) {
			dbUtils = new DBUtils(this);
		}
		functionBtnList = dbUtils.functionsButtonsDelegate.get();

		functionKeyGallery = (Gallery) findViewById(R.id.functionKeyGallery);

		if (functionBtnList.size() > 0) {
			functionKeyGallery.setAdapter(new FunctionButtonAdapter(this));
		}
		functionKeyGallery.setBackgroundColor(Color.alpha(0));
		functionKeyGallery.setSelection(functionBtnList.size() / 2);
		functionKeyGallery.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				String k = functionBtnList.get(position).getKeys();
				String v = functionBtnList.get(position).getName();

				Toast.makeText(TerminalActivity.this, v, Toast.LENGTH_SHORT)
						.show();

				boolean controlPressed = false;
				for (char c : k.toCharArray()) {
					if (c == '^') {
						controlPressed = true;
						pressMetaKey(KeyEvent.KEYCODE_DPAD_CENTER);
					} else {
						if (controlPressed) {
							c = String.valueOf(c).toLowerCase().charAt(0);
							KeyEvent[] events = TerminalView.DEFAULT_KEYMAP
									.getEvents(new char[] { c });

							pressKey(events[0].getKeyCode());
						} else {
							pressKey(c);
						}
						controlPressed = false;
					}
				}
			}
		});

		mHandler = new RefreshHandler();

	}

	public class FunctionButtonAdapter extends BaseAdapter {
		public FunctionButtonAdapter(Context c) {
			mContext = c;
		}

		public int getCount() {
			return functionBtnList.size();
		}

		public Object getItem(int position) {
			return position;
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			System.out.println("functionBtnList=" + functionBtnList.size());
			Button btn = new Button(mContext);
			btn.setText(functionBtnList.get(position).getName());
			btn.setClickable(false);
			return btn;
		}

		private Context mContext;
	}

	@Override
	public void onStart() {
		super.onStart();

		Host host = (Host) getIntent().getExtras().getSerializable("host");

		currentViewId = host.getId();

		TerminalView view = TerminalManager.getInstance()
				.getView(currentViewId);
		
		if (view == null) {
			view = new TerminalView(this, null);
			view.terminalActivity = this;
			view.startConnection(host);
			TerminalManager.getInstance().putView(view);
			// checkService();
		}

		view.terminalActivity = this;

		showView(currentViewId);

	}

	public void refreshView() {
		showView(currentViewId);
	}

	public void showView(long id) {
		TerminalView view = TerminalManager.getInstance().getView(id);
		if (view != null) {
			view.terminalActivity = this;
			
			FrameLayout terminalFrame = (FrameLayout) findViewById(R.id.terminalFrame);					
			View osd = findViewById(R.id.terminalOSD);
			
			terminalFrame.removeAllViews();			
			terminalFrame.addView(view,LayoutParams.FILL_PARENT,LayoutParams.FILL_PARENT);
			terminalFrame.addView(osd);
			
			currentViewId = id;
			
		}
	}

	public TerminalView getCurrentTerminalView() {
		return TerminalManager.getInstance().getView(currentViewId);
	}
	
	public boolean showUrlDialog(String url) {

		AlertDialog.Builder alert = new AlertDialog.Builder(
				TerminalActivity.this);

		alert.setTitle(R.string.title_openurl);

		// Set an EditText view to get user input
		final EditText input = new EditText(TerminalActivity.this);
		input.setText(url);
		alert.setView(input);

		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				String value = input.getText().toString();

//				TerminalView view = TerminalManager.getInstance().getView(
//						currentViewId);
//
//				if (view != null) {
//					try {
//						view.connection.send(value.getBytes(view.host
//								.getEncoding()));
//
//					} catch (Exception e) {
//					}
//
//				}
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(value)));
			}
		});

		alert.setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						// Canceled.
					}
				});

		alert.show();
		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		MenuItem help = menu.add(R.string.addressbook_help).setIcon(
				android.R.drawable.ic_menu_help);

		help.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				Intent intent = new Intent();
				intent.setClass(TerminalActivity.this, HelpActivity.class);
				TerminalActivity.this.startActivityForResult(intent, 0);				
				return true;
			}
		});
		
		MenuItem disconnect = menu.add(R.string.terminal_disconnect).setIcon(
				android.R.drawable.ic_menu_close_clear_cancel);
		disconnect.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				close(null);
				return true;
			}
		});

		TerminalView[] views = TerminalManager.getInstance().getViews();
		for (final TerminalView view : views) {
			MenuItem item = menu.add(view.host.getName()).setIcon(
					R.drawable.online);
			if (views.length > 1)
				item.setOnMenuItemClickListener(new OnMenuItemClickListener() {
					public boolean onMenuItemClick(MenuItem item) {
						showView(view.host.getId());
						return true;
					}
				});
		}

		return true;
	}

	public void disconnect(Exception e) {
		mHandler.dispatch(e);
	}

	public void pressKey(String s) {
		Log.i(TAG, "pressKey(byte)");
		TerminalView currentView = TerminalManager.getInstance().getView(
				currentViewId);

		try {
			currentView.write(s.getBytes(currentView.host.getEncoding()));
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void pressKey(char c) {
		pressKey(String.valueOf(c));
	}

	public void pressKey(byte[] b) {
		Log.i(TAG, "pressKey(byte)");
		TerminalView currentView = TerminalManager.getInstance().getView(
				currentViewId);

		try {
			currentView.write(b);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void pressMetaKey(int keyCode) {
		Log.i(TAG, "pressMetaKey=" + keyCode);
		TerminalView currentView = TerminalManager.getInstance().getView(
				currentViewId);

		try {
			currentView.processSpecialChar(keyCode, 65);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void pressKey(int keyCode) {
		Log.i(TAG, "pressKey=" + keyCode);
		TerminalView currentView = TerminalManager.getInstance().getView(
				currentViewId);
		
		try {
			currentView.processSpecialChar(keyCode, 0);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void close(Exception e) {

		TerminalView currentView = TerminalManager.getInstance().getView(
				currentViewId);
		if (currentView == null)
			return;

		try {
			currentView.connection.disconnect();
		} catch (Exception _e) {
		}

		if (e != null) {
			String msg = e.getLocalizedMessage();

			Host currentHost = currentView.host;

			if (UnknownHostException.class.isInstance(e)) {
				msg = String
						.format(getText(R.string.terminal_error_unknownhost)
								.toString(), currentHost.getName());
			} else if (ConnectException.class.isInstance(e)) {
				msg = String.format(getText(R.string.terminal_error_connect)
						.toString(), currentHost.getName());
			}

			Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
		}

		TerminalManager.getInstance().removeView(currentViewId);
		// checkService();
		currentViewId = -1;
		finish();
	}

	public void changeFunctionKeyGalleryDisplay() {
		if (functionBtnList.size() == 0) {
			Toast
					.makeText(this, R.string.functionbtn_empty,
							Toast.LENGTH_SHORT).show();
		}

		if (functionKeyGallery.getVisibility() == View.VISIBLE)
			functionKeyGallery.setVisibility(View.INVISIBLE);
		else
			functionKeyGallery.setVisibility(View.VISIBLE);
	}

	@Override
	public void onStop() {
		super.onStop();

		if (dbUtils != null) {
			dbUtils.close();
			dbUtils = null;
		}

		if (currentViewId == -1) {
			Toast.makeText(this, R.string.terminal_connectclose,
					Toast.LENGTH_SHORT).show();
		} else {
			// disable this toast
			// Toast.makeText(this,
			// R.string.terminal_connectsave,Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.d(TAG, "onResume called");
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

	}

	@Override
	protected void onDestroy() {
		this.m_wake_lock.release();
		super.onDestroy();
	}
	
}
