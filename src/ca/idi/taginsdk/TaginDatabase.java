package ca.idi.taginsdk;

/**
 * Komodo Lab: Tagin! Project: 3D Tag Cloud
 * Google Summer of Code 2011
 * @authors Jorge Silva and Primal Pappachan
 */

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class TaginDatabase extends SQLiteOpenHelper {
	
private static final String TAG = "ca.idi.tagin.TaginDbAdapter";
	
	private static final int DATABASE_VERSION = 4; //increment to update Database
	
	private static final String DATABASE_NAME = "taginsdk.db";
	
	public static final String _ID = "_id"; //Identifier Column of tables
	
	public static final String SYNC_UP = "sync_up";
	
	//Raw Content Table Columns
	//Fingerprints
	public static final String CREATED = "created";
	public static final String WLAN_RADIO_ID = "wlan_radio_id";
	public static final String BT_RADIO_ID = "BT_RADIO_ID";
	
	//Radios
	public static final String RSSI_MAX_LIMIT = "rssi_max_limit";
	public static final String RSSI_MIN_LIMIT = "rssi_min_limit";
	
	//Beacons
	public static final String TYPE = "type";
	public static final String BSSID = "bssid";
	
	//Fingerprint Details
	public static final String FINGERPRINT_ID = "fingerprint_id";
	public static final String BEACON_ID = "beacon_id";
	public static final String RSSI = "rssi";
	
	//URN Table Columns
	public static final String MODIFIED = "modified";
	public static final String URN = "URN";
	public static final String RANK = "rank";
	
	//Required Constants
	public static final int WLAN = 1;
	public static final int BLUETOOTH = 2;
	
	public static final int RAW_TABLES = 1; 
	public static final int URN_TABLES = 2;
	
    
	private static final String CREATE_TABLE_RAW_R =
        "create table raw_radios (_id integer primary key autoincrement, "
                + "bssid text not null, type integer not null, " +
                		"rssi_max_limit integer not null, rssi_min_limit integer not null);";
    
	private static final String CREATE_TABLE_RAW_FP =
		"create table raw_fingerprints (_id integer primary key autoincrement,"
			+ " created text not null, wlan_radio_id integer not null," +
					" bt_radio_id integer);";

	private static final String CREATE_TABLE_RAW_B =
		"create table raw_beacons (_id integer primary key autoincrement," +
		" bssid text not null, type integer not null);";

	private static final String CREATE_TABLE_RAW_FP_D =
		"create table raw_fingerprint_details (_id integer primary key autoincrement," +
		" fingerprint_id integer not null, beacon_id integer not null," +
							" rssi integer not null);";
	
	private static final String CREATE_TABLE_URN_FP =
		"create table urn_fingerprints (_id integer primary key autoincrement,"
			+ " modified text not null, URN text not null);";

	private static final String CREATE_TABLE_URN_B =
		"create table urn_beacons (_id integer primary key autoincrement," +
		" bssid text not null, type integer not null);";

	private static final String CREATE_TABLE_URN_FP_D =
		"create table urn_fingerprint_details (_id integer primary key autoincrement," +
		" fingerprint_id integer not null, beacon_id integer not null," +
							" rank real not null);";
	
	//TODO SYNC TABLE
	
	TaginDatabase(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
			db.execSQL(CREATE_TABLE_RAW_R);
			db.execSQL(CREATE_TABLE_RAW_B);
			db.execSQL(CREATE_TABLE_RAW_FP);
			db.execSQL(CREATE_TABLE_RAW_FP_D);
			db.execSQL(CREATE_TABLE_URN_B);
			db.execSQL(CREATE_TABLE_URN_FP);
			db.execSQL(CREATE_TABLE_URN_FP_D);
			
			//TODO Creation of Temp Tables
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.w(TAG, "Upgrading database from version " + 
                oldVersion + " to " + newVersion + ", which will destroy all old data.");
		db.execSQL("DROP TABLE IF EXISTS raw_radios");
		db.execSQL("DROP TABLE IF EXISTS raw_beacons");
		db.execSQL("DROP TABLE IF EXISTS raw_fingerprints");
		db.execSQL("DROP TABLE IF EXISTS raw_fingerprint_details");
		db.execSQL("DROP TABLE IF EXISTS urn_beacons");
		db.execSQL("DROP TABLE IF EXISTS urn_fingerprints");
		db.execSQL("DROP TABLE IF EXISTS urn_fingerprint_details");
		onCreate(db);
		
	}


}
