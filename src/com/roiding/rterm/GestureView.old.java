package com.roiding.rterm;

import java.lang.reflect.Array;
import java.util.ArrayList;

import com.roiding.rterm.util.ChineseUtils;

import de.mud.terminal.VDUBuffer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class GestureView extends View {

	private final String app = "TOUCH";

	private Bitmap footprintBitmap;
	private final Paint footprintPaint;
	private Canvas footprintCanvas;

	private Bitmap textBitmap;
	private final Paint textPaint;
	private final Paint testPaint;
	private Canvas textCanvas;
	private int textBgColor = Color.BLUE;
	private int textColor = Color.WHITE;
	
	int TERM_HEIGHT = 24;
	ArrayList<Url>[] urls = (ArrayList<Url>[]) Array.newInstance(ArrayList.class, TERM_HEIGHT);
	int w,h;


	private final Rect mRect = new Rect();

	private OnGestureListener mOnGestureListener;

	private TerminalActivity terminalActivity;

	public void setTerminalActivity(TerminalActivity terminalActivity) {
		this.terminalActivity = terminalActivity;
	}

	public OnGestureListener getOnGestureListener() {
		return mOnGestureListener;
	}

	public void setOnGestureListener(OnGestureListener onGestureListener) {
		this.mOnGestureListener = onGestureListener;
	}

	public GestureView(Context c, AttributeSet attrs) {
		super(c, attrs);

		footprintPaint = new Paint();
		footprintPaint.setAntiAlias(true);
//		footprintPaint.setColor(footprintColor);
//		footprintPaint.setStyle(Paint.Style.STROKE);
//		footprintPaint.setStrokeWidth(footprintWidth);

		textPaint = new Paint();
		textPaint.setAntiAlias(true);
		textPaint.setTextSize(15);
		textPaint.setTypeface(Typeface.MONOSPACE);

		testPaint = new Paint();
		testPaint.setColor(textColor);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);

		this.w=w;
		this.h=h;
		
		footprintBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_4444);
		footprintCanvas = new Canvas();
		footprintCanvas.setBitmap(footprintBitmap);

		textBitmap = Bitmap.createBitmap(w, 20, Bitmap.Config.ARGB_4444);
		textCanvas = new Canvas();
		textCanvas.setBitmap(textBitmap);
		

	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		canvas.drawBitmap(footprintBitmap, 0, 0, null);
		canvas.drawBitmap(textBitmap, 0, 0, null);
	}

	public static final char GESTURE_LEFT = 'L';
	public static final char GESTURE_RIGHT = 'R';
	public static final char GESTURE_UP = 'U';
	public static final char GESTURE_DOWN = 'D';

	private void clear() {

		footprintBitmap.eraseColor(0);
		textBitmap.eraseColor(0);

		invalidate();

	}





	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		Log.d(app, "onTouchEvent...action=" + ev.getAction());


		int x0 = (int) ev.getX();
		int y0 = (int) ev.getY();
		if ((ev.getAction() != MotionEvent.ACTION_DOWN) ||(ev.getAction() != MotionEvent.ACTION_MOVE)) {
			

			TerminalView view = terminalActivity
			.getCurrentTerminalView();
			System.out.println(view.SCREEN_WIDTH_DEFAULT);
	
			VDUBuffer buffer = view.getVDUBuffer();
			int fg, bg;
			boolean fullRedraw = true;
			int DEFAULT_FG_COLOR = 7;
			int DEFAULT_BG_COLOR = 0;
			Paint defaultPaint = new Paint();
			defaultPaint.setAntiAlias(true);
			defaultPaint.setTypeface(Typeface.MONOSPACE);
			defaultPaint.setTextSize(18.5f);
	
			boolean entireDirty = buffer.update[0] || fullRedraw;
			
			int l = Math.round((float)(y0/13.3));

			// canvas.drawColor(color[COLOR_BG_STD]);


				// check if this line is dirty and needs to be repainted
				// also check for entire-buffer dirty flags
				//if (!entireDirty && !buffer.update[1])
					//continue;

				// reset dirty flag for this line
				buffer.update[l + 1] = false;

				// reset urls for this line
				if (urls != null)
					urls[l] = new ArrayList<Url>();
						
				// walk through the lth line in the buffer
				for (int c = 0; c < buffer.width; c++) {
					int addr = 0;
					int currAttr = buffer.charAttributes[buffer.windowBase + l][c];

					// reset default colors
					fg = view.color[DEFAULT_FG_COLOR];
					bg = view.color[DEFAULT_BG_COLOR];

					// check if foreground color attribute is set
					if ((currAttr & VDUBuffer.COLOR_FG) != 0)
						fg = view.color[((currAttr & VDUBuffer.COLOR_FG) >> VDUBuffer.COLOR_FG_SHIFT) - 1];

					// check if background color attribute is set
					if ((currAttr & VDUBuffer.COLOR_BG) != 0)
						bg = view.color[((currAttr & VDUBuffer.COLOR_BG) >> VDUBuffer.COLOR_BG_SHIFT) - 1];

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
					while (c + addr < buffer.width
							&& buffer.charAttributes[buffer.windowBase + l ][c
							                                                 + addr] == currAttr) {
				
						if (buffer.getChar(c + addr, 0) == '/') {  // Detect url
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
										buffer.charArray[buffer.windowBase + l], c, addr + 1);
									Url url = new Url(c, c+addr, l, current);
									urls[0].add(url);
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

					// clear this dirty area with background color
					defaultPaint.setColor(bg);

					footprintCanvas.drawRect(0, (19 * view.CHAR_HEIGHT),
							480, 23 * view.CHAR_HEIGHT,
					defaultPaint);

					System.out.println(c);

					// write the text string starting at 'c' for 'addr' number of
					// characters
					if ((currAttr & VDUBuffer.INVISIBLE) == 0) {
						defaultPaint.setColor(fg);

						char[] chars = new char[addr];
						System.arraycopy(buffer.charArray[buffer.windowBase + l],
								c, chars, 0, addr);

						String encoding = "GBK";
						if (view.host != null)
							encoding = view.host.getEncoding();
						String string = ChineseUtils.decode(chars, encoding);

						int asciiCharCount = 0;
						for (int i = 0; i < string.length(); i++) {
							char _c = string.charAt(i);
							
						footprintCanvas.drawText(String.valueOf(_c),(c + asciiCharCount) * (view.CHAR_WIDTH + 5),
							(20 * view.CHAR_HEIGHT), defaultPaint);

							if ((int) _c < 128)
								asciiCharCount++;
							else
								asciiCharCount = asciiCharCount + 2;
						}

					}

			// advance to the next text block with different
			// characteristics
			c += addr - 1;
		}
	// reset entire-buffer flags
	buffer.update[0] = false;
	fullRedraw = false;

	postInvalidate();

	
		}
		if ((ev.getAction() == MotionEvent.ACTION_UP) || (ev.getAction() == MotionEvent.ACTION_MOVE)) {
			clear();
		}
		return true;
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
	
