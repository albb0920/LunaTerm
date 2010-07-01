package com.roiding.rterm;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.Bitmap.Config;
import android.graphics.Region.Op;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import com.roiding.rterm.bean.Host;
import com.roiding.rterm.util.ChineseUtils;

import de.mud.jta.Wrapper;
import de.mud.ssh.SshWrapper;
import de.mud.telnet.TelnetWrapper;
import de.mud.terminal.VDUBuffer;
import de.mud.terminal.VDUDisplay;
import de.mud.terminal.vt320;

public class TerminalView extends View implements VDUDisplay {
	final String TAG = "rterm.view";

	private static final int TERM_WIDTH = 80;
	private static final int TERM_HEIGHT = 24;

	private ArrayList<Url>[] urls;

	public int SCREEN_WIDTH_DEFAULT;
	public int SCREEN_HEIGHT_DEFAULT;
	public float CHAR_WIDTH;
	public float CHAR_HEIGHT;
	public int SCREEN_WIDTH;
	public int SCREEN_HEIGHT;
	
	private float fontSize = 11.5f;

	private static final int SCROLLBACK = 50;
	private static final int DEFAULT_FG_COLOR = 7;
	private static final int DEFAULT_BG_COLOR = 0;
	
	public static final KeyCharacterMap DEFAULT_KEYMAP = KeyCharacterMap
			.load(KeyCharacterMap.BUILT_IN_KEYBOARD);

	public Bitmap bitmap;
	public VDUBuffer buffer;
	public int color[];
	public boolean connected;
	private boolean fullRedraw = true;

	private final Paint defaultPaint = new Paint();
	private final Paint cursorPaint = new Paint();
	private final Canvas canvas = new Canvas();

	private boolean ctrlPressed;
	private boolean altPressed;
	private boolean shiftPressed;

	public Wrapper connection;
	public TerminalActivity terminalActivity;
	public Host host;

	private Boolean highres;

	public TerminalView(Context context, AttributeSet attrs,
			TerminalActivity terminalActivity, Boolean highres) {
		super(context, attrs);

		this.terminalActivity = terminalActivity;
		this.highres = highres;
		setFocusable(true);
		setFocusableInTouchMode(true);
		init();
	}

	public void init() {
		resetColors();

		buffer = new vt320() {
			public void beep() {
				Log.i(TAG, "beep");
			}

			public void write(byte[] b) {
				try {
					connection.write(b);
				} catch (Exception e) {
					terminalActivity.disconnect(e);
				}
			}
		};

		buffer.setBufferSize(SCROLLBACK);
		buffer.setDisplay(this);
		buffer.setScreenSize(TERM_WIDTH, TERM_HEIGHT, true);

		defaultPaint.setAntiAlias(true);
		defaultPaint.setTypeface(Typeface.MONOSPACE);
		setFontSize(fontSize);

		// Workaround to create array of ArrayList generic type.
		urls = (ArrayList<Url>[]) Array.newInstance(ArrayList.class, TERM_HEIGHT);
	}

