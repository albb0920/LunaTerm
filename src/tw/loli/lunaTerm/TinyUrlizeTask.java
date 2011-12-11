package tw.loli.lunaTerm;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;


import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;

public class TinyUrlizeTask extends AsyncTask<String, Void, String> {
	private static final String apiUrl = "http://tinyurl.com/api-create.php?url=%s";
	private final Activity activity;
	private final OnShortUrlCreatedListener onShortUrlCreatedListener;
	private final ProgressDialog dialog;
	
	
	public TinyUrlizeTask(Activity activity, OnShortUrlCreatedListener onShortUrlCreatedListener){
		super();
		this.activity = activity;
		this.onShortUrlCreatedListener = onShortUrlCreatedListener;
		this.dialog = new ProgressDialog(activity);
	}
	
	@Override
	protected void onPreExecute(){
		dialog.setMessage(activity.getString(R.string.terminal_short_url_processing));
		dialog.show();
	}
	
	@Override
	protected String doInBackground(String... params) {
		HttpClient client = new DefaultHttpClient();
		try {
			HttpResponse resp =  client.execute(new HttpGet(String.format(apiUrl, params[0])));
			return EntityUtils.toString(resp.getEntity());
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	@Override
	protected void onPostExecute(String shortUrl) {
		dialog.dismiss();
		onShortUrlCreatedListener.onShortUrlCreated(shortUrl);
	}
	
	interface OnShortUrlCreatedListener{
		void onShortUrlCreated(String shortUrl);
	}
}
