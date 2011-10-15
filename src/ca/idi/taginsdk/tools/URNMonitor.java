package ca.idi.taginsdk.tools;

/**
 * Komodo Lab: Tagin! Project: 3D Tag Cloud
 * Google Summer of Code 2011
 * @authors Jorge Silva and Primal Pappachan
 */


import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import ca.idi.taginsdk.Helper;
import ca.idi.taginsdk.R;
import ca.idi.taginsdk.TaginDatabase;
import ca.idi.taginsdk.TaginProvider;
import ca.idi.taginsdk.TaginURN;

public class URNMonitor extends ListActivity {
	private TextView mURNView;
	private Button mGetURNButton;
	private ProgressDialog mProgressDialog;
	public String URN;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
		if (Helper.DEBUG) android.os.Debug.waitForDebugger();
    	
		//android.os.Debug.startMethodTracing("tagin");
		
    	setContentView(R.layout.simple);

    	registerReceiver(mReceiver, new IntentFilter(TaginURN.ACTION_URN_READY));

		mGetURNButton = (Button) findViewById(R.id.U_Button01);
		mURNView = (TextView) findViewById(R.id.U_TextView01);
		
		mGetURNButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
					fetchURN();
					fillData();
			}
		});
    }
    
    private void fetchURN(){
    	if(CheckWiFiEnabled()){
    		showDialog("Please wait");
    		startService(new Intent(TaginURN.INTENT_START_SERVICE));
    	}
    	else{
			Helper.showToast(this, "Please enable WiFi and try again");
		}
    }
    
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(TaginURN.ACTION_URN_READY)) {
				Log.i(Helper.TAG, TaginURN.getURN());
				mURNView.setText(TaginURN.getURN());
			}
			closeDialog();
		}
	};

	private void showDialog(String message) {
		mProgressDialog = ProgressDialog.show(this, "Please wait...", 
				message , true, true);
	}

	private void closeDialog() {
		if (mProgressDialog != null)
			mProgressDialog.dismiss();
	}
	
	private void fillData() {
        // Get all of the rows from the database and create the URN list
        Cursor mCursor = getContentResolver().query(TaginProvider.URN_FINGERPRINTS_URI,
        				null, null, null, null);
        startManagingCursor(mCursor);
 
        String[] from = new String[]{TaginDatabase._ID, TaginDatabase.MODIFIED, TaginDatabase.URN};
        int[] to = new int[]{R.id.Row_TextView01, R.id.Row_TextView02, R.id.Row_TextView03};
        
        SimpleCursorAdapter urns = new SimpleCursorAdapter(this, R.layout.row, mCursor, from, to);
        setListAdapter(urns);
    }
	
	 public Boolean CheckWiFiEnabled(){
	    WifiManager mWifiManager;
	    mWifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
	    if (mWifiManager.isWifiEnabled()) return true;
	    else return false;   		
	}    		

	@Override
    public void onDestroy () {
    	super.onDestroy();
    	//android.os.Debug.stopMethodTracing();
    	stopService(new Intent(TaginURN.INTENT_STOP_SERVICE));
    	if(mReceiver != null){
			unregisterReceiver(mReceiver);
			mReceiver = null;
		}
    }

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
	}
}
