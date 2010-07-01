package com.roiding.rterm;

import java.lang.reflect.Field;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Gallery;
import android.widget.SpinnerAdapter;

class ButtonGallery extends Gallery {

	public ButtonGallery(Context context) {
		super(context);
	}

	public ButtonGallery(Context context, AttributeSet attrs) {

		super(context, attrs);

	}

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		System.out.println("onSingleTapUp...");

		Field f;
		try {
			f = Gallery.class.getDeclaredField("mDownTouchView");
			f.setAccessible(true);
			View mDownTouchView = (View) f.get(this);

			f = Gallery.class.getDeclaredField("mDownTouchPosition");
			f.setAccessible(true);
			int mDownTouchPosition = (Integer) f.get(this);

			f = Gallery.class
					.getDeclaredField("mShouldCallbackOnUnselectedItemClick");
			f.setAccessible(true);
			boolean mShouldCallbackOnUnselectedItemClick = (Boolean) f
					.get(this);

			int mSelectedPosition = computeHorizontalScrollOffset();
			SpinnerAdapter mAdapter = super.getAdapter();

			if (mDownTouchPosition >= 0) {
				if (mShouldCallbackOnUnselectedItemClick
						|| mDownTouchPosition == mSelectedPosition) {
					performItemClick(mDownTouchView, mDownTouchPosition,
							mAdapter.getItemId(mDownTouchPosition));
				}

			}

		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		return false;

	}
	// public void onShowPress(MotionEvent e) {
	// System.out.println("onShowPress...");
	// }

	// void onUp() {
	// System.out.println("onUp...");
	// // for (int i = getChildCount() - 1; i >= 0; i--) {
	// // getChildAt(i).setPressed(false);
	// // }
	// //
	// // setPressed(false);
	// }
}
