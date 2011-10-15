package ca.idi.taginsdk.tools;

/**
 * Komodo Lab: Tagin! Project: 3D Tag Cloud
 * Google Summer of Code 2011
 * @authors Jorge Silva and Primal Pappachan
 */

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;


import ca.idi.taginsdk.R;
import ca.idi.taginsdk.Beacon;
import ca.idi.taginsdk.Fingerprint;
import ca.idi.taginsdk.Fingerprinter;
import ca.idi.taginsdk.Helper;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;


public class Logger extends Activity {
	private TextView mMonitor1, mMonitor2, mMonitor3, mMonitor4, mMonitor5;
	private ToggleButton mToggleButton;
	private Button mStopButton;
	private ImageView mLoading;
	private Fingerprint mSavedFP; //Used to backup fp for calculating rank 
	private Helper mHelper;
	private Handler handler;
	private AnimationDrawable loadAnimation;

	private static BufferedWriter out;
	private final String LogHeader = "FINGERPRINT_APS, RANK_DISTANCE_TO_PREVIOUS, In-place/Moving";
	
	//Strings for storing previous and current fingerprint details
	private String cTime, pAP, cAP, pTime = "-Infinity";
	
	//private StringBuffer LogBuffer = new StringBuffer(); //Buffer used to backup the log data.
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
		if (Helper.DEBUG) android.os.Debug.waitForDebugger();
    	
		//android.os.Debug.startMethodTracing("tagin");
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
    	setContentView(R.layout.dialog);
    	
        
    	mHelper = Helper.getInstance();
    	
    	//LogBuffer.append("Log View" + "\n" + LogHeader + "\n");
    	
    	//Textviews to monitor logging process
    	mMonitor1 = (TextView) findViewById(R.id.Dialog_TextView01);
    	mMonitor2 = (TextView) findViewById(R.id.Dialog_TextView02);
    	mMonitor3 = (TextView) findViewById(R.id.Dialog_TextView03);
    	mMonitor4 = (TextView) findViewById(R.id.Dialog_TextView04);
    	mMonitor5 = (TextView) findViewById(R.id.Dialog_TextView05);

    	mLoading =(ImageView) findViewById(R.id.Dialog_ImageView01);
    	mLoading.setImageResource(R.drawable.loading); //Sets the image for the Imageview
    	loadAnimation = (AnimationDrawable)mLoading.getDrawable(); //

    	startFingerprint(1);
    	
    	//Registers a broadcast for event of Fingerprint change
		registerReceiver(mReceiver, new IntentFilter(Fingerprinter.ACTION_FINGERPRINT_CHANGED));
		
		try {
			createFileOnDevice(false); //Append is set to false at the beginning.
			//Log.d(Helper.TAG, "CreateFileonDevice");
		} catch (IOException e) {
			Log.e(Helper.TAG, "Exception in createFileOnDevice");
			e.printStackTrace();
		}
		
		mStopButton = (ToggleButton) findViewById(R.id.Dialog_toggleButton01);
		mToggleButton = (ToggleButton) findViewById(R.id.Dialog_toggleButton02);
		
		handler = new Handler();