	private void setFontSize(float size) {
		if (size < 11f)
			size = 11f;

		fontSize = size;
		defaultPaint.setTextSize(size);
		// TODO: change fix value to adaptive more screen resolutions
		if(this.highres){
			SCREEN_WIDTH_DEFAULT = 854;
			SCREEN_HEIGHT_DEFAULT = 480;
			CHAR_WIDTH = 7f;
		} else {
			SCREEN_WIDTH_DEFAULT = 480;
			SCREEN_HEIGHT_DEFAULT = 320;
			CHAR_WIDTH = 6f;
		}
		
		CHAR_HEIGHT = 13.3f;
		

		SCREEN_WIDTH = SCREEN_WIDTH_DEFAULT;
		SCREEN_HEIGHT = SCREEN_HEIGHT_DEFAULT;

//		CHAR_WIDTH = SCREEN_WIDTH / TERM_WIDTH;
//		CHAR_HEIGHT = SCREEN_HEIGHT / TERM_HEIGHT;
		// SCREEN_WIDTH = SCREEN_WIDTH_DEFAULT;
		// SCREEN_HEIGHT = SCREEN_HEIGHT_DEFAULT;

		// if (size <= 11f) {
		// CHAR_WIDTH = 6;
		// CHAR_HEIGHT = 13;
		// SCREEN_WIDTH = SCREEN_WIDTH_DEFAULT;
		// SCREEN_HEIGHT = SCREEN_HEIGHT_DEFAULT;
		// } else if (size <= 12.5f) {
		// CHAR_WIDTH = 6.1f;
		// CHAR_HEIGHT = 14;
		// SCREEN_WIDTH = (int) (CHAR_WIDTH * TERM_WIDTH);
		// SCREEN_HEIGHT = (int) (CHAR_HEIGHT * TERM_HEIGHT);
		//
		// } else {
		//
		// // read new metrics to get exact pixel dimensions
		// FontMetricsInt fm = defaultPaint.getFontMetricsInt();
		// int charDescent = fm.descent;
		//
		// float[] widths = new float[1];
		// defaultPaint.getTextWidths("X", widths);
		//
		// CHAR_WIDTH = widths[0] - 1;
		// CHAR_HEIGHT = Math.abs(fm.top) + Math.abs(fm.descent) + 1;
		//
		// SCREEN_WIDTH = (int) (CHAR_WIDTH * TERM_WIDTH);
		// SCREEN_HEIGHT = (int) (CHAR_HEIGHT * TERM_HEIGHT);
		//
		// SCREEN_WIDTH = Math.max(SCREEN_WIDTH, SCREEN_WIDTH_DEFAULT);
		// SCREEN_HEIGHT = Math.max(SCREEN_HEIGHT, SCREEN_HEIGHT_DEFAULT);
		//
		// Log.d(TAG, charDescent + "#" + CHAR_WIDTH + "," + CHAR_HEIGHT + "/"
		// + SCREEN_WIDTH + "," + SCREEN_HEIGHT);
		// }

		bitmap = Bitmap.createBitmap(SCREEN_WIDTH, SCREEN_HEIGHT,
				Config.ARGB_8888);
		canvas.setBitmap(bitmap);
		defaultPaint.setColor(Color.BLACK);
		canvas.drawRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT, defaultPaint);
		fullRedraw = true;
		redraw();

