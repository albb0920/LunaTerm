package tw.loli.lunaTerm;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import android.content.Context;
import android.content.SharedPreferences;
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
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.method.MetaKeyKeyListener;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
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
	
	private Typeface specialTypeface;
	private float specialDecent; 
	
	private final Canvas canvas = new Canvas();
	private boolean ctrlPressed;
	private long metaState = 0;
	private final float xdpi, ydpi; 
	
	public Wrapper connection;
	public TerminalActivity terminalActivity;
	public Host host;

	public boolean debug = false;
	
	public TerminalView(TerminalActivity context, AttributeSet attrs) {		
		super(context, attrs);
		this.terminalActivity = context;
		setFocusable(true);
		setFocusableInTouchMode(true);
		DisplayMetrics metrics = new DisplayMetrics();
		context.getWindowManager().getDefaultDisplay().getMetrics(metrics);
		xdpi = metrics.xdpi;
		ydpi = metrics.ydpi;
		

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
		
		specialTypeface = Typeface.createFromAsset(this.getResources().getAssets(), "SpecialChar.ttf");
		
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
		CHAR_WIDTH = (float) w / TERM_WIDTH;     
		CHAR_HEIGHT = (float) h / TERM_HEIGHT;
		
		defaultPaint.setTextSize(CHAR_HEIGHT);
		defaultPaint.setTextScaleX(CHAR_WIDTH*2/CHAR_HEIGHT);
		
		// Because canvas.drawText() use baseline to position
		// Calculate the distance between baseline and top line for convenience
		// I think we have to make a private method for decent measure? 
		Rect bound = new Rect();
		defaultPaint.getTextBounds("龜", 0, 1,bound); // I know this is dirty, anyone have a better solution?
		CHAR_POS_FIX = CHAR_HEIGHT - bound.bottom;		
		
		Paint specialPaint = new Paint(defaultPaint);
		specialPaint.setTypeface(specialTypeface);
		specialPaint.getTextBounds("▇", 0, 1,bound);
		specialDecent = CHAR_HEIGHT - bound.bottom;			
		
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

		renderText(magedCanvas,new RectF(0,0,magedCanvas.getWidth(),magedCanvas.getHeight()),paint,renderRegion.top,renderRegion.left);
		c.clipRect(drawArea);
		c.drawBitmap(magBitmap, 
				drawArea.left - (focus.left - renderRegion.left * CHAR_WIDTH)*ratioX ,
				drawArea.top - (focus.top - renderRegion.top * CHAR_HEIGHT)*ratioY
				, new Paint());
	}
	
	public void renderText(Canvas canvas,int row){
		RectF r = new RectF(0,row*CHAR_HEIGHT,CHAR_WIDTH * TERM_WIDTH,(row+1)*CHAR_HEIGHT);
		renderText(canvas,r,null,row,0);
	}
		
	private static int round(float x) {
		/* I got this from com.android.internal.util.FastMath. */
		/* I don't think it well make us any faster, but can help us type less code */
        long lx = (long)(x * (65536 * 256f));
        return (int)((lx + 0x800000) >> 24);
	}
	
	public void renderText(Canvas canvas,RectF drawArea,Paint paint,int row, int col){
		// We round floats ourself because the direct use RectF to paint cause dirty lines.		
		// TODO: The better approach: mix the color if a pixel is shared.
		// TODO: Draw rect chars our self.
		
		canvas.clipRect(drawArea,Op.REPLACE);
		
		float decent = CHAR_POS_FIX, sp_decent = specialDecent, charWidth = CHAR_WIDTH, charHeight = CHAR_HEIGHT;
		if(paint == null){
			paint = defaultPaint;				
		}else{
			charWidth = paint.getTextSize() / 2 * paint.getTextScaleX();
			charHeight = paint.getTextSize();
			Rect bound = new Rect();
			paint.getTextBounds("龜", 0, 1,bound); 
			decent = charHeight - bound.bottom;
			
			Paint sPaint = new Paint(paint);
			sPaint.setTypeface(specialTypeface);
			sPaint.getTextBounds("▇", 0, 1,bound); 
			sp_decent = charHeight - bound.bottom;
		}	
		
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
				
				// ptr is the "length" of same attr char
				
				while(c+ptr <= toCol && currAttr == buffer.charAttributes[buffer.windowBase + r][c+ptr]){					
					stateHigh = (!stateHigh && buffer.getChar(c+ptr, r) >= 128);
					ptr++;
				}
				if(stateHigh && c+ptr+1 < TERM_WIDTH - 1){ 
					ptr++;
					stateHigh = false;
				}		

				int color[] = getColor(currAttr);
				
				paint.setColor(color[1]);
				localRect.right = localRect.left + ptr*charWidth;

				canvas.drawRect(round(localRect.left),
								round(localRect.top),
								round(localRect.right),
								round(localRect.bottom),						
								paint);

				char[] chars = new char[ptr];
				System.arraycopy(buffer.charArray[buffer.windowBase + r],
				c, chars, 0, ptr);
				
				String encoding = (host != null)? host.getEncoding():"Big5";
				String string = ChineseUtils.decode(chars, encoding,this.getResources());		
				paint.setColor(color[0]);
				
				// Since Android's MONOFACE is not really MONOFACE..... We have to postion by our self
				int colCount = 0;
				String ch = null;
				float chDecent = decent;
				for(int pos = 0; pos < string.length(); pos++){
					ch = string.substring(pos, pos+1);
					chDecent = decent;
					/* hrs.110301: fix decent bug - use special chars [dirty] */
					if( (colCount < ptr && chars[colCount] >= 0x21 && chars[colCount] <= 0x7E) ||
						(colCount+1 < ptr && ((chars[colCount] == 0xA1 && chars[colCount+1] >= 0x41) || 
						(chars[colCount] == 0xA2 && (
							  chars[colCount+1] < 0x49 ||
							 (chars[colCount+1] > 0x62 && chars[colCount+1]< 0xAE)))))){
						paint.setTypeface(specialTypeface);
						chDecent = sp_decent;
					}else{
						paint.setTypeface(Typeface.MONOSPACE);
					}
						
					
					canvas.drawText(
						ch,
						localRect.left + colCount*charWidth,
						localRect.top+chDecent,
						paint);					
					if(ch.charAt(0) > 128)
						colCount++;
					colCount++;					
				}
				int lastColor[] = getColor(buffer.charAttributes[buffer.windowBase + r][c+ptr-1]);
				if(!Arrays.equals(color,lastColor) && ch != null){  /* null happens */
					localRect.left = localRect.right - charWidth;
					color = getColor( buffer.charAttributes[buffer.windowBase + r][c+ptr-1]);
					canvas.clipRect(localRect, Op.REPLACE);
					paint.setColor(color[1]);
					canvas.drawRect(round(localRect.left),
							round(localRect.top),
							round(localRect.right),
							round(localRect.bottom),						
							paint);
					paint.setColor(color[0]);
					canvas.drawText(ch , localRect.left-charWidth, localRect.top+chDecent, paint);
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
			
			// redraw
			renderText(canvas,l);
			
			// reset urls for this line
			if (urls != null)
				urls[l] = new ArrayList<Url>();
			
			// look for url
			int pos = 0, addr;
			String current = String.valueOf(buffer.charArray[buffer.windowBase + l]);
			while( (pos = current.indexOf("://",pos)) != -1){
				// substring's returns a string contains [start,end-1]    
				if(pos >= 4 && current.substring(pos-4, pos).equalsIgnoreCase("http"))
					addr = 4;
				else if(pos >= 5 && current.substring(pos-5, pos).equalsIgnoreCase("https"))
					addr = 5;
				else
					break;
				
				pos -= addr;
				addr += 3;
				Log.v(TAG,"URL START AT"+pos);
				while(pos+addr+1<current.length()){
					char thisChar = current.charAt(pos+addr+1);				
					if(!Character.isLetterOrDigit(thisChar) &&  
						! "./@?&=-_;%#!~".contains(Character.toString(thisChar)) )
						break;
					addr++;
				}
				
				String url = current.substring(pos,pos+addr+1);
				Log.v(TAG,"URL is:"+url);
				urls[l].add(new Url(pos,pos+addr,l,url));
				defaultPaint.setColor(Color.BLUE);
				canvas.drawLine(pos*CHAR_WIDTH, (l+1)*CHAR_HEIGHT -1, (pos+addr+1)*CHAR_WIDTH, (l+1)*CHAR_HEIGHT-1, defaultPaint);
				pos+=addr+1;
			}
		}
		// reset entire-buffer flags
		buffer.update[0] = false;
		fullRedraw = false;		
		
		// Calculate cursor
		int curCol = this.buffer.getCursorColumn(), 
			curRow = (this.buffer.getCursorRow() + this.buffer.screenBase - this.buffer.windowBase);
		float	cursorX = curCol * CHAR_WIDTH,
				cursorY = curRow  * CHAR_HEIGHT;
        
        float scroll = 0;
        
		// albb0920: Manually scroll, requestRectangleOnScreen(cursor,true) doesn't seems to work well
        //            Rewrite this if we can get requestRectangleOnScreen work well.

			int viewPos[] = new int[2];
			getLocationOnScreen(viewPos);
			//Log.v(TAG,"On Screen Pos: "+viewPos[0]+","+viewPos[1]);
			if(cursorY+viewPos[1]<0){ // ime is up and we were blocked.
				
				scroll = -1 * (cursorY+viewPos[1]) + (SCREEN_HEIGHT + viewPos[1] - CHAR_HEIGHT)/2;
				if(scroll > -1 *viewPos[1])
					scroll = -1 *viewPos[1];

				Log.v(TAG,"SCROLL FIX"+scroll);
			}						
				
		viewCanvas.drawBitmap(this.bitmap, 0, 0+scroll, defaultPaint);		

		// draw cursor
		if (this.buffer.isCursorVisible()) {
			cursorPaint.setColor(Color.argb(128, 0, 255, 0));
			viewCanvas.drawRect(cursorX, cursorY+scroll, cursorX + CHAR_WIDTH, cursorY + CHAR_HEIGHT + scroll, cursorPaint);						
		}
	}


	/**
	 * Chinese InputMethod
	 * 
	 * @author Chen slepher (slepheric@gmail.com)
	 */
	@Override
	public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
		// We need this private flag to make candidate selection for IME usable.
		// This is the value for API level between 5 and 11, value has changed since then.
		// It seems that after lv11, setting this flag(0x2000000) is no longer necessary.
		final int IME_FLAG_NO_FULLSCREEN = 0x80000000;
		
		outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI | IME_FLAG_NO_FULLSCREEN; 
		outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS; //albb0920.100706: Without this, HTC_CIME's Chewing KB refuse to work
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
		public boolean  setComposingText  (CharSequence text, int newCursorPosition){
			Log.v(TAG,"setComposingText: "+text);
			if((TerminalActivity.termActFlags & TerminalActivity.FLAG_SHOW_EXTRACT_UI) !=0 && text.length()>0 && text.charAt(0)>128){
				terminalActivity.showInputHelper();
				return false;
			}
			Log.v(TAG,"onCommit: "+text);
			return super.setComposingText(text, newCursorPosition);
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
			Log.v(TAG, "Got touch ev when url is "+urls.length);
			int y = (int) event.getY();
			int x = (int) event.getX();
			int l = (int) (y / CHAR_HEIGHT);
			int w = (int) (x / CHAR_WIDTH);
			float lastDiff = 0f;
			Url lastUrl = null;
			for(ArrayList<Url> lineUrls : urls){
				for(Url url : lineUrls){
					if (l == url.y && w >= url.startX && w <= url.endX){				
						terminalActivity.showUrlDialog(url.url.trim());
						return true;						
					}else{
						float diff = (l == url.y)? 0 : 
										(l < url.y) ? url.y * CHAR_HEIGHT - y : y - (url.y+1) * CHAR_HEIGHT;
						diff /= ydpi;
						if(w < url.startX)
							diff += (url.startX * CHAR_WIDTH - x) / xdpi;
						else if(w > url.endX)
							diff += (x - url.endX * CHAR_WIDTH)/ xdpi;
						if(lastDiff == 0 || diff < lastDiff ){
							lastDiff = diff;
							lastUrl = url;
						}
					}
				}
			}
			if(lastDiff > 0 && lastDiff <= 0.0787 ){ // accept 0.2cm difference				
				terminalActivity.showUrlDialog(lastUrl.url.trim());
				return true;						
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
		metaState = MetaKeyKeyListener.handleKeyUp(metaState, keyCode, event);
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {		
		if (connection == null)
			return false;
		
		try {
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
				terminalActivity.changeFunctionKeyGalleryDisplay();
				return true;
			} else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
				InputMethodManager inputMethodManager = (InputMethodManager) terminalActivity
						.getSystemService(Context.INPUT_METHOD_SERVICE);
				inputMethodManager.toggleSoftInput(
						InputMethodManager.SHOW_FORCED, 0);
				return true;
			}
			
			if(metaState == (metaState = MetaKeyKeyListener.handleKeyDown(metaState, keyCode, event))){
				boolean result = processSpecialChar(keyCode,MetaKeyKeyListener.getMetaState(metaState));
				metaState = MetaKeyKeyListener.adjustMetaAfterKeypress(metaState);
				if(result)
					return true;
			}

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
	
	public boolean processSpecialChar(int keyCode, int mState) throws IOException{
		boolean printing = (DEFAULT_KEYMAP.isPrintingKey(keyCode) || keyCode == KeyEvent.KEYCODE_SPACE);
		if (printing) {			
			int key = DEFAULT_KEYMAP.get(keyCode, mState);
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
		case KeyEvent.KEYCODE_DEL:
			connection.write(0x08);
			return true;
		case KeyEvent.KEYCODE_ENTER:
			((vt320) buffer).keyTyped(vt320.KEY_ENTER, ' ', mState);
			return true;
		case KeyEvent.KEYCODE_DPAD_LEFT:
			((vt320) buffer).keyPressed(vt320.KEY_LEFT, ' ', mState);
			return true;
		case KeyEvent.KEYCODE_DPAD_UP:
			((vt320) buffer).keyPressed(vt320.KEY_UP, ' ', mState);
			invalidate();
			return true;
		case KeyEvent.KEYCODE_DPAD_DOWN:
			((vt320) buffer).keyPressed(vt320.KEY_DOWN, ' ', mState);
			invalidate();
			return true;
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			((vt320) buffer).keyPressed(vt320.KEY_RIGHT, ' ', mState);
			return true;
		case KeyEvent.KEYCODE_TAB:
			((vt320) buffer).keyPressed(vt320.KEY_TAB, ' ', mState);
			return true;
		case KeyEvent.KEYCODE_SEARCH:
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
	 *   This was used to tell OS where to pan.
	 *   Unfortunately, the method requestFocusRect() seems to be broken with HTC CIME
	 *   (If you request a rect that is in the middle of the screen, It will be broken, blame HTC)
	 *   And I can't find a good way to get IME height.
	 *   So we lie we are in that last line to force system scroll our whole view above IME.
	 *   And we manually scroll it back. WTF
	 */
	public void   getFocusedRect(Rect cursor){   
		// Report the last line
		cursor.bottom = SCREEN_HEIGHT;
		cursor.top = (int) (cursor.bottom - CHAR_HEIGHT);
		cursor.left = 0;
		cursor.right = SCREEN_HEIGHT;
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
				int delay = 2000;

				try {
					String hostProtocal = host.getProtocal();
					String hostHost = host.getHost();
					int hostPort = host.getPort();
					
					if ("telnet".equalsIgnoreCase(hostProtocal)) {
						connection = new TelnetWrapper();
						connection.connect(hostHost, hostPort);
					}  /* else if ("ssh".equalsIgnoreCase(hostProtocal)) {
						connection = new SshWrapper();
						connection.connect(hostHost, hostPort);
					} */
					
					TerminalView.this.postDelayed(new Runnable() {
						public void run() {
							try {
								String hostUser = host.getUser();
								String hostPass = host.getPass();
				
								String hostProtocal = host.getProtocal();
								
								if ("telnet".equalsIgnoreCase(hostProtocal)) {
				
									if (hostUser != null && hostPass != null
											&& hostUser.length() > 0
											&& hostPass.length() > 0) {
										connection.send(hostUser + "\r");
										
										connection.send(hostPass + "\r");
									}
				
								} else if ("ssh".equalsIgnoreCase(hostProtocal)) {
				
									connection.login(hostUser, hostPass);
									connection.send("" + "\r");
				
								}			
							} catch (Exception e) {
								e.printStackTrace();
								nodifyParent(e);
							}
						}
					}, delay);

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
	}
}