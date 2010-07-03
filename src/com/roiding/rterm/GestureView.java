package com.roiding.rterm;

import tw.loli.lunaTerm.TerminalActivity;
import tw.loli.lunaTerm.TerminalView;
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
	private int footprintColor = Color.RED;
	private int footprintWidth = 5;

	private Bitmap textBitmap;
	private final Paint textPaint;
	private Canvas textCanvas;
	private int textBgColor = Color.BLUE;
	private int textColor = Color.WHITE;

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
		footprintPaint.setColor(footprintColor);
		footprintPaint.setStyle(Paint.Style.STROKE);
		footprintPaint.setStrokeWidth(footprintWidth);

		textPaint = new Paint();
		textPaint.setAntiAlias(true);
		textPaint.setTextSize(15);
		textPaint.setTypeface(Typeface.MONOSPACE);

	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);

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
	private int minGestureDistance = 50;

	private String currentGesture = "";

	private void recognize(char e) {
		Log.d("Gesture", "computeGesture:" + currentGesture + "#" + e);

		if (currentGesture.length() > 0) {
			if (currentGesture.charAt(currentGesture.length() - 1) != e) {
				currentGesture = currentGesture + ("," + e);
				Log.d("Gesture", "Gesture:" + currentGesture);
				drawText();
			}
		} else {
			currentGesture = currentGesture + e;
			drawText();
		}
	}

	private void clear() {

		if (currentGesture.length() > 0) {
			mOnGestureListener.onGestureEvent(currentGesture);
		}

		currentGesture = "";
		footprintBitmap.eraseColor(0);
		textBitmap.eraseColor(0);

		invalidate();

	}

	private void drawLine(Point p1, Point p2) {
		if (footprintBitmap == null)
			return;

		footprintCanvas.drawLine(p1.x, p1.y, p2.x, p2.y, footprintPaint);

		int x1 = Math.min(p1.x, p2.x) - 10;
		int y1 = Math.min(p1.y, p2.y) - 10;
		int x2 = Math.max(p1.x, p2.x) + 10;
		int y2 = Math.max(p1.y, p2.y) + 10;

		mRect.set(x1, y1, x2, y2);
		invalidate(mRect);
	}

	private void drawText() {
		if (textBitmap == null)
			return;

		String desc = mOnGestureListener.getGestureText(currentGesture);

		textBitmap.eraseColor(0);
		textPaint.setColor(textBgColor);
		textCanvas.drawRect(0, 0, desc.length() * 9, textBitmap.getHeight(),
				textPaint);
		textPaint.setColor(textColor);
		textCanvas.drawText(desc.toString(), 0, 15, textPaint);
		textBitmap.getWidth();

		mRect.set(0, 0, textBitmap.getWidth(), textBitmap.getWidth());
		invalidate(mRect);
	}

	private float dx = 0;
	private float dy = 0;
	private Point p;

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		Log.d(app, "onTouchEvent...action=" + ev.getAction());

		if (mOnGestureListener == null)
			Log.e(app, "there is no gesture listener");

		mGestureDetector.onTouchEvent(ev);

		int x0 = (int) ev.getX();
		int y0 = (int) ev.getY();
		Point p0 = new Point(x0, y0);
		if (ev.getAction() != MotionEvent.ACTION_DOWN) {
			drawLine(p, p0);
		}
		p = p0;

		if (ev.getAction() == MotionEvent.ACTION_UP) {
			clear();
		}
		return true;
	}

	GestureDetector mGestureDetector = new GestureDetector(
			new GestureDetector.SimpleOnGestureListener() {

				@Override
				public boolean onSingleTapConfirmed(MotionEvent e) {
					TerminalView view = terminalActivity
							.getCurrentTerminalView();
					if (view != null)
						return view.onTouchEvent(e);
					else
						return false;
				}

				@Override
				public boolean onScroll(MotionEvent e1, MotionEvent e2,
						float distanceX, float distanceY) {
					Log.d("Gesture", "onScroll:" + distanceX + "," + distanceY);

					if (Math.max(Math.abs(dx), Math.abs(dy)) >= minGestureDistance) {
						char g = GESTURE_LEFT;
						if (Math.abs(dx) > Math.abs(dy)) {
							if (dx < 0)
								g = GESTURE_RIGHT;
							else
								g = GESTURE_LEFT;
						} else {
							if (dy < 0)
								g = GESTURE_DOWN;
							else
								g = GESTURE_UP;
						}

						recognize(g);
						dx = dy = 0;
					} else {
						dx = dx + distanceX;
						dy = dy + distanceY;

					}
					return true;
				}
			});
}
