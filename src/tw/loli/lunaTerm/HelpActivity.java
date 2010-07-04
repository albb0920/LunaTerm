package tw.loli.lunaTerm;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;

public class HelpActivity extends Activity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_help);

		String url = "file:///android_asset/help/help.htm";
		WebView web = (WebView) findViewById(R.id.view_webview);
		web.loadUrl(url);
		web.computeScroll();
	}
}
