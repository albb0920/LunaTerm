package tw.loli.lunaTerm;

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
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.Bitmap.Config;
import android.graphics.Region.Op;
import android.os.SystemClock;
import android.text.InputType;
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
	final String TAG = "TerminalView";
	
	private ArrayList<Url>[] urls;
	
	private static final int TERM_WIDTH = 80;
	private static final int TERM_HEIGHT = 24;

	public float CHAR_WIDTH;
	public float CHAR_HEIGHT;
	private float CHAR_POS_FIX;  
	
	private int SCREEN_WIDTH;   
	private int SCREEN_HEIGHT;	
	
	private static final int SCROLLBACK = 0;	
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

	public boolean debug = false;
	
	public TerminalView(TerminalActivity context, AttributeSet attrs) {		
		super(context, attrs);
		this.terminalActivity = context;
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

		defaultPaint.setFlags(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
		defaultPaint.setTypeface(Typeface.MONOSPACE);				
		
		// Workaround to create array of ArrayList generic type.
		urls = (ArrayList<Url>[]) Array.newInstance(ArrayList.class, TERM_HEIGHT);
	}
	
	/**
	 * Change the size of virtual screen.
	 * 
	 * This will change the size of canvas we draw on.
	 * Which is planed could be bigger than actual screen size and scrolled by user.
	 * @param w width 
	 * @param h height
	 */
	public void setVScreenSize(int w, int h){
		Log.d(TAG,"Set VScreen to ("+w+","+h+")");
		// Prepare bitmap and canvas for us to draw on
		bitmap = Bitmap.createBitmap(w, h,Config.ARGB_8888);
		canvas.setBitmap(bitmap);
		defaultPaint.setColor(Color.BLACK);
		canvas.drawRect(0, 0, w, h, defaultPaint);
		
		//Calculate char size
		CHAR_WIDTH = (float) SCREEN_WIDTH / TERM_WIDTH;     
		CHAR_HEIGHT = (float) SCREEN_HEIGHT / TERM_HEIGHT;
		
		defaultPaint.setTextSize(CHAR_HEIGHT);
		defaultPaint.setTextScaleX(CHAR_WIDTH*2/CHAR_HEIGHT);
		// Because canvas.drawText() use baseline to position
		// Calculate the distance between baseline and top line for convenience
		Rect bound = new Rect();
		defaultPaint.getTextBounds("龜", 0, 1,bound); // I know this is dirty, anyone have a better solution?
		CHAR_POS_FIX = CHAR_HEIGHT - bound.bottom;		
		
		// Draw
		fullRedraw = true;
		redraw();
		this.postInvalidate();
		terminalActivity.refreshView();
	}
		
	@Override protected void onSizeChanged (int w, int h, int oldw, int oldh){		
		SCREEN_HEIGHT = h;
		SCREEN_WIDTH = w;
		
		//This is when android start us. use this w/h as base size unless user zoom
		if(oldw == 0){ 
			setVScreenSize(w,h);						
		}
	}
	
	/**
	 * Render magnifier.
	 * 
	 * @param canvas
	 * @param drawArea
	 * @param Focus
	 */
	 /* I can't come up with good way to write magnifier.
	 * So.. just do it the worst say. */
	public void renderMagnifier(Canvas c,Rect drawArea,RectF focus){   
		// Create a bitmap that can cover canvas all text
		Rect renderRegion = new Rect(
				(int)(focus.left / CHAR_WIDTH),
				(int)(focus.top / CHAR_HEIGHT),
				(int)Math.ceil(focus.right / CHAR_WIDTH),				
				(int)Math.ceil(focus.bottom / CHAR_HEIGHT)		
		);
		float ratioX = drawArea.width() / focus.width();
		float ratioY = drawArea.height() / focus.height();
		Bitmap magBitmap = Bitmap.createBitmap((int)(renderRegion.width()*CHAR_WIDTH*ratioX),(int)(renderRegion.height()*CHAR_HEIGHT*ratioY), Config.ARGB_8888);
		Canvas magedCanvas = new Canvas(magBitmap);
		Paint paint = new Paint(defaultPaint);
		paint.setTextSize(CHAR_HEIGHT*ratioY);
		paint.setTextScaleX(paint.getTextScaleX()*ratioY/ratioX);

		renderText(magedCanvas,new Rect(0,0,magedCanvas.getWidth(),magedCanvas.getHeight()),paint,renderRegion.top,renderRegion.left);
		c.clipRect(drawArea);
		c.drawBitmap(magBitmap, 
				drawArea.left - (focus.left - renderRegion.left * CHAR_WIDTH)*ratioX ,
				drawArea.top - (focus.top - renderRegion.top * CHAR_HEIGHT)*ratioY
				, new Paint());
	}
	
	public void renderText(Canvas canvas,Rect drawArea,Paint paint,int row, int col){
		/////////////////////////////
		Paint testPaint = new Paint();
		testPaint.setColor(Color.WHITE);
		testPaint.setStrokeWidth(1);
		/////////////////////////////	
		
		canvas.clipRect(drawArea,Op.REPLACE);
		float charWidth = paint.getTextSize() / 2 * paint.getTextScaleX();
		float charHeight = paint.getTextSize();
		

		
		Rect bound = new Rect();
		paint.getTextBounds("龜", 0, 1,bound); // I know this is dirty, anyone have a better solution?
		float decent_fix = charHeight - bound.bottom;
				
		int toRow = row + (int)Math.ceil(drawArea.height()/charHeight);
		int toCol = col + (int)Math.ceil(drawArea.width()/charWidth);
		if(toRow >= buffer.getRows())
			toRow = buffer.getRows() -1;
		if(toCol >= buffer.getColumns())
			toCol = buffer.getColumns() -1;

		for(int r=row; r <= toRow ;r++){
			int c;
			boolean stateHigh = false;
			RectF localRect = new RectF(
					drawArea.left,
					drawArea.top + (r-row) * charHeight,
					drawArea.right,
					drawArea.top + (r-row+1) * charHeight);

			for(c=0; c<col; c++) //We should move charset decode to terminal class
				stateHigh = (!stateHigh && buffer.getChar(c, r) >= 128);
			if(stateHigh){
				c--;
				stateHigh = false;
				localRect.left -= charWidth;
			}
			
			for(;c <= toCol; c++){
				int ptr = 0;
				int currAttr = buffer.charAttributes[buffer.windowBase + r][c];
				
				while(c+ptr <= toCol && currAttr == buffer.charAttributes[buffer.windowBase + r][c+ptr]){					
					stateHigh = (!stateHigh && buffer.getChar(c+ptr, r) >= 128);
					ptr++;
				}
				if(stateHigh){
					ptr++;
					stateHigh = false;
				}			
				int color[] = getColor(currAttr);
				
				paint.setColor(color[1]);
				localRect.right = localRect.left + ptr*charWidth;
				canvas.drawRect(localRect,paint);

				char[] chars = new char[ptr];
				System.arraycopy(buffer.charArray[buffer.windowBase + r],
				c, chars, 0, ptr);
				
				String encoding = (host != null)? host.getEncoding():"Big5";
				String string = ChineseUtils.decode(chars, encoding,this.getResources());		
				paint.setColor(color[0]);
				
				// Since Android's MONOFACE is not really MONOFACE..... We have to postion by our self
				int colCount = 0;
				for(int pos = 0; pos < string.length(); pos++){
					char ch = string.charAt(pos);
					canvas.drawText(
						String.valueOf(ch),
						localRect.left + colCount*charWidth,
						localRect.top+decent_fix,
						paint);					
					if(ch > 128)
						colCount++;
					colCount++;					
				}
				int lastColor[] = getColor(buffer.charAttributes[buffer.windowBase + r][c+ptr-1]);
				if(!Arrays.equals(color,lastColor)){ 
					localRect.left = localRect.right - charWidth; 
					color = getColor( buffer.charAttributes[buffer.windowBase + r][c+ptr-1]);
					canvas.clipRect(localRect, Op.REPLACE);
					paint.setColor(color[1]);
					canvas.drawRect(localRect, paint);
					paint.setColor(color[0]);
					canvas.drawText(String.valueOf(string.charAt(string.length()-1)) , localRect.left-charWidth, localRect.top+decent_fix, paint);
				}
				
				
				c+= ptr -1;
				canvas.clipRect(drawArea,Op.REPLACE);
				
				localRect.left = localRect.right;				
			}					
		}

		
	}
	
	
	@Override
	public void onDraw(Canvas viewCanvas) {
		Log.v(TAG,"onDraw()");
		// draw
		if (this.bitmap == null)
			return;
		boolean entireDirty = buffer.update[0] || fullRedraw;
		
		// walk through all lines in the buffer
		for (int l = 0; l < buffer.height; l++) {

			// check if this line needs redraw.
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
				
				boolean stateHigh = false; //albb0920: record DBCS state
				// find whole string with sane colors
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
									buffer.charArray[buffer.windowBase + l], c, addr); 
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
				
				/* TODO: We should replace float rects with int ones to get better render
				canvas.drawRect(new Rect((int)(c * CHAR_WIDTH),(int)( l * CHAR_HEIGHT),
						(int)((c + addr) * CHAR_WIDTH),(int)( (l + 1) * CHAR_HEIGHT)), defaultPaint);
				*/								
				canvas.drawRect(c * CHAR_WIDTH,(int) l * CHAR_HEIGHT,
						(c + addr) * CHAR_WIDTH, (l + 1) * CHAR_HEIGHT,
						defaultPaint);
				
				// write the text string starting at 'c' for 'addr' number of
				// characters

					defaultPaint.setColor(color[0]);

					char[] chars = new char[addr];
					System.arraycopy(buffer.charArray[buffer.windowBase + l],
							c, chars, 0, addr);

					String encoding = "Big5";
					if (host != null)
						encoding = host.getEncoding();
					String string = ChineseUtils.decode(chars, encoding,this.getResources());
					
					if(string.length()==0){ // Conversion failed
						c += addr - 1;
						continue;
					}
					
					int asciiCharCount = 0;
					for (int i = 0; i < string.length(); i++) {
						char _c = string.charAt(i);

						canvas.drawText(String.valueOf(_c),
								(c + asciiCharCount) * CHAR_WIDTH,
								l * CHAR_HEIGHT + CHAR_POS_FIX, defaultPaint);
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
								l * CHAR_HEIGHT + CHAR_POS_FIX , defaultPaint);

						canvas.clipRect(0,0,SCREEN_WIDTH,SCREEN_HEIGHT,Op.REPLACE);
					}
						
				

				// advance to the next text block with different
				// characteristics
				c += addr - 1;
			}
		}

		// reset entire-buffer flags
		buffer.update[0] = false;
		fullRedraw = false;		
			
		viewCanvas.drawBitmap(this.bitmap, 0, 0, defaultPaint);		

		// draw cursor
		if (this.buffer.isCursorVisible()) {
			cursorPaint.setColor(color[DEFAULT_FG_COLOR]);
			float x = this.buffer.getCursorColumn() * CHAR_WIDTH;
			float y = (this.buffer.getCursorRow() + this.buffer.screenBase - this.buffer.windowBase)
					* CHAR_HEIGHT;
			viewCanvas.drawRect(x, y, x + CHAR_WIDTH, y + CHAR_HEIGHT, cursorPaint);

			// terminalActivity.scroll((int) x, (int) y);

		}
	}


	/**
	 * Chinese InputMethod
	 * 
	 * @author Chen slepher (slepheric@gmail.com)
	 */
	@Override
	public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
		outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI;
		outAttrs.inputType = InputType.TYPE_CLASS_TEXT; //albb0920.100706: Without this, HTC_CIME's Chewing KB refuse to work
		InputConnection ic = new TermInputConnection(this);
		return ic;
	}
	
	@Override
	public boolean onCheckIsTextEditor(){
		return true;		
	}
	
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
		//TODO: Let user customize color plate
		color = new int[] {
				Color.rgb(  0,  0,  0),    	// black 
				Color.rgb(128,  0,  0),  	// red
				Color.rgb(  0,128,  0),  	// green
				Color.rgb(128,128,  0),		// yellow
				Color.rgb(  0,  0,128),    	// blue
				Color.rgb(128,  0,128),		// purple 
				Color.rgb(  0,128,128),		// cyan
				Color.rgb(192,192,192),     // light gray
				Color.rgb(128,128,128),  	// dark gray
				Color.rgb(255,	0,	0),		// light red
				Color.rgb(	0,255,	0),		// light green
				Color.rgb(255,255,	0),		// yellow
				Color.rgb(  0,  0,255),		// light blue				
				Color.rgb(255,  0,255),		// light purple
				Color.rgb(  0,255,255),		// light cyan
				Color.rgb(255,255,255)		// white
				};
	}

	public VDUBuffer getVDUBuffer() {
		return buffer;
	}
	
	/* 
	 * albb0920:
	 *   This method is here to make OS knows the correct position to pan.
	 *   Although the method name seems to make sense, 
	 *   the API document didn's mention they'll pan based on this. Orz
	*/
	public void   getFocusedRect(Rect r){   
		// Just report cursor line.
		r.top = (int) (this.buffer.getCursorRow() * CHAR_HEIGHT);
		r.bottom = r.top + (int)CHAR_HEIGHT;
		r.left = 0;
		r.right = SCREEN_HEIGHT;		
	}
	
	/**
	 * Return color set of currAttr
	 * @param currAttr
	 * @return array(foreground,background)
	 */
	private int[] getColor(int currAttr){
		int fg,bg;
		// reset default colors
		fg = DEFAULT_FG_COLOR; //albb0920: this will be convert to color later
		bg = color[DEFAULT_BG_COLOR];

		// check if foreground color attribute is set
		if ((currAttr & ( VDUBuffer.COLOR_FG  )) != 0)
			fg = ((currAttr & VDUBuffer.COLOR_FG) >> VDUBuffer.COLOR_FG_SHIFT) - 1;
		
		// bright color
		if ((currAttr & VDUBuffer.BOLD) != 0)
			fg |= 8;
		
		// albb0920.100709: now we know the fg color
		fg = color[fg];
		
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

		defaultPaint
				.setUnderlineText((currAttr & VDUBuffer.UNDERLINE) != 0);

		return new int[] {fg,bg};
	}
	public void redraw() {
		Log.v(TAG,"redraw()");
		
		postInvalidate(); /* render should always be done in onDraw() */
	}

	
	
	
	public void setVDUBuffer(VDUBuffer buffer) {
		this.buffer = buffer;
	}

	/**
	 * Not Implemented.
	 * We don't even have scroll buffer. So...(ry. 
	 */
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