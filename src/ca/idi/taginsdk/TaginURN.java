package ca.idi.taginsdk;

/**
 * Komodo Lab: Tagin! Project: 3D Tag Cloud
 * Google Summer of Code 2011
 * @authors Jorge Silva and Primal Pappachan
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/**
 * Tagin Engine which generates/manages Uniform Resource Names(URNs).
 */

public class TaginURN extends Service implements Runnable {

	private static String mURN; //Uniform Resource Name for the location
	Neighbour [] neighbours;
	private static Boolean check; //To check whether neighbours for a fingerprint have been computed already.
	private static double THRESHOLD;
	
	private Helper mHelper;
	private ContentResolver cr;
	private Intent mURNReadyIntent;
	private static Fingerprint mFingerprint;
	private Handler mHandler;	
	
	public static final String ACTION_URN_READY = "ca.idi.taginsdk.action.URN_READY";
    public static final String INTENT_START_SERVICE = "ca.idi.taginsdk.TaginURN";
    public static final String INTENT_STOP_SERVICE = "ca.idi.taginsdk.TaginURN";
	
    public static final String EXTRA_RUN_INTERVAL = "ca.idi.taginsdk.extra.RUN_INTERVAL";
	public static final String EXTRA_NUMBER_OF_RUNS = "ca.idi.taginsdk.NUMBER_OF_RUNS";
    public static final int DEFAULT_NUMBER_OF_RUNS = 9999; //Default number of runs
	public static final int DEFAULT_RUN_INTERVAL =9000; //Default interval between runs
    
	
	private int mRunInterval, mNumberOfRuns, mRunCount;
    
