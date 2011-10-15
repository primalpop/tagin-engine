package ca.idi.taginsdk;

/**
 * Komodo Lab: Tagin! Project: 3D Tag Cloud
 * Google Summer of Code 2011
 * @authors Jorge Silva and Primal Pappachan
 */

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

public class TaginProvider extends ContentProvider {
	
	private static final String TAG ="ca.idi.tagin.TaginProvider";

	private static final String SCHEME = "content://";
	private static final String AUTHORITY = "ca.idi.tagin.taginprovider";
	private static final String PATH_RAW = "/raw/";
	private static final String PATH_URN = "/urn/";
	
	public static final String DEFAULT_SORT_ORDER = "modified DESC";
	
	private static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.idi.tagin.";
	private static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.idi.tagin.";
	
	public static final Uri CONTENT_URI = Uri.parse(SCHEME + AUTHORITY + "/");
	/**
	 * Content URI for a single radio. Append an id to this Uri to retrieve a radio.
	 */
	public static final Uri RAW_RADIO_URI = Uri.parse(SCHEME + AUTHORITY + PATH_RAW + "radio");
	public static final Uri RAW_FINGERPRINT_URI = Uri.parse(SCHEME + AUTHORITY + PATH_RAW + "fingerprint");
	public static final Uri RAW_FINGERPRINTS_URI = Uri.parse(SCHEME + AUTHORITY + PATH_RAW + "fingerprints");
	public static final Uri RAW_BEACON_URI = Uri.parse(SCHEME + AUTHORITY + PATH_RAW + "beacon");
	public static final Uri RAW_BEACONS_URI = Uri.parse(SCHEME + AUTHORITY + PATH_RAW + "beacons");
	public static final Uri RAW_FINGERPRINT_DETAIL_URI = Uri.parse(SCHEME + AUTHORITY 
																			+ PATH_RAW + "fingerprint/detail");
	public static final Uri RAW_FINGERPRINTS_DETAIL_URI = Uri.parse(SCHEME + AUTHORITY 
																			+ PATH_RAW + "fingerprints/detail");
	
	public static final Uri URN_FINGERPRINT_URI = Uri.parse(SCHEME + AUTHORITY + PATH_URN + "fingerprint");
	public static final Uri URN_FINGERPRINTS_URI = Uri.parse(SCHEME + AUTHORITY + PATH_URN + "fingerprints");
	public static final Uri URN_BEACON_URI = Uri.parse(SCHEME + AUTHORITY + PATH_URN + "beacon");
	public static final Uri URN_BEACONS_URI = Uri.parse(SCHEME + AUTHORITY + PATH_URN + "beacons");
	public static final Uri URN_FINGERPRINT_DETAIL_URI = Uri.parse(SCHEME + AUTHORITY 
			+ PATH_URN + "fingerprint/detail");
	public static final Uri URN_FINGERPRINTS_DETAIL_URI = Uri.parse(SCHEME + AUTHORITY 
			+ PATH_URN + "fingerprints/detail");

	
	private static final int RADIO_RAW = 1;
	private static final int FINGERPRINT_RAW = 2;
	private static final int FINGERPRINTS_RAW = 3;
	private static final int BEACON_RAW = 4;
	private static final int BEACONS_RAW = 5;
	private static final int FINGERPRINT_DETAIL_RAW = 6;
	private static final int FINGERPRINTS_DETAIL_RAW = 7;
	private static final int FINGERPRINT_URN = 8;
	private static final int FINGERPRINTS_URN = 9;
	private static final int BEACON_URN = 10;
	private static final int BEACONS_URN = 11;
	private static final int FINGERPRINT_DETAIL_URN = 12;
	private static final int FINGERPRINTS_DETAIL_URN = 13;
	
	/**
	 * Standard Projections for Interesting Columns
	 */
	public static final String[] RADIO_PROJECTION = new String[] {
        TaginDatabase._ID, 
        TaginDatabase.BSSID, 
	};
	
	public static final String[] FINGERPRINT_ID_PROJECTION = new String[]{
		TaginDatabase.FINGERPRINT_ID
	};
	
	public static final String[] FINGERPRINT_DETAIL_PROJECTION = new String[]{
		TaginDatabase.RANK,
		TaginDatabase.BEACON_ID
	};
	
	public static final String[] NEIGHBOUR_PROJECTION = new String[]{
		TaginDatabase.RANK,
		TaginDatabase.FINGERPRINT_ID
	};
	
	public static final String[] BEACON_PROJECTION = new String[]{
		TaginDatabase._ID,
		TaginDatabase.TYPE,
		TaginDatabase.BSSID
	};
	
	public static final String[] URN_PROJECTION = new String[]{
		TaginDatabase.MODIFIED,
		TaginDatabase.URN
	};
	