		this.postInvalidate();
		terminalActivity.refreshView();
	}

	@Override
	public void onDraw(Canvas canvas) {
		// draw
		if (this.bitmap != null) {
			canvas.drawBitmap(this.bitmap, 0, 0, defaultPaint);
		}

		// draw cursor
		if (this.buffer.isCursorVisible()) {
			cursorPaint.setColor(color[DEFAULT_FG_COLOR]);
			float x = this.buffer.getCursorColumn() * CHAR_WIDTH;
			float y = (this.buffer.getCursorRow() + this.buffer.screenBase - this.buffer.windowBase)
					* CHAR_HEIGHT;
			canvas.drawRect(x, y, x + CHAR_WIDTH, y + CHAR_HEIGHT, cursorPaint);

			// terminalActivity.scroll((int) x, (int) y);

		}

	}

	@Override
	public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
		InputConnection ic = new TermInputConnection(this);
		return ic;
	}

	/**
	 * Chinese InputMethod
	 * 
	 * @author Chen slepher (slepheric@gmail.com)
	 */
	private class TermInputConnection extends BaseInputConnection {

		public TermInputConnection(View targetView) {
			super(targetView, false);
		}

		@Override
		public boolean performEditorAction(int actionCode) {
			if (actionCode == EditorInfo.IME_ACTION_UNSPECIFIED) {
				long eventTime = SystemClock.uptimeMillis();
				sendKeyEvent(new KeyEvent(eventTime, eventTime,
						KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0));
				sendKeyEvent(new KeyEvent(SystemClock.uptimeMillis(),
						eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER,
						0));
				return true;
			}
			return false;
		}

	}

	/**
	 * Chinese InputMethod
	 * 
	 * @author Chen slepher (slepheric@gmail.com)
	 */
	@Override
	public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
		Log.d(TAG, "onKeyMultiple:" + keyCode);
		if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
			try {
				String ime_input = event.getCharacters();
				connection.write(ime_input.getBytes(host.getEncoding()));
				return true;
			} catch (SocketException e) {
				nodifyParent(e);
			} catch (IOException e) {
				try {
					connection.flush();
				} catch (IOException ioe) {
					nodifyParent(e);
				}
			} catch (NullPointerException npe) {
				Log.d(TAG, "Input before connection established ignored.");
				return true;
			}
		}
		return super.onKeyMultiple(keyCode, repeatCount, event);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {

		int eventaction = event.getAction();
		Log.d(TAG, "onTouchEvent:" + eventaction);

		switch (eventaction) {

		case MotionEvent.ACTION_DOWN: // touch down so check if the

			int y = (int) event.getRawY();
			int x = (int) event.getRawX();
			int l = (int) (y / CHAR_HEIGHT);
			int w = (int) (x / CHAR_WIDTH);
//			String url = String
//					.valueOf(buffer.charArray[buffer.windowBase + l]);
//			if (!url.startsWith("http://"))
//				break;

//			Log.d(TAG, "onTouchEvent:" + y + "/" + CHAR_HEIGHT + "=" + l + ","
//					+ url);
//			terminalActivity.showUrlDialog(url.trim());
//			break;
			if (urls[l] != null) {
				for (Url url : urls[l]) {
					if (url.pointIn(w, l))
						terminalActivity.showUrlDialog(url.url.trim());
					}
				}
			Log.d(TAG, "onTouchEvent:" + y + "/" + CHAR_HEIGHT + "=" + l);
			return true;

		case MotionEvent.ACTION_MOVE: // touch drag with the ball
			break;

		case MotionEvent.ACTION_UP:
			break;

		}

		return false;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		// TODO Auto-generated method stub
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {

		if (connection == null)
			return false;

		int metaState = event.getMetaState();

		Log.i(TAG, "onKeyDown:" + keyCode + ",metaState=" + metaState);

		try {
			if (event.getAction() == KeyEvent.ACTION_UP) {
				return false;
			}
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
				// setFontSize(fontSize + 1.5f);
				terminalActivity.changeFunctionKeyGalleryDisplay();
				return true;
			} else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
				InputMethodManager inputMethodManager = (InputMethodManager) terminalActivity
						.getSystemService(Context.INPUT_METHOD_SERVICE);
				inputMethodManager.toggleSoftInput(
						InputMethodManager.SHOW_FORCED, 0);
				// inputMethodManager.hideSoftInputFromWindow(this
				// .getWindowToken(), 0);
				return true;
			}

			// look for special chars
			boolean result = processSpecialChar(keyCode, metaState);
			if (result)
				return result;
		} catch (SocketException e) {
			e.printStackTrace();
			nodifyParent(e);
		} catch (IOException e) {
			e.printStackTrace();
			try {
				connection.flush();
			} catch (IOException ioe) {
				nodifyParent(e);
			}
		} catch (NullPointerException npe) {
			Log.d(TAG, "Input before connection established ignored.");
			return true;
		}

		return false;
	}

	public void write(byte[] b) throws IOException {
		connection.write(b);
	}

	public boolean processSpecialChar(int keyCode, int metaState)
			throws IOException {
		boolean printing = (DEFAULT_KEYMAP.isPrintingKey(keyCode) || keyCode == KeyEvent.KEYCODE_SPACE);

		if (printing) {
			if (shiftPressed) {
				metaState |= KeyEvent.META_SHIFT_ON;
				shiftPressed = false;
			}

			if (altPressed) {
				metaState |= KeyEvent.META_ALT_ON;
				altPressed = false;
			}

			int key = DEFAULT_KEYMAP.get(keyCode, metaState);

			if (ctrlPressed) {
				// Support CTRL-a through CTRL-z
				if (key >= 0x61 && key <= 0x7A)
					key -= 0x60;
				// Support CTRL-A through CTRL-_
				else if (key >= 0x41 && key <= 0x5F)
					key -= 0x40;
				else if (key == 0x20)
					key = 0x00;
				ctrlPressed = false;
			}

			connection.write(key);
			return true;
		}

		switch (keyCode) {
		case KeyEvent.KEYCODE_SHIFT_LEFT:
		case KeyEvent.KEYCODE_SHIFT_RIGHT:
			shiftPressed = true;
			return true;
		case KeyEvent.KEYCODE_ALT_LEFT:
		case KeyEvent.KEYCODE_ALT_RIGHT:
			altPressed = true;
			return true;
		case KeyEvent.KEYCODE_DEL:
			connection.write(0x08);
			return true;
		case KeyEvent.KEYCODE_ENTER:
			((vt320) buffer).keyTyped(vt320.KEY_ENTER, ' ', metaState);
			return true;
		case KeyEvent.KEYCODE_DPAD_LEFT:
			((vt320) buffer).keyPressed(vt320.KEY_LEFT, ' ', metaState);
			return true;
		case KeyEvent.KEYCODE_DPAD_UP:
			((vt320) buffer).keyPressed(vt320.KEY_UP, ' ', metaState);
			return true;
		case KeyEvent.KEYCODE_DPAD_DOWN:
			((vt320) buffer).keyPressed(vt320.KEY_DOWN, ' ', metaState);
			return true;
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			((vt320) buffer).keyPressed(vt320.KEY_RIGHT, ' ', metaState);
			return true;
		case KeyEvent.KEYCODE_DPAD_CENTER:
			// TODO: Add some visual indication of Ctrl state
			if (ctrlPressed) {
				((vt320) buffer).keyTyped(vt320.KEY_ESCAPE, ' ', 0);
				ctrlPressed = false;
			} else
				ctrlPressed = true;
			return true;
		}
		return false;
	}

	public void resetColors() {
		color = new int[] {
				0xff000000, // black
				0xffcc0000, // red
				0xff00cc00, // green
				0xffcccc00, // brown
				0xff0000cc, // blue
				0xffcc00cc, // purple
				0xff00cccc, // cyan
				0xffcccccc, // light grey
				0xff444444, // dark grey
				0xffff4444, // light red
				0xff44ff44, // light green
				0xffffff44, // yellow
				0xff4444ff, // light blue
				0xffff44ff, // light purple
				0xff44ffff, // light cyan
				0xffffffff, // white
				0xff000000, 0xff00005f, 0xff000087, 0xff0000af, 0xff0000d7,
				0xff0000ff, 0xff005f00, 0xff005f5f, 0xff005f87, 0xff005faf,
				0xff005fd7, 0xff005fff, 0xff008700, 0xff00875f, 0xff008787,
				0xff0087af, 0xff0087d7, 0xff0087ff, 0xff00af00, 0xff00af5f,
				0xff00af87, 0xff00afaf, 0xff00afd7, 0xff00afff, 0xff00d700,
				0xff00d75f, 0xff00d787, 0xff00d7af, 0xff00d7d7, 0xff00d7ff,
				0xff00ff00, 0xff00ff5f, 0xff00ff87, 0xff00ffaf, 0xff00ffd7,
				0xff00ffff, 0xff5f0000, 0xff5f005f, 0xff5f0087, 0xff5f00af,
				0xff5f00d7, 0xff5f00ff, 0xff5f5f00, 0xff5f5f5f, 0xff5f5f87,
				0xff5f5faf, 0xff5f5fd7, 0xff5f5fff, 0xff5f8700, 0xff5f875f,
				0xff5f8787, 0xff5f87af, 0xff5f87d7, 0xff5f87ff, 0xff5faf00,
				0xff5faf5f, 0xff5faf87, 0xff5fafaf, 0xff5fafd7, 0xff5fafff,
				0xff5fd700, 0xff5fd75f, 0xff5fd787, 0xff5fd7af, 0xff5fd7d7,
				0xff5fd7ff, 0xff5fff00, 0xff5fff5f, 0xff5fff87, 0xff5fffaf,
				0xff5fffd7, 0xff5fffff, 0xff870000, 0xff87005f, 0xff870087,
				0xff8700af, 0xff8700d7, 0xff8700ff, 0xff875f00, 0xff875f5f,
				0xff875f87, 0xff875faf, 0xff875fd7, 0xff875fff, 0xff878700,
				0xff87875f, 0xff878787, 0xff8787af, 0xff8787d7, 0xff8787ff,
				0xff87af00, 0xff87af5f, 0xff87af87, 0xff87afaf, 0xff87afd7,
				0xff87afff, 0xff87d700, 0xff87d75f, 0xff87d787, 0xff87d7af,
				0xff87d7d7, 0xff87d7ff, 0xff87ff00, 0xff87ff5f, 0xff87ff87,
				0xff87ffaf, 0xff87ffd7, 0xff87ffff, 0xffaf0000, 0xffaf005f,
				0xffaf0087, 0xffaf00af, 0xffaf00d7, 0xffaf00ff, 0xffaf5f00,
				0xffaf5f5f, 0xffaf5f87, 0xffaf5faf, 0xffaf5fd7, 0xffaf5fff,
				0xffaf8700, 0xffaf875f, 0xffaf8787, 0xffaf87af, 0xffaf87d7,
				0xffaf87ff, 0xffafaf00, 0xffafaf5f, 0xffafaf87, 0xffafafaf,
				0xffafafd7, 0xffafafff, 0xffafd700, 0xffafd75f, 0xffafd787,
				0xffafd7af, 0xffafd7d7, 0xffafd7ff, 0xffafff00, 0xffafff5f,
				0xffafff87, 0xffafffaf, 0xffafffd7, 0xffafffff, 0xffd70000,
				0xffd7005f, 0xffd70087, 0xffd700af, 0xffd700d7, 0xffd700ff,
				0xffd75f00, 0xffd75f5f, 0xffd75f87, 0xffd75faf, 0xffd75fd7,
				0xffd75fff, 0xffd78700, 0xffd7875f, 0xffd78787, 0xffd787af,
				0xffd787d7, 0xffd787ff, 0xffd7af00, 0xffd7af5f, 0xffd7af87,
				0xffd7afaf, 0xffd7afd7, 0xffd7afff, 0xffd7d700, 0xffd7d75f,
				0xffd7d787, 0xffd7d7af, 0xffd7d7d7, 0xffd7d7ff, 0xffd7ff00,
				0xffd7ff5f, 0xffd7ff87, 0xffd7ffaf, 0xffd7ffd7, 0xffd7ffff,
				0xffff0000, 0xffff005f, 0xffff0087, 0xffff00af, 0xffff00d7,
				0xffff00ff, 0xffff5f00, 0xffff5f5f, 0xffff5f87, 0xffff5faf,
				0xffff5fd7, 0xffff5fff, 0xffff8700, 0xffff875f, 0xffff8787,
				0xffff87af, 0xffff87d7, 0xffff87ff, 0xffffaf00, 0xffffaf5f,
				0xffffaf87, 0xffffafaf, 0xffffafd7, 0xffffafff, 0xffffd700,
				0xffffd75f, 0xffffd787, 0xffffd7af, 0xffffd7d7, 0xffffd7ff,
				0xffffff00, 0xffffff5f, 0xffffff87, 0xffffffaf, 0xffffffd7,
				0xffffffff, 0xff080808, 0xff121212, 0xff1c1c1c, 0xff262626,
				0xff303030, 0xff3a3a3a, 0xff444444, 0xff4e4e4e, 0xff585858,
				0xff626262, 0xff6c6c6c, 0xff767676, 0xff808080, 0xff8a8a8a,
				0xff949494, 0xff9e9e9e, 0xffa8a8a8, 0xffb2b2b2, 0xffbcbcbc,
				0xffc6c6c6, 0xffd0d0d0, 0xffdadada, 0xffe4e4e4, 0xffeeeeee, };
	}

	public VDUBuffer getVDUBuffer() {
		return buffer;
	}
	private int[] getColor(int currAttr){
		int fg,bg;
		// reset default colors
		fg = color[DEFAULT_FG_COLOR];
		bg = color[DEFAULT_BG_COLOR];

		// check if foreground color attribute is set
		if ((currAttr & VDUBuffer.COLOR_FG) != 0)
			fg = color[((currAttr & VDUBuffer.COLOR_FG) >> VDUBuffer.COLOR_FG_SHIFT) - 1];

		// check if background color attribute is set
		if ((currAttr & VDUBuffer.COLOR_BG) != 0)
			bg = color[((currAttr & VDUBuffer.COLOR_BG) >> VDUBuffer.COLOR_BG_SHIFT) - 1];

		// support character inversion by swapping background and
		// foreground color
		if ((currAttr & VDUBuffer.INVERT) != 0) {
			int swapc = bg;
			bg = fg;
			fg = swapc;
		}

		// if black-on-black, try correcting to grey
		// if(fg == Color.BLACK && bg == Color.BLACK) fg = Color.GRAY;

		// correctly set bold and underlined attributes if requested
		// defaultPaint.setFakeBoldText((currAttr & VDUBuffer.BOLD)!=0);
		defaultPaint
				.setUnderlineText((currAttr & VDUBuffer.UNDERLINE) != 0);

		// determine the amount of continuous characters with the
		// same
		// settings and print them all at once
		return new int[] {fg,bg};
	}
	public void redraw() {
		boolean entireDirty = buffer.update[0] || fullRedraw;

		// canvas.drawColor(color[COLOR_BG_STD]);
		// walk through all lines in the buffer
		for (int l = 0; l < buffer.height; l++) {

			// check if this line is dirty and needs to be repainted
			// also check for entire-buffer dirty flags
			if (!entireDirty && !buffer.update[l + 1])
				continue;

			// reset dirty flag for this line
			buffer.update[l + 1] = false;

			// reset urls for this line
			if (urls != null)
				urls[l] = new ArrayList<Url>();
			
			// walk through all characters in this line
			for (int c = 0; c < buffer.width; c++) {
				int addr = 0;
				int currAttr = buffer.charAttributes[buffer.windowBase + l][c];

				int color[] = getColor(currAttr);
				
				boolean stateHigh = false; //albb0920:record now DBCS state
				
				while (c + addr < buffer.width
						&& buffer.charAttributes[buffer.windowBase + l][c
								+ addr] == currAttr) {
					if(!stateHigh && buffer.getChar(c + addr, l) >= 128)   //getChar actually returns a byte, so just treat it as byte
							stateHigh = true;
					else
						stateHigh = false;
					if (buffer.getChar(c + addr, l) == '/') {  // Detect url
						String current = String.valueOf(
								buffer.charArray[buffer.windowBase + l], c, addr + 1);
						if (current.startsWith("http://") ||
								current.startsWith("https://")) {  // We have entered url
							while (c + addr < buffer.width) {  // Read in rest of url
								char url_char = buffer.getChar(c + addr, l);
								if (Character.isLetterOrDigit(url_char)) {
									addr++;
								} else if ("./@?&=-_;%#!~".contains(Character.toString(url_char))) {
									addr++;
								} else {
									break;
								}
							}
							defaultPaint.setUnderlineText(true);  // Underline the url
							
							current = String.valueOf(
									buffer.charArray[buffer.windowBase + l], c, addr); // 上面的 while 迴圈會多 +1所以不必 addr+1
									Url url = new Url(c, c+addr, l, current);
									urls[l].add(url);
									break;
						} else if (current.endsWith("http://")) {
							// Reached a url at end of read buffer. Leave the chars in buffer
							// and handle them next time we get around.
							addr -= "http://".length() - 1;
							break;
						} else if (current.endsWith("https://")) {
							addr -= "https://".length() - 1;
							break;
						}
					}
					addr++;
				}
				//albb0920.100615: We must include the full DBCS Char
				if(c + addr < buffer.width && stateHigh == true)
					addr++;

				// clear this dirty area with background color
				defaultPaint.setColor(color[1]);
				canvas.drawRect(c * CHAR_WIDTH, (l * CHAR_HEIGHT) - 1,
						(c + addr) * CHAR_WIDTH, (l + 1) * CHAR_HEIGHT,
						defaultPaint);

				// write the text string starting at 'c' for 'addr' number of
				// characters
				if ((currAttr & VDUBuffer.INVISIBLE) == 0) {
					defaultPaint.setColor(color[0]);

					char[] chars = new char[addr];
					System.arraycopy(buffer.charArray[buffer.windowBase + l],
							c, chars, 0, addr);

					String encoding = "Big5";
					if (host != null)
						encoding = host.getEncoding();
					String string = ChineseUtils.decode(chars, encoding,this.getResources());
					
					int asciiCharCount = 0;
					for (int i = 0; i < string.length(); i++) {
						char _c = string.charAt(i);

						canvas.drawText(String.valueOf(_c),
								(c + asciiCharCount) * CHAR_WIDTH,
								((l + 1) * CHAR_HEIGHT) - 4, defaultPaint);

						if ((int) _c < 128)
							asciiCharCount++;
						else
							asciiCharCount = asciiCharCount + 2;
					}
					//albb0920.100617: Check if is dual color char, must be in last char
					int lastColor[] = getColor(buffer.charAttributes[buffer.windowBase + l][c+addr-1]);
					if(!Arrays.equals(color,lastColor)){ 
						defaultPaint.setColor(lastColor[1]);
						RectF halfChar = new RectF((c+asciiCharCount-1)*CHAR_WIDTH, 
								l * CHAR_HEIGHT -1 ,
								(c+asciiCharCount)*CHAR_WIDTH, (l + 1) * CHAR_HEIGHT);
						canvas.drawRect(halfChar, defaultPaint);
						canvas.clipRect(halfChar,Op.REPLACE);
						defaultPaint.setColor(lastColor[0]);
						canvas.drawText(String.valueOf(string.charAt(string.length()-1)),
								(c + asciiCharCount-2) * CHAR_WIDTH,
								((l + 1) * CHAR_HEIGHT) - 4, defaultPaint);
						canvas.clipRect(0,0,canvas.getWidth(),canvas.getHeight(),Op.REPLACE);
					}
						
				}

				// advance to the next text block with different
				// characteristics
				c += addr - 1;
			}
		}

		// reset entire-buffer flags
		buffer.update[0] = false;
		fullRedraw = false;

		postInvalidate();

	}

	public void setVDUBuffer(VDUBuffer buffer) {
		this.buffer = buffer;

	}

	public void updateScrollBar() {

	}

	public void setColor(int index, int red, int green, int blue) {
		if (index < color.length && index >= 16)
			color[index] = 0xff000000 | red << 16 | green << 8 | blue;
	}

	protected void startConnection(final Host host) {
		this.host = host;

		new Thread(new Runnable() {
			public void run() {
				byte[] b = new byte[4096];

				try {
					String hostProtocal = host.getProtocal();
					String hostHost = host.getHost();
					String hostUser = host.getUser();
					String hostPass = host.getPass();

					int hostPort = host.getPort();

					if ("telnet".equalsIgnoreCase(hostProtocal)) {
						connection = new TelnetWrapper();
						connection.connect(hostHost, hostPort);

						if (hostUser != null && hostPass != null
								&& hostUser.length() > 0
								&& hostPass.length() > 0) {
							connection.send(hostUser + "\n");
							connection.send(hostPass + "\n");
						}

					} else if ("ssh".equalsIgnoreCase(hostProtocal)) {
						connection = new SshWrapper();
						connection.connect(hostHost, hostPort);
						connection.login(hostUser, hostPass);
						connection.send("" + "\n");

					}

					connected = true;
					while (true) {
						int n = connection.read(b);
						if (n > 0) {
							// long start = System.currentTimeMillis();
							String fullString = new String(b, 0, n, "ISO8859-1");
							// Log.i(TAG, fullString);
							((vt320) buffer).putString(fullString, host
									.getEncoding());
							// long end1 = System.currentTimeMillis();
							redraw();
							// long end2 = System.currentTimeMillis();
							// Log.i(TAG, (end1 - start) + "," + (end2 - end1));
						} else if (n < 0) {
							break;
						}
					}
					nodifyParent(null);
				} catch (Exception e) {
					e.printStackTrace();
					nodifyParent(e);
				}

			}
		}).start();
	}

	private void nodifyParent(Exception e) {
		connected = false;
		terminalActivity.disconnect(e);
	}
	
	private class Url {
		public int startX;
		public int endX;
		public int y;
	    public String url;
	    
	    public Url(int startX, int endX, int y, String url) {
	    	this.startX = startX;
		    this.endX = endX;
		    this.y = y;
		    this.url = url;
		}
		
	    public boolean pointIn(int x, int y) {
		    return (this.y == y && x >= startX && x <= endX);
		}
	}
}