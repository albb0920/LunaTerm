package tw.loli.lunaTerm.widgets;


import tw.loli.lunaTerm.R;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class SeekBarPreference extends Preference implements OnSeekBarChangeListener {
	private int mValue;
	
	public SeekBarPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		setWidgetLayoutResource(R.layout.pref_seekbar);	
	}
	
	public SeekBarPreference(Context context) 	{
		this(context, null);
	}
	
	
	protected void onBindView(View view) {
					
		super.onBindView(view);
		
		SeekBar seekbar = (SeekBar) view.findViewById(R.id.pref_seekbar);
		if(seekbar != null){
			seekbar.setMax(100);
			seekbar.setProgress(mValue);
			seekbar.setOnSeekBarChangeListener(this);
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		int newValue = progress;
		
        if (!callChangeListener(newValue)){
        	seekBar.setProgress(mValue);
            return;
        }
        
        setValue(progress);
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
    
	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {}
	
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
