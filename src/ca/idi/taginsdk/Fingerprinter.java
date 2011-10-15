package ca.idi.taginsdk;

/**
 * Komodo Lab: Tagin! Project: 3D Tag Cloud
 * Google Summer of Code 2011
 * @authors Jorge Silva and Primal Pappachan
 */

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

public class Fingerprinter extends Service implements Runnable {

	//Constants
    public static final String INTENT_START_SERVICE = "ca.idi.taginsdk.Fingerprinter";
    public static final String INTENT_STOP_SERVICE = "ca.idi.taginsdk.Fingerprinter";
    public static final String ACTION_FINGERPRINT_CHANGED = "ca.idi.taginsdk.action.FINGERPRINT_CHANGED";
    public static final String EXTRA_SCAN_INTERVAL = "ca.idi.taginsdk.extra.SCAN_INTERVAL";
	public static final String EXTRA_SCANS_PER_FINGERPRINT = "ca.idi.taginsdk.extra.SCANS_PER_FINGERPRINT";
	public static final int DEFAULT_SCAN_INTERVAL = 5000; //milliseconds
	public static final int DEFAULT_SCANS_PER_FINGERPRINT = 1;
	
	private static Fingerprint mFP;
	private WifiManager mWifiManager;
	private Handler mHandler;
	private Boolean mFingerprintRequested;
	private int mScanCount;
	private int mScanInterval, mScansPerFingerprint;
	private Intent mFPChangedIntent;
	private Helper mHelper;
    
    @Override
	public void onCreate() {
        //Intents & Intent Filters
    	mWifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
    	mFPChangedIntent = new Intent(ACTION_FINGERPRINT_CHANGED);
    	mHandler = new Handler();
    	mHelper = Helper.getInstance();
    	mFP = new Fingerprint();

    	registerReceiver(mReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) );
	}
	
    // platform.  On 2.0 or later we override onStartCommand() so this
    // method will not be called.
    /*
    * The system calls this method when another component, such as an activity, 
    * requests that the service be started, by calling startService(). 
    */
    @Override
    public void onStart(Intent intent, int startId) {
    	if (intent.hasExtra(EXTRA_SCAN_INTERVAL)) {
			mScanInterval = intent.getExtras().getInt(EXTRA_SCAN_INTERVAL); 
		} else {
			mScanInterval = DEFAULT_SCAN_INTERVAL;
		}
		if (intent.hasExtra(EXTRA_SCANS_PER_FINGERPRINT)) {
			mScansPerFingerprint  = intent.getExtras().getInt(EXTRA_SCANS_PER_FINGERPRINT);
		} else {
			mScansPerFingerprint = DEFAULT_SCANS_PER_FINGERPRINT;
		}
    	registerReceiver(mReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) );
		if (mWifiManager.isWifiEnabled()) {
			Log.d(Helper.TAG, "Starting fingerprinting service...");
	    	startFingerprintScan();
	    	startServiceThread();
        } else {
        	Helper.showToast(this, "Please enable WiFi");
        }
    }

	@Override
	public void onDestroy() {
		super.onDestroy();
		if(mHandler!= null){
			mHandler.removeCallbacks(mFPScanRunnable);
		}
		if(mReceiver != null){
			unregisterReceiver(mReceiver);
			mReceiver = null;
		}
		/* Call this from the main Activity to shutdown the connection */
		
	}
	
    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();
	
    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
    	Fingerprinter getService() {
            return Fingerprinter.this;
        }
    }

    @Override
	public IBinder onBind(Intent arg0) {
		return mBinder;
	}

	@Override
	public void run() {
		Looper.prepare();
	}
	
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		//TODO Find a better way to pass in mMaxRSSIEver. Intent?
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
				if (mFingerprintRequested) {
	    			mScanCount++;
	    			if (mScanCount == 1) {
		    			mFP.setBeaconsFromScanResult(mWifiManager.getScanResults(), mHelper.getMaxRSSIEver(Fingerprinter.this));
	    			} else {
		    			mFP.addBeaconsFromScanResult(mWifiManager.getScanResults(), mScanCount, mHelper.getMaxRSSIEver(Fingerprinter.this));
	    			}
	    			// Update max RSSI ever
	    			updateMaxRSSIEver(mFP.getMaxRSSI());
					if (mScanCount <  mScansPerFingerprint) {// Continue if not done
						/* Executes the code recursively after definite time intervals
	    				*  by sending a delayed message.
	    				*/	 				
		    			mHandler.postDelayed(mFPScanRunnable, mScanInterval); 
	    			} else {
	        			// Finish fingerprint scanning
	    				mFingerprintRequested = false;
	        			// Announce a new fingerprint is available
	    			}
					sendBroadcast(mFPChangedIntent);
				}
			}
		}
    };
    
	private Runnable mFPScanRunnable = new Runnable () {

		@Override
		public void run() {
			Log.d(Helper.TAG, "Starting a fingerprint scan...");
			mScanCount = 0;
			mFingerprintRequested = true;
			startWifiScan();
		}
	};
	
	private void startFingerprintScan() {	
		mHandler.post(mFPScanRunnable); 
	}
	
	private void startWifiScan() {
		Log.d(Helper.TAG, "Starting a wifi scan...");
		mWifiManager.startScan();
	}

	private void updateMaxRSSIEver(int maxRSSI) {
		if (maxRSSI > mHelper.getMaxRSSIEver(this))
			mHelper.saveMaxRSSIEver(this, maxRSSI);
	}
	
	/**
	* Executes the run() thread.
	*/
	private void startServiceThread() {
		Thread thread = new Thread(this);
        	thread.start();
	}
	
	//TODO: Find a non-static way to do this. Maybe include with intent?
	public static Fingerprint getFingerprint() {
		return mFP;
	}

}