	private SQLiteDatabase DB;
	private AsyncTask<SQLiteOpenHelper, Void, SQLiteDatabase> DBFetcher; //TODO Implement in a seperate thread
	private TaginDatabase mDBHelper;
	
	public TaginProvider(){
		
	}
	
	private static final UriMatcher uriMatcher;
	static{
		uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		uriMatcher.addURI(AUTHORITY, "raw/radio", RADIO_RAW);
		uriMatcher.addURI(AUTHORITY, "raw/fingerprints", FINGERPRINTS_RAW);
		uriMatcher.addURI(AUTHORITY, "raw/fingerprints/#", FINGERPRINT_RAW);
		uriMatcher.addURI(AUTHORITY, "raw/beacons", BEACONS_RAW);
		uriMatcher.addURI(AUTHORITY, "raw/beacons/#", BEACON_RAW);
		uriMatcher.addURI(AUTHORITY, "raw/fingerprints/detail", FINGERPRINTS_DETAIL_RAW);
		uriMatcher.addURI(AUTHORITY, "raw/fingerprints/detail/#", FINGERPRINT_DETAIL_RAW);
		uriMatcher.addURI(AUTHORITY, "urn/beacons", BEACONS_URN);
		uriMatcher.addURI(AUTHORITY, "urn/beacons/#", BEACON_URN);
		uriMatcher.addURI(AUTHORITY, "urn/fingerprints", FINGERPRINTS_URN);
		uriMatcher.addURI(AUTHORITY, "urn/fingerprints/#", FINGERPRINT_URN);
		uriMatcher.addURI(AUTHORITY, "urn/fingerprints/detail", FINGERPRINTS_DETAIL_URN);
		uriMatcher.addURI(AUTHORITY, "urn/fingerprints/detail/#", FINGERPRINT_DETAIL_URN);
	}
	
	@Override
	public boolean onCreate() {
		//TODO Invoking on a background thread
		Context context = getContext();
		mDBHelper = new TaginDatabase(context); 
		DB = mDBHelper.getWritableDatabase();
		return (DB == null) ? false : true;
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		int nrows = 0;
		switch(uriMatcher.match(uri)){
			case FINGERPRINTS_DETAIL_URN:	
				nrows = DB.delete("urn_fingerprint_details", selection, selectionArgs);
				//Log.i(Helper.TAG, "Deleting the Fingerprint Details from URN Contents Table = "+ nrows);
				return nrows;
			case FINGERPRINT_RAW:
				return nrows;
			default:
				IllegalArgumentException e = new IllegalArgumentException("Unsupported URI: " + uri);
				Log.e(TAG, "Unsupported content type requested.");
				throw e;
		}
	}

