package ca.idi.taginsdk;

/**
 * Komodo Lab: Tagin! Project: 3D Tag Cloud
 * Google Summer of Code 2011
 * @authors Jorge Silva and Primal Pappachan
 */

import java.util.Date;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.widget.Toast;

public class Helper {
	/**
	 * Main debug switch, turns on/off debugging for the whole app
	 */
	public static final boolean DEBUG = false;
	/**
	 * Tag used for logging in the whole app
	 */
	public static final String TAG = "tagin!";
	
	public static final int NULL_RSSI = -999;
	
	private static Helper helper = null;
	

	/**
	 * Create a new instance of this class or return one it it already exists
	 * @return Helper
	 */
	public static Helper getInstance() {
		if(helper == null) {
			return new Helper();
		}
		return helper;
	}
	
	public static void showToast(Context context, String msg) {
		Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
	}

	public int getMaxRSSIEver(Context context) {
		// We need an Editor object to make preference changes.
        // All objects are from android.context.Context
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getInt("MAX_RSSI_EVER", NULL_RSSI);
	}

	public void saveMaxRSSIEver(Context context, int maxRSSIEver) {
		// We need an Editor object to make preference changes.
        // All objects are from android.context.Context
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("MAX_RSSI_EVER", maxRSSIEver);
        // Commit the edit!
        editor.commit();
	}
	
	/**
	 * @return time in Hours: Minutes: Seconds as string
	 */
	public String getTime(){
		Date date = new Date();
		String time = date.getHours() + ":" + date.getMinutes() + ":" + date.getSeconds();
		return time;
	}
	
	public int getMinRSSIEver(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getInt("MIN_RSSI_EVER", NULL_RSSI);
	}
	
	public void saveMinRSSIEver(Context context, int minRSSIEver) {
		// We need an Editor object to make preference changes.
        // All objects are from android.context.Context
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("MIN_RSSI_EVER", minRSSIEver);
        // Commit the edit!
        editor.commit();
	}
	
	public String getDeviceId(Context context){
		return Secure.getString(context.getContentResolver(),
                Secure.ANDROID_ID); 
	}
	
	
}