		mStopButton.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
							startFingerprint(-1);
							fileFlush();
							finish();
					}
		});
		
		mToggleButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mToggleButton.isChecked()){
					Helper.showToast(Logger.this, "Click again to toggle to Moving");
				}
				else{	
					Helper.showToast(Logger.this, "Click again to toggle to IN PLACE");
				}
			}
		});
	}
  
    @Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		startLoading(); 
	}
    
	/*
	 * Function to initially create the log file and it also writes the time of creation to file.
	 */
    private void createFileOnDevice(Boolean append) throws IOException {
    	File Root, LogFile;
    	FileWriter LogWriter;
    	Root = Environment.getExternalStorageDirectory();
    	if(Root.canWrite()){
    		 LogFile = new File(Root, "taginLog.txt");
    	     LogWriter = new FileWriter(LogFile, append);
    	     out = new BufferedWriter(LogWriter);
    	     out.write("Logged at" + mHelper.getTime() + "\n");
    	     out.write(LogHeader + "\n");
    	     Log.d(Helper.TAG, LogHeader);
    	}
	}

	private void startFingerprint(int status) {
		if(status == 1){ //Start the Logging Process
			Helper.showToast(this, "Started Logging");
			Intent intent = new Intent(Fingerprinter.INTENT_START_SERVICE);
			intent.putExtra(Fingerprinter.EXTRA_SCANS_PER_FINGERPRINT, 5);
			startService(intent);
			//TODO: Add number of WiFi scans as intent extra
		}
		else if (status == -1){ //Stop the Logging process
			stopService(new Intent(Fingerprinter.INTENT_STOP_SERVICE));
			Helper.showToast(this, "To view the complete log, check taginLog file in sdcard.");
		}
	}
        

	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			String LogItem = " "; //String buffer to be written to the log file
			if (action.equals(Fingerprinter.ACTION_FINGERPRINT_CHANGED)) {
				Helper.showToast(Logger.this, "Logging");
				Fingerprint lastFP = new Fingerprint(Fingerprinter.getFingerprint().getBeacons());
				Log.d(Helper.TAG, "Fingerprint: " + printFP(lastFP));
				cTime = lastFP.getTime(); //Getting the time current Fingerprint was taken
				cAP = Integer.toString(lastFP.getBeacons().length);
				if (mSavedFP != null){	
					pTime = mSavedFP.getTime();
					LogItem =  cAP + "," + measureRankDistance(mSavedFP,lastFP) +
									"," + mToggleButton.isChecked() +"\n" ;
					pAP = Integer.toString(mSavedFP.getBeacons().length);
					Log.d(Helper.TAG, LogItem);
					writeToFile(LogItem); //Writing pairs of Rank Distance and Inside/Outside to log file.
					mMonitor5.setText("Distance: " + Double.toString(measureRankDistance(mSavedFP,lastFP)));
				}	
				mMonitor1.setText("Taken at: " +  cTime  );
				mMonitor2.setText("Number of Beacons: " + cAP);
				mMonitor3.setText("Taken at: " + pTime );
				mMonitor4.setText("Number of Beacons:  " + pAP);
				mSavedFP = lastFP; //Backs up the current fingerprint
			}
		}
	};
	
	private String printFP (Fingerprint fp) {
		Beacon[] beacons = fp.getBeacons();
		Double[] ranks = fp.getRanks();
		String msg = ""; int i = 0;
        for (i = 0; i < beacons.length ;i++) {
        	msg += beacons[i].getBSSID() + ", " + beacons[i].getRSSI().toString() + ", " + ranks[i].toString();
        }
		return msg;
	}
	
	private double measureRankDistance(Fingerprint fp1, Fingerprint fp2) {
		return fp1.rankDistanceTo(fp2, mHelper.getMaxRSSIEver(this));
	}
	
	/*
	 * Function to write the message to the log file.
	 */
	private void writeToFile(String message){
		try {
			out.write(message+"\n");
		} catch (IOException e) {
			Log.e(Helper.TAG, "Exception in writeToFile");
			e.printStackTrace();
		}
	}
	
	@Override
    public void onDestroy () {
    	super.onDestroy();
    	//android.os.Debug.stopMethodTracing();
    	if(mReceiver != null){
			unregisterReceiver(mReceiver);
			mReceiver = null;
		}
    	fileFlush();
    	stopService(new Intent(Fingerprinter.INTENT_START_SERVICE));
    }
	
	private void fileFlush(){
		try {
    		out.flush(); //The BufferedWriter Object is flushed and closed here.
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
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
	
	private void startLoading(){
		handler.post(mLoader); //Posts the runnable to the message queue thread
	}
	
	private Runnable mLoader = new Runnable () { //Runs the loading animation in a new thread
		@Override
		public void run() {
			Log.d(Helper.TAG, "Loading");
			loadAnimation.start(); //Starts the animation
		}
	};


}