	@Override
	public void onCreate() {
		super.onCreate();
		registerReceiver(mReceiver, new IntentFilter(Fingerprinter.ACTION_FINGERPRINT_CHANGED)); 
		mHelper = Helper.getInstance();
		mFingerprint = new Fingerprint();
		mURNReadyIntent = new Intent(ACTION_URN_READY);
		cr = getContentResolver();
		mURN =  new String();
		mHandler = new Handler();
		check = false; //Initializing it to false
		THRESHOLD = 0.25; //TODO Pass it by intents?
		mRunCount = 0;
	}
	
	
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		/**
		 * Checking if extras are attached with the intent passed.
		 * If not, use the default values
		 */
		if (intent.hasExtra(EXTRA_RUN_INTERVAL)) {
			mRunInterval = intent.getExtras().getInt(EXTRA_RUN_INTERVAL); 
		} else {
			mRunInterval = DEFAULT_RUN_INTERVAL;
		}
		if (intent.hasExtra(EXTRA_NUMBER_OF_RUNS)) {
			mNumberOfRuns  = intent.getExtras().getInt(EXTRA_NUMBER_OF_RUNS);
		} else {
			mNumberOfRuns = DEFAULT_NUMBER_OF_RUNS;
		}
		registerReceiver(mReceiver, new IntentFilter(Fingerprinter.ACTION_FINGERPRINT_CHANGED));
		startURNRun();
		startServiceThread();
	}

	
	private void startFingerprint() {
		//Starts the Fingerprinter Service
		startService(new Intent(Fingerprinter.INTENT_START_SERVICE));
	}
	
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(Fingerprinter.ACTION_FINGERPRINT_CHANGED)) {
				mRunCount++;
				Fingerprint lastFP = new Fingerprint(Fingerprinter.getFingerprint().getBeacons());	
				addFPToDatabase(lastFP); 
				mFingerprint = lastFP; //Backs up for URN Generation
				startURN(); 
				if (mRunCount <  mNumberOfRuns) {
					/* Executes the code recursively after definite time intervals
    				*  by sending a delayed message.
    				*/	 				
					mHandler.postDelayed(mURNRunnable, mRunInterval); 
    			}
			}
		}	
	};
	
	/**
	 * Returns a newly generated URN or modified existing URN.
	 */
	public void startURN(){
		long URNId = 0;
		//checkContents(); //Uncomment to see the table contents on the Log View.
		neighbours = getNeighbours(mFingerprint);
		URNId = getClosestNeighbour();
		if(URNId == 0){
			generateURN();
		}
		else{
			mergeFingerprint(mFingerprint, URNId);
			mURN = fetchURNfromDB(URNId);
		}
		sendBroadcast(mURNReadyIntent);
	}

	/**
	 * Merges a new fingerprint with a fingerprint already existing in the database.
	 * @param fp - New Fingerprint
	 * @param urnId - Identifier for fingerprint in the database.
	 */
	private void mergeFingerprint(Fingerprint fp, long urnId ){
		Fingerprint newFingerprint = GetFingerprint(urnId);
		//Log.d(Helper.TAG, "Before Merging - 1:- " + printFP(newFingerprint));
		//Log.d(Helper.TAG, "Before Merging - 2:- " + printFP(fp));
		newFingerprint.merge(fp);
		//Log.d(Helper.TAG, "After merging:- " + printFP(newFingerprint));
		Beacon[] changeVector = calculateChangeVector(urnId, newFingerprint);
		updateURN(urnId, newFingerprint);
		pushFingerprint(urnId, changeVector); //Pushing Away the overLapping Neighbours		
	}

	/**
	 * Updates the Fingerprint in the URN Contents table
	 * @param urnId - Identifier for the fingerprint in the database
	 * @param fp - New Fingerprint
	 */
	private void updateURN(long urnId, Fingerprint fp) {
		//Deleting the fingerprint details from URN Content Table
		String where = TaginDatabase.FINGERPRINT_ID + "=" + urnId;
		cr.delete(TaginProvider.URN_FINGERPRINTS_DETAIL_URI, where, null);
		Long beacon_id;
		Beacon[] beacons = fp.getBeacons();
		//Updating with fingerprint details of merged Fingerprint
		for (int i = 0; i < beacons.length ;i++) {
			beacon_id = addBeacon(beacons[i].getBSSID(), TaginDatabase.WLAN, TaginDatabase.URN_TABLES);
			addFPDetails(urnId, beacon_id, beacons[i].getRank(), TaginDatabase.URN_TABLES);
		}
		change_modified_time(urnId); //Changes the modified time of URN.
	}

	/**
	 * Calculates the change in rank values of the updated fingerprint and old fingerprint
	 * @param urnId - Identifier for the Fingerprint(Old)
	 * @param fp - Fingerprint(Updated)
	 * @return - The changevector of ranks
	 */
	private Beacon[] calculateChangeVector(Long urnId, Fingerprint fp) {
		Fingerprint oldFp = GetFingerprint(urnId);
		Beacon[] oldBeacons = oldFp.getBeacons();
		Beacon[] newBeacons = fp.getBeacons();
		ArrayList<Beacon> cBeacons = new ArrayList<Beacon>();
		int oldLength = oldBeacons.length;
		int newLength = newBeacons.length;
		int i, j;
		Boolean dupFound;
		for(i = 0; i < newLength; i++){
			j = 0; 
			dupFound = false; //To check if dup was found for this beacon in new fingerprint
			Beacon vector = new Beacon();
			while (!dupFound && (j < oldLength)) {
				if (newBeacons[i].getBSSID().equals(oldBeacons[j].getBSSID())) {
					// The beacon was present in both fingerprints and change is calculated.
					vector.setBSSID(newBeacons[i].getBSSID());
					vector.setRank(newBeacons[i].getRank() - oldBeacons[j].getRank());
					cBeacons.add(vector);
					dupFound = true;  
				}
				j++;
			}
			if (!dupFound) {
				cBeacons.add(newBeacons[i]);
			}
		}
		return cBeacons.toArray(new Beacon[cBeacons.size()]);
	}
		
	/**
	 * Changes the modified time of an URN
	 * @param urnId - Identifier for the fingerprint in the database
	 */
	private void change_modified_time(Long urnId) {
		//Change the modified time to current time.
		ContentValues updateValues = new ContentValues();
		updateValues.put(TaginDatabase.MODIFIED, mHelper.getTime());
		cr.update(TaginProvider.URN_FINGERPRINTS_URI, updateValues, TaginDatabase._ID + "=" + urnId , null);
	}
	
	/**
	 * Pushes away the Over Lapping Neighbours of a fingerprint.
	 * @param urnId - Identifier for the fingerprint in the database
	 * @param changeVector - The vector denoting change in ranks of beacons
	 */
	private void pushFingerprint(long urnId, Beacon[] changeVector){
		Neighbour[] overLappingNeighbours = getOverlappingNeighbours(urnId);
		if(overLappingNeighbours.length == 0) return;
		for (Neighbour neighbour : overLappingNeighbours){
			Fingerprint neighbourFp = GetFingerprint(neighbour.id);
			neighbourFp.applyDisplacement(changeVector);
			updateURN(neighbour.id, neighbourFp);
		}
		for (Neighbour neighbour : overLappingNeighbours) //TODO Is iteration necessary?
			pushFingerprint(neighbour.id, changeVector);	
	}
	
	/**
	 * Builds a fingerprint object from an URN Identifier Reference
	 * @param URNId - Identifier for the fingerprint in the database
	 * @return Fingerprint 
	 */
	private Fingerprint GetFingerprint(Long URNId){
		String where1, where2;
		Cursor c1, c2 = null;
		Long beaconId;
		where1 = TaginDatabase.FINGERPRINT_ID + "=" + URNId;
		c1 = cr.query(TaginProvider.URN_FINGERPRINTS_DETAIL_URI, 
													TaginProvider.FINGERPRINT_DETAIL_PROJECTION, where1, null, null);
		
		Fingerprint fp = new Fingerprint();
		ArrayList<Beacon> uBeacons = new ArrayList<Beacon>();
		if(c1.moveToFirst()){
			do{
				Beacon mBeacon = new Beacon();
				mBeacon.setRank(c1.getDouble(c1.getColumnIndexOrThrow(TaginDatabase.RANK)));	
				beaconId = c1.getLong(c1.getColumnIndexOrThrow(TaginDatabase.BEACON_ID));
				where2 = TaginDatabase._ID + "=" + beaconId;
				c2 = cr.query(TaginProvider.URN_BEACONS_URI, TaginProvider.BEACON_PROJECTION, where2, null, null);
				if(c2.moveToFirst())
					mBeacon.setBSSID(c2.getString(c2.getColumnIndexOrThrow(TaginDatabase.BSSID)));
				uBeacons.add(mBeacon);
			}while(c1.moveToNext());
		}	
		if(c2 !=null) c2.close();
		if(c1 != null) c1.close();
		fp.setBeacons(uBeacons.toArray(new Beacon[uBeacons.size()]));
		//Log.d(Helper.TAG, "GetFingerprint - URNID: " + URNId +  ", "+ "Fingerprint: " + printFP(fp));
		return fp;
	}
	
	/**
	 * Computes the neighbours for Fingerprint which have at least one beacon in common.
	 * @param fp - Fingerprint
	 */
	private Neighbour [] getNeighbours(Fingerprint fp){
		ArrayList<Neighbour> NeighbourList = new ArrayList<Neighbour>();
		long urn_id;
		Double rankDistance;
		Cursor c = null;
		Cursor fpc = null;
		Beacon[] beacons = fp.getBeacons();
		String where = TaginDatabase.BSSID + "=" + "?";
		for(int i = 0; i < beacons.length; i++){
			c = cr.query(TaginProvider.URN_BEACONS_URI, TaginProvider.BEACON_PROJECTION, where, 
					new String[]{beacons[i].getBSSID()}, null);
			if(c.moveToFirst()){
				long rowId = c.getLong(c.getColumnIndexOrThrow(TaginDatabase._ID));
				fpc = cr.query(TaginProvider.URN_FINGERPRINTS_DETAIL_URI, TaginProvider.FINGERPRINT_ID_PROJECTION,
						TaginDatabase.BEACON_ID + "=" + rowId, null, null);
				if(fpc.moveToFirst()){
					do{				
 						urn_id =  fpc.getLong(fpc.getColumnIndexOrThrow(TaginDatabase.FINGERPRINT_ID));
 						if(NotAlreadyExistsURN(urn_id, NeighbourList)){
 							Fingerprint temp_fp = GetFingerprint(urn_id);
 							rankDistance = fp.rankDistanceTo(temp_fp, mHelper.getMaxRSSIEver(this));
							NeighbourList.add(new Neighbour(urn_id, rankDistance));
						}
					}while(fpc.moveToNext());	
				}
			}
			else
				continue;
		}
		if(c != null) c.close();
		if(fpc != null) fpc.close();
		check = true; //Setting flag to true to indicate Neighbours have been computed 
		return NeighbourList.toArray(new Neighbour[NeighbourList.size()]);
	}
	
	/**
	 * Calculates the overLapping Neighbours for the Fingerprint based on a threshold value
	 * @param urnId - Identifier for fingerprint in the Database
	 * @return List of Neighbour Objects
	 */
	private Neighbour[] getOverlappingNeighbours(Long urnId){
		int i;
		if(check) //TODO Remove this check mechanism, if possible.
			check = false;
		else{
			Fingerprint fp = GetFingerprint(urnId);
			getNeighbours(fp);
		}
		ArrayList<Neighbour> overLapNeighboursList = new ArrayList<Neighbour>();
		for(i=0; i<neighbours.length ; i++){
			//Log.v(Helper.TAG, "Rank Distance to Neighbour: " + neighbours[i].rankDistance);
			if(neighbours[i].rankDistance < THRESHOLD && neighbours[i].id != urnId){ 
				overLapNeighboursList.add(neighbours[i]);
			}
		}
		return overLapNeighboursList.toArray(new Neighbour[overLapNeighboursList.size()]);
	}
	
	/**
	 * Sorts the Neighbours list in descending order of rankDistance and returns the first element
	 * @return Identifier of the closest neighbour
	 */
	private long getClosestNeighbour(){
		Arrays.sort(neighbours);
		if(neighbours.length == 0) return 0;
		//Log.i(Helper.TAG, "Get Closest Neighbour: " + neighbours[0].id + "Rank Distance" + neighbours[0].rankDistance);
		return neighbours[0].rankDistance < THRESHOLD? neighbours[0].id : 0; 
	}
	
	/**
	 * Checks if the URN already exists in neighbours
	 * @param urnId - Identifier for the fingerprint in the database
	 * @return True if it doesn't exist, false otherwise.
	 */
	private boolean NotAlreadyExistsURN(long urnId, ArrayList<Neighbour> NeighbourList) {
		for(int i = 0; i < NeighbourList.size(); i++){
			if(NeighbourList.get(i).id == urnId)
				return false;
			else
				continue;
		}
		return true;
	}	
	
	/**
	 * Gets the Uniform Resource Name from the database
	 * @param urnId Identifier for the fingerprint in the database
	 * @return String 
	 */
	private String fetchURNfromDB(long urnId){
		String urn = "No URN Present in DB"; 
		String where = TaginDatabase._ID + "=" + urnId;
		Cursor c = cr.query(TaginProvider.URN_FINGERPRINTS_URI, TaginProvider.URN_PROJECTION, where, null, null );
		if(c.moveToFirst()){
			urn = c.getString(c.getColumnIndexOrThrow(TaginDatabase.URN));
		}
		c.close();
		return urn;
	}
	
	/**
	 * Generates a new URN 
	 */
	private void generateURN() {
		Fingerprint fp = mFingerprint;
		Beacon[] beacons = fp.getBeacons();
		Double[] ranks = fp.getRanks();
		long urn_id, beacon_id;
		urn_id = addURN();
		for (int i = 0; i < beacons.length ;i++) {
			beacon_id = addBeacon(beacons[i].getBSSID(), TaginDatabase.WLAN, TaginDatabase.URN_TABLES);
			addFPDetails(urn_id, beacon_id, ranks[i], TaginDatabase.URN_TABLES);
		}
		String where = TaginDatabase._ID + "=" + urn_id;
		Cursor c = cr.query(TaginProvider.URN_FINGERPRINTS_URI, TaginProvider.URN_PROJECTION, where, null, null );
		if(c.moveToFirst() && c.getCount() > 0){
			mURN = c.getString(c.getColumnIndexOrThrow(TaginDatabase.URN));
		}
		c.close();
	}
	
	/*
	 * Adds a new URN to the Database.
	 */
	private long addURN(){
		Uri uri;
		UUID URN = java.util.UUID.randomUUID(); //Generates a unique ID
		String uuid = URN.toString(); //Converts it to string
		ContentValues values = new ContentValues();
    	values.put(TaginDatabase.MODIFIED, mHelper.getTime());
    	values.put(TaginDatabase.URN, uuid);
    	uri = cr.insert(TaginProvider.URN_FINGERPRINTS_URI, values);
		return (uri == null) ? 0: Long.parseLong(uri.getPathSegments().get(2));	
	}
	
	/**
	 * Adds the Fingerprint Details to the RAW Contents Tables
	 * @param fp - Fingerprint
	 */
	protected void addFPToDatabase(Fingerprint fp) {
		long beacon_id = 0, fp_id = 0, radioId;
	    String created = fp.getTime();
		Beacon[] beacons = fp.getBeacons();
		radioId = addRadio(mHelper.getDeviceId(this));
	    fp_id = addFingerprint(created, radioId);
		for (int i = 0; i < beacons.length ;i++) {
			beacon_id =	addBeacon(beacons[i].getBSSID(), TaginDatabase.WLAN, TaginDatabase.RAW_TABLES);
			addFPDetails(fp_id, beacon_id, beacons[i].getRSSI(), TaginDatabase.RAW_TABLES);
		}
		updateRadio(radioId); //Updates the radio based on current fingerprint.
	}
	
	/**
	 * Adds the Device Details to RAW Contents Table if not already present
	 * @param deviceId - BSSID of the device
	 * @return Row Number
	 */
	private long addRadio(String deviceId){
		Uri uri;
		String where = TaginDatabase.BSSID + "=" + "?";
		long rowId;
		Cursor c = cr.query(TaginProvider.RAW_RADIO_URI, TaginProvider.RADIO_PROJECTION, where, new String[]{deviceId}, null );
		if(c.moveToFirst() && c.getCount() > 0){
			rowId = c.getLong(c.getColumnIndexOrThrow(TaginDatabase._ID));
		}
		else{
			ContentValues values = new ContentValues();
			values.put(TaginDatabase.BSSID, deviceId);
			values.put(TaginDatabase.RSSI_MAX_LIMIT, mHelper.getMaxRSSIEver(this));
			values.put(TaginDatabase.RSSI_MIN_LIMIT, mHelper.getMinRSSIEver(this));
			values.put(TaginDatabase.TYPE, TaginDatabase.WLAN);
			uri = cr.insert(TaginProvider.RAW_RADIO_URI, values);
			rowId = Long.parseLong(uri.getPathSegments().get(2));
		}
		c.close();
		return rowId;
	}
	
	/**
	 * Updates the Device details in the RAW Contents Table
	 * @param rowId - Identifier for the device
	 */
	
	private void updateRadio(long rowId) {
		String where = TaginDatabase._ID + "=" + rowId;
		ContentValues values = new ContentValues();
		values.put(TaginDatabase.RSSI_MAX_LIMIT, mHelper.getMaxRSSIEver(this));
		values.put(TaginDatabase.RSSI_MIN_LIMIT, mHelper.getMinRSSIEver(this));
		cr.update(TaginProvider.RAW_RADIO_URI, values, where, null);
	}
	
	/**
	 * Adds the Fingerprint Details to the Database
	 * @param fp_id - Identifier for the fingerprint
	 * @param beacon_id - Identifier for the beacon
	 * @param value - Either RSSI or Rank value.
	 * @param tables - 1 for RAW Tables, 2 for URN Tables
	 * @return
	 */
	private long addFPDetails(long fp_id, long beacon_id, double value, int tables){
		Uri uri = null;
		ContentValues initialValues = new ContentValues();
		initialValues.put(TaginDatabase.BEACON_ID, beacon_id);
		initialValues.put(TaginDatabase.FINGERPRINT_ID, fp_id);
		if(tables == TaginDatabase.RAW_TABLES){
			initialValues.put(TaginDatabase.RSSI, value);
			uri = cr.insert(TaginProvider.RAW_FINGERPRINTS_DETAIL_URI, initialValues);
		}
		else if(tables == TaginDatabase.URN_TABLES){
			initialValues.put(TaginDatabase.RANK, value);
			uri = cr.insert(TaginProvider.URN_FINGERPRINTS_DETAIL_URI, initialValues);
		}
		if(uri == null){
			Log.e(Helper.TAG, "Fingerprint Details Insertion Failed");
			return 0;
		}
		//Log.d(Helper.TAG, "Fingerprint Details: " +  uri.getPathSegments().toString());
		return Long.parseLong(uri.getPathSegments().get(3));
	}
	
	/**
	 * Adds the beacon to the Database.
	 * @param bssid - BSSID of the beacon
	 * @param type - Type of Beacon
	 * @param tables - 1 for RAW Tables, 2 for URN Tables
	 * @return Row Number
	 */
	private long addBeacon(String bssid, int type, int tables) {
		Uri uri = null;
		long rowId = 0;
		Cursor c;
		ContentValues values = new ContentValues();
		values.put(TaginDatabase.BSSID, bssid);
		values.put(TaginDatabase.TYPE, type);
		if(tables == TaginDatabase.RAW_TABLES)
			uri = cr.insert(TaginProvider.RAW_BEACONS_URI, values);
		else{ //In URN Contents, each beacon appears only at most once.
			String where = TaginDatabase.BSSID + "=" + "?";
			c = cr.query(TaginProvider.URN_BEACONS_URI, TaginProvider.BEACON_PROJECTION, where, new String[]{bssid}, null );
			if(c.moveToFirst() && c.getCount() > 0){
				rowId = c.getLong(c.getColumnIndexOrThrow(TaginDatabase._ID));
				//Log.i(Helper.TAG, "Duplicate Found for beacon in URN Contents" + Long.toString(rowId));
			}
			else{
				//Log.i(Helper.TAG, "Duplicate not found, Inserting");
				uri = cr.insert(TaginProvider.URN_BEACONS_URI, values);
			}
			c.close();
		}
	
		return (uri == null) ? rowId: Long.parseLong(uri.getPathSegments().get(2));
	}
	
	/**
	 * Adds the Fingerprint to the RAW Contents table
	 * @param created - Time when the fingerprint was generated
	 * @param radioId - Identifier for the device
	 * @return - Row Number
	 */
	private long addFingerprint(String created, long radioId) {
		Uri uri;
		ContentValues values = new ContentValues();
		values.put(TaginDatabase.CREATED, created);
		values.put(TaginDatabase.WLAN_RADIO_ID, radioId);
		values.put(TaginDatabase.BT_RADIO_ID, "");
		uri = cr.insert(TaginProvider.RAW_FINGERPRINTS_URI, values);
		if(uri == null){
			Log.e(Helper.TAG, "Fingerprint Insertion Failed");
			return 0;
		}
		//Log.d(Helper.TAG, uri.getPathSegments().toString());
		return Long.parseLong(uri.getPathSegments().get(2));
	}
	
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if(mHandler != null){
			mHandler.removeCallbacks(mURNRunnable);
		}
		stopService(new Intent(Fingerprinter.INTENT_STOP_SERVICE));
		if(mReceiver != null){
			unregisterReceiver(mReceiver);
			mReceiver = null;
		}
		/* Call this from the main Activity to shutdown the connection */
		
	}
	
	private Runnable mURNRunnable = new Runnable () {
		@Override
		public void run() {
			Log.d(Helper.TAG, "Starting an URN Run...");
			mRunCount = 0;
			startFingerprint();
		}
	};
	
	private void startURNRun() {	
		mHandler.post(mURNRunnable); 
	}
	
	/**
	* Executes the run() thread.
	*/
	private void startServiceThread() {
		Thread thread = new Thread(this);
        	thread.start();
	}
	
	public static String getURN(){
		/**
		 * Getter Function for URN
		 */
		return mURN;
	}

	@Override
	public void run() {
		Looper.prepare();
	}
	
	private void checkContents() {
		printURNTableContents();
		printBeaconTableContents();
		printDetailsTableContents();
	}

	private String printFP(Fingerprint fp) {
		Beacon[] beacons = fp.getBeacons();
		Double[] ranks = fp.getRanks();
		String msg = ""; int i = 0;
        for (i = 0; i < beacons.length ;i++) {
        	msg += beacons[i].getBSSID() + ", " + ranks[i].toString() + "\n";
        }
		return msg;
	}
	
	private void printDetailsTableContents(){
		Log.d(Helper.TAG, "Entering Print Table Contents");
		Cursor c = cr.query(TaginProvider.URN_FINGERPRINTS_DETAIL_URI, null, null, null, null);
		Long beacon_id, fingerprint_id, id;
		Double rank;
		if(c.moveToFirst()){
			do{
				beacon_id = c.getLong(c.getColumnIndexOrThrow(TaginDatabase.BEACON_ID));
				fingerprint_id = c.getLong(c.getColumnIndexOrThrow(TaginDatabase.FINGERPRINT_ID));
				id = c.getLong(c.getColumnIndexOrThrow(TaginDatabase._ID));
				rank = c.getDouble(c.getColumnIndexOrThrow(TaginDatabase.RANK));
				Log.i(Helper.TAG, "*Fingerprint Details* : " + id + "," + fingerprint_id +  "," + beacon_id  +  "," + rank);
			}while(c.moveToNext());	
		}
		c.close();
	}
	
	private void printURNTableContents(){
		Log.d(Helper.TAG, "Entering Print URN Table Contents");
		Cursor c = cr.query(TaginProvider.URN_FINGERPRINTS_URI, null, null, null, null);
		Long id;
		String modified, urn;
		if(c.moveToFirst()){
			do{
				modified = c.getString(c.getColumnIndexOrThrow(TaginDatabase.MODIFIED));
				urn = c.getString(c.getColumnIndexOrThrow(TaginDatabase.URN));
				id = c.getLong(c.getColumnIndexOrThrow(TaginDatabase._ID));
				Log.i(Helper.TAG, "*URN Details* : " + id + "," + modified +  "," + urn);
			}while(c.moveToNext());	
		}
		c.close();
	}
	
	private void printBeaconTableContents(){
		Log.d(Helper.TAG, "Entering Print Beacon Table Contents");
		Cursor c = cr.query(TaginProvider.URN_BEACONS_URI, null, null, null, null);
		Long id;
		String bssid;
		int type;
		if(c.moveToFirst()){
			do{
				type = c.getInt(c.getColumnIndexOrThrow(TaginDatabase.TYPE));
				bssid = c.getString(c.getColumnIndexOrThrow(TaginDatabase.BSSID));
				id = c.getLong(c.getColumnIndexOrThrow(TaginDatabase._ID));
				Log.i(Helper.TAG, "*Beacons* : " + id + "," + bssid +  "," + type);
			}while(c.moveToNext());	
		}
		c.close();
	}
}