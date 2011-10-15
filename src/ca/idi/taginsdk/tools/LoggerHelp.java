package ca.idi.taginsdk.tools;

/**
 * Komodo Lab: Tagin! Project: 3D Tag Cloud
 * Google Summer of Code 2011
 * @authors Jorge Silva and Primal Pappachan
 */

import ca.idi.taginsdk.Helper;
import ca.idi.taginsdk.R;
import android.app.Activity;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;

public class LoggerHelp extends Activity {
	
	private Button tLearnMore, tStartLogging;
	private LinearLayout tMore;
	private ViewFlipper mFlipper;
	private TextView mLearn;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.more);
		
		tLearnMore = (Button) findViewById(R.id.More_Button01);
		tStartLogging = (Button) findViewById(R.id.More_Button02);
		tMore = (LinearLayout) findViewById(R.id.More_Container);
		mFlipper = (ViewFlipper) findViewById(R.id.More_ViewFlipper);
		mLearn = (TextView) findViewById(R.id.More_TextView01);
		mLearn.setMovementMethod(LinkMovementMethod.getInstance());
		
		final Animation anim = (Animation) AnimationUtils.loadAnimation(LoggerHelp.this, R.anim.push_left_out);
		
		tStartLogging.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if(CheckWiFiEnabled())
					startActivity(new Intent(LoggerHelp.this, Logger.class)); //Starting the Logging Activity
				else{
					Helper.showToast(LoggerHelp.this, "Please enable WiFi");
				}
			}
		});

		tLearnMore.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if(tLearnMore.getText().toString().equals("Previous")){
					tLearnMore.setText("Next");
				}
				else{
					tLearnMore.setText("Previous");
				}
				mFlipper.showNext(); //Flips into the next view in the ViewFlipper
				tMore.startAnimation(anim);
			}
		});
	}
	
    public Boolean CheckWiFiEnabled(){
    	WifiManager mWifiManager;
    	mWifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
    	if (mWifiManager.isWifiEnabled()) {
    		return true;
    	}
    	else{
    		return false;
    	}
    }
}
