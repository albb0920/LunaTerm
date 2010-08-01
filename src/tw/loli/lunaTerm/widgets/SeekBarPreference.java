package tw.loli.lunaTerm.widgets;


import tw.loli.lunaTerm.R;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;

import android.util.AttributeSet;
import android.view.View;





import android.widget.ProgressBar;
import android.widget.SeekBar;

public class SeekBarPreference extends DialogPreference {
	private SeekBar mSeekBar;

	private int mValue;
	
	public SeekBarPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		setWidgetLayoutResource(R.layout.pref_seekbar);	
		setDialogLayoutResource(R.layout.pref_seekbar_dialog);
		setDialogTitle(getTitle());
	}
	
	public SeekBarPreference(Context context) 	{
		this(context, null);
	}
		
	protected void onBindView(View view) {				
		super.onBindView(view);
		
		ProgressBar valuedisp = (ProgressBar) view.findViewById(R.id.pref_valuedisp);
		if(valuedisp != null){
			valuedisp.setMax(100);
			valuedisp.setProgress(mValue);
		}
	}
	
    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        mSeekBar = (SeekBar) view.findViewById(R.id.seekbar);
        mSeekBar.setProgress(mValue);      
    }
    
    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        
        if (positiveResult) {
            int value = mSeekBar.getProgress();
            if (callChangeListener(value)) {
            	setValue(value);
            }
        }
    }
	
	public void setValue(int value){
		mValue = value;
		persistInt(value);   
        notifyChanged();		
	}
	
	public int getValue(){
		return mValue;	
	}
	
    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
    	return a.getInt(index, 50);
    }
    
    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
    	setValue( restoreValue ? getPersistedInt(mValue) : (Integer)defaultValue);
    }
    
    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent())
            return superState;
        
        final SavedState myState = new SavedState(superState);
        myState.value = mValue;
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            super.onRestoreInstanceState(state);
            return;
        }
         
        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        setValue(myState.value);
    }

	
    private static class SavedState extends BaseSavedState {
        int value;
        
        public SavedState(Parcel source) {
            super(source);
            value = source.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(value);
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }	

        @SuppressWarnings("unused")
		public static final Parcelable.Creator<SavedState> CREATOR =
               new Parcelable.Creator<SavedState>() {
		            public SavedState createFromParcel(Parcel in) { return new SavedState(in); }
		            public SavedState[] newArray(int size) { return new SavedState[size];}
        		};
    }
}