	@Override
	public String getType(Uri uri) {
		switch(uriMatcher.match(uri)){
		case RADIO_RAW:
			Log.i(TAG, "Returning content type of radios");
			return CONTENT_ITEM_TYPE + "radios";
		case BEACON_URN:	
		case BEACON_RAW:
			Log.i(TAG, "Returning content type of beacons");
			return CONTENT_ITEM_TYPE + "beacon";
		case FINGERPRINTS_URN:	
		case FINGERPRINTS_RAW:
			Log.i(TAG, "Returning content type of fingerprints");
			return CONTENT_TYPE + "fingerprints";
		case FINGERPRINT_URN:	
		case FINGERPRINT_RAW:
			Log.i(TAG, "Returning content type of fingerprint");
			return CONTENT_ITEM_TYPE + "fingerprint";
		case FINGERPRINT_DETAIL_URN:
		case FINGERPRINT_DETAIL_RAW:
			Log.i(TAG, "Returning content type of fingerprint details");
			return CONTENT_ITEM_TYPE + "fingerprint_details";
		case FINGERPRINTS_DETAIL_RAW:
		case FINGERPRINTS_DETAIL_URN:
			Log.i(TAG, "Returning content type of fingerprints details");
			return CONTENT_TYPE + "fingerprints_details";
		default:
			IllegalArgumentException e = new IllegalArgumentException("Unsupported URI: " + uri);
            Log.e(TAG, "Unsupported content type requested.");
            throw e;
		}
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		String DATABASE_TABLE;
		switch(uriMatcher.match(uri)){
		case RADIO_RAW:
			DATABASE_TABLE="raw_radios";
			break;
		case FINGERPRINTS_RAW:
			DATABASE_TABLE="raw_fingerprints";
			break;
		case BEACONS_RAW:
			DATABASE_TABLE="raw_beacons";
			break;
		case FINGERPRINTS_DETAIL_RAW:
			DATABASE_TABLE="raw_fingerprint_details";
			break;
		case FINGERPRINTS_URN:
			DATABASE_TABLE="urn_fingerprints";
			break;
		case FINGERPRINTS_DETAIL_URN:
			DATABASE_TABLE="urn_fingerprint_details";
			break;
		case BEACONS_URN:
			DATABASE_TABLE="urn_beacons";
			break;
		default:
			return null;
		}
		
		long rowID = DB.insert(DATABASE_TABLE, "", values);
		
		if(rowID > 0){
			Uri _uri;
			switch(uriMatcher.match(uri)){
			case RADIO_RAW:
				_uri = ContentUris.withAppendedId(RAW_RADIO_URI, rowID);
				getContext().getContentResolver().notifyChange(_uri, null);
				//Log.i(TAG, "Inserted row " + values+ "into " + uri + ".");
				return _uri;
			case FINGERPRINTS_RAW:
				_uri = ContentUris.withAppendedId(RAW_FINGERPRINTS_URI, rowID);
				getContext().getContentResolver().notifyChange(_uri, null);
				//Log.i(TAG, "Inserted row " + values + "into " + uri + ".");
				return _uri;
			case BEACONS_RAW:
				_uri = ContentUris.withAppendedId(RAW_BEACONS_URI, rowID);
				getContext().getContentResolver().notifyChange(_uri, null);
				//Log.i(TAG, "Inserted row " + values+ "into " + uri + ".");
				return _uri;
			case FINGERPRINTS_DETAIL_RAW:
				_uri = ContentUris.withAppendedId(RAW_FINGERPRINTS_DETAIL_URI, rowID);
				getContext().getContentResolver().notifyChange(_uri, null);
				//Log.i(TAG, "Inserted row " + values+ "into " + uri + ".");
				return _uri;
			case FINGERPRINTS_URN:
				_uri = ContentUris.withAppendedId(URN_FINGERPRINTS_URI, rowID);
				getContext().getContentResolver().notifyChange(_uri, null);
				//Log.i(TAG, "Inserted row " + values+ "into " + uri + ".");
				return _uri;
			case BEACONS_URN:
				_uri = ContentUris.withAppendedId(URN_BEACONS_URI, rowID);
				getContext().getContentResolver().notifyChange(_uri, null);
				//Log.i(TAG, "Inserted row " + values+ "into " + uri + ".");
				return _uri;
			case FINGERPRINTS_DETAIL_URN:
				_uri = ContentUris.withAppendedId(URN_FINGERPRINTS_DETAIL_URI, rowID);
				getContext().getContentResolver().notifyChange(_uri, null);
				//Log.i(TAG, "Inserted row " + values+ "into " + uri + ".");
				return _uri;
			default:
				throw new IllegalArgumentException("Unknown URI " + uri);
			}
		}
		SQLException e = new SQLException("Failed to insert row into " + uri);
        throw e;
	}



	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
			String sortOrder) {
		String DATABASE_TABLE;
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		
		switch(uriMatcher.match(uri)){
		case RADIO_RAW:
			DATABASE_TABLE = "raw_radios";
			break;
		case FINGERPRINTS_RAW:
			DATABASE_TABLE = "raw_fingerprints";
			break;
		case FINGERPRINTS_DETAIL_RAW:
			DATABASE_TABLE ="raw_fingerprint_details";
			break;
		case BEACONS_RAW:
			DATABASE_TABLE = "raw_beacons";
			break;
		case FINGERPRINTS_URN:
			DATABASE_TABLE = "urn_fingerprints";
			break;
		case BEACONS_URN:
			DATABASE_TABLE = "urn_beacons";
			break;
		case FINGERPRINTS_DETAIL_URN:
			DATABASE_TABLE = "urn_fingerprint_details";
			break;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
		qb.setTables(DATABASE_TABLE);
		/*String query = qb.buildQuery(projection, 
                selection, 
                selectionArgs, 
                null, 
                null, 
                sortOrder, null);
		Log.i(Helper.TAG, "Query " + "=" + query); */
		 Cursor c = qb.query(
                 DB, 
                 projection, 
                 selection, 
                 selectionArgs, 
                 null, 
                 null, 
                 sortOrder);
		 c.setNotificationUri(getContext().getContentResolver(), uri);
	     return c;
	}

	@Override
	public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
		int count = 0;
		switch(uriMatcher.match(uri)){
			case RADIO_RAW:
				count = DB.update("raw_radios", values, where, whereArgs);
				break;
			case FINGERPRINTS_DETAIL_URN:
				count = DB.update("urn_fingerprint_details", values, where, whereArgs);
				break;
			case FINGERPRINTS_URN:
				count = DB.update("urn_fingerprints", values, where, whereArgs);
				break;
			default:
				throw new IllegalArgumentException("Unknown URI" + uri);
		}	
		return count;	
	}
}
