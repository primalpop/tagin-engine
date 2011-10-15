package ca.idi.taginsdk;

/**
 * Komodo Lab: Tagin! Project: 3D Tag Cloud
 * Google Summer of Code 2011
 * @authors Jorge Silva and Primal Pappachan
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.net.wifi.ScanResult;


//TODO: extend JSONObject
public class Fingerprint {
	/**
	 * Individual beacons that this fingerprint contains
	**/
	private Beacon[] mBeacons;
	private String mTime;
	private Helper mHelper = Helper.getInstance();
	
	// Constructors
	public Fingerprint() {
		mBeacons = null;
		mTime = mHelper.getTime();
	}

	public Fingerprint(Beacon[] beacons) {
		int length = beacons.length;
		mBeacons = new Beacon[length];
		for ( int i = 0; i < length; i++ ) {
			mBeacons[i] = new Beacon(beacons[i].getBSSID(),beacons[i].getRSSI(), beacons[i].getRank());
		}
		mTime = mHelper.getTime();
	}
	
	public void setBeacons(Beacon[] beacons) {
		int length = beacons.length;
		mBeacons = new Beacon[length];
		for ( int i = 0; i < length; i++ ) {
			mBeacons[i] = new Beacon(beacons[i].getBSSID(),beacons[i].getRSSI(), beacons[i].getRank());
		}
		mTime = mHelper.getTime();
	}
	
	public void setBeaconsFromScanResult(List<ScanResult> scanResults, int mMaxRSSIEver) {
		mBeacons = new Beacon[scanResults.size()];
		for ( int i = 0; i < scanResults.size(); i++ ) {
			ScanResult sr = scanResults.get(i);
			mBeacons[i] = new Beacon(sr.BSSID, sr.level, mMaxRSSIEver);
		}
		mTime = mHelper.getTime();
	}
	
	/**
	 * Instead of substituting the fingerprint's beacons, add a list from another scan.
	 * This is useful when performing multiple scans in a single fingerprint to increase
	 * its accuracy.
	 * @param scanResults: Results from a WiFi scan
	 * @param n: Iteration per fingerprint that this scan represents (for calculating an average)
	**/
	public void addBeaconsFromScanResult(List<ScanResult> scanResults, int n, int mMaxRSSIEver) {
		ArrayList<Beacon> allBeacons = new ArrayList<Beacon>();
		Integer thisLength = mBeacons.length;
		Integer otherLength = scanResults.size();
		Boolean dupFound; int i, j;
		// Initialize boolean masks for identifying identical beacons
		Boolean[] otherUsed = new Boolean[otherLength];
		for ( i = 0; i < otherLength; i++ ) {
			otherUsed[i] = false;
		}
		// 
		for ( i=0; i < thisLength; i++ ) {
			j = 0; dupFound = false;
			while (!dupFound && (j < otherLength)) {
				if (!otherUsed[j]) {
					if (mBeacons[i].getBSSID().equals(scanResults.get(j).BSSID)) {
						// The same beacon was found in the other fingerprint.
						// Calculate the average
						allBeacons.add(new Beacon(mBeacons[i].getBSSID(),movingRSSIAvg(mBeacons[i].getRSSI(),scanResults.get(j).level,n), mMaxRSSIEver));
						otherUsed[j] = true;
						dupFound = true;
					}
				}
				j++;
			}
			if (!dupFound) {
				allBeacons.add(new Beacon(mBeacons[i].getBSSID(),movingRSSIAvg(Helper.NULL_RSSI,mBeacons[i].getRSSI(),n), mMaxRSSIEver));
			}
		}
		// Add non-duplicate scan results
		for ( i = 0; i < otherLength; i++ ) {
			if (!otherUsed[i]) {
				allBeacons.add(new Beacon(scanResults.get(i).BSSID,movingRSSIAvg(Helper.NULL_RSSI,scanResults.get(i).level,n), mMaxRSSIEver));
			}
		}
		mBeacons = allBeacons.toArray(new Beacon[allBeacons.size()]);
		mTime = mHelper.getTime();
	}
	
	private double dBm2Power(int rssi) {
		return Math.pow(10.0, Double.valueOf(rssi - 30) / 10.0);
	}
	
	private int power2dBm(double power) {
		return (int) Math.round(10 * Math.log10(power)) + 30;
	}
	
	private int movingRSSIAvg (int avgRSSI, int newRSSI, int n) {
		double startPower = avgRSSI == Helper.NULL_RSSI? 0:dBm2Power(avgRSSI);
		double newPower = newRSSI == Helper.NULL_RSSI? 0:dBm2Power(newRSSI);
		double avgPower = ((startPower * Double.valueOf(n-1)) + newPower) / Double.valueOf(n); 
		//Until (n-1)th scan the value was startPower and for nth scan the value is newPower.
		return power2dBm(avgPower);
	}
	
	/**
	 * Returns a rank in the range (0, 1.0] based on the beacon's RSSI.
	 * The highest RSSI values approach a rank of 1.0 while the weakest ones
	 * approach 0.0
	**/
	public Double[] getRanks() {
		Double[] ranks = new Double[mBeacons.length];
		int i = 0;
		for (Beacon beacon : mBeacons) {
			ranks[i] = beacon.getRank();
			i++;
		}
		return ranks;
	}
	
	public double rankDistanceTo(Fingerprint another, int mMaxRSSIEver) {
		/*
		 * Calculates the relative distance between two fingerprints.
		 * The relative is between 0 and 1 and is max when two fingerprints
		 * doesnt share a beacon and min where there are lot of shared 
		 * beacons.
		 */
		//Log.d(Helper.TAG, "Calculating rank distance...");
		// Used to save fingerprint distance by calculating the euclidean distance between two beacons having same
		// BSSID in two fingerprints. 
		Double d = 0.0;
		// Used to save the maximum possible fingerprint distance (for distance normalization) by considering
		// the case when two fingerprints doesn't share any beacon.
		Double maxD = 0.0;
		
		Boolean dupFound; int i, j;
		Beacon[] thisBeacons = mBeacons;
		Beacon[] otherBeacons = another.getBeacons();
		Double[] thisRanks = getRanks(); //Getting the ranks of beacons 
		Double[] otherRanks = another.getRanks();
		Integer thisLength = mBeacons.length;
		Integer otherLength = otherBeacons.length;
		
		// Initialize boolean masks for identifying identical beacons
		// Can be used to check latter on which beacons had no identical beacon
		Boolean[] otherUsed = new Boolean[otherLength];
		for ( i = 0; i < otherLength; i++ ) {
			otherUsed[i] = false;
		}
		// Measure differences from this fingerprint
		for ( i=0; i < thisLength; i++ ) {
			// Maximum contribution from this fingerprint's i-th beacon
			maxD += Math.pow(thisRanks[i], 2.0);
			j = 0; 
			dupFound = false; //To check if dup was found for this beacon
			while (!dupFound && (j < otherLength)) {
				if (!otherUsed[j]) {
					if (thisBeacons[i].getBSSID().equals(otherBeacons[j].getBSSID())) {
						// The same beacon was found in the other fingerprint.
						// The distance between both must be measured
						d += Math.pow(thisRanks[i] - otherRanks[j], 2.0);
						otherUsed[j] = true; //The identical beacon found.
						dupFound = true;  
					}
				}
				j++;
			}
			if (!dupFound) {
				/*
				 * The dup was not found and the maximum contribution is added from this beacon.
				 */
				d += Math.pow(thisRanks[i], 2.0);
			}
		}
		// Measure remaining differences from the other fingerprint
		for ( i = 0; i < otherLength; i++ ) {
			// Maximum contribution from other fingerprint's i-th beacon
			maxD += Math.pow(otherRanks[i], 2.0);
			if (!otherUsed[i]) {
				d += Math.pow(otherRanks[i], 2.0);
			}
		}
		return Math.sqrt(d) / Math.sqrt(maxD);
	}
	
	
	public int getMaxRSSI() {
		// Make sure limit RSSI values are still valid
		Arrays.sort(mBeacons);
		return mBeacons[0].getRSSI();
	}
	
	/**
	 * Displaces the fingerprint by the change vector.
	 * @param changeVector - An Array of Beacons
	 */
	public void applyDisplacement(Beacon [] changeVector){
		Beacon[] thisBeacons = mBeacons;
		Integer thisLength = mBeacons.length;
		Integer vectorLength = changeVector.length;
		int i, j;
		Boolean [] vectorUsed = new Boolean[vectorLength];
		for(i=0; i<vectorLength; i++){
			vectorUsed[i] = false;
		}
		for(i=0; i<thisLength; i++){
			j = 0; 
			while (j < vectorLength) {
				if (!vectorUsed[j]) {
					if (thisBeacons[i].getBSSID().equals(changeVector[j].getBSSID())) {
						// The same beacon was found in the change vector
						thisBeacons[i].setRank(thisBeacons[i].getRank() + changeVector[j].getRank());
						vectorUsed[j] = true; //The identical beacon found. 
					}
				}
				j++;
			}
		}
		// Converting to an ArrayList to add the new beacons
		ArrayList<Beacon> BeaconList = new ArrayList<Beacon>(Arrays.asList(thisBeacons)); 
		// Add remaining beacons from the Change Vector
		for ( i = 0; i < vectorLength; i++ )
			if (!vectorUsed[i])
				BeaconList.add(changeVector[i]);
		this.setBeacons(BeaconList.toArray(new Beacon[BeaconList.size()]));
	}
	
	/**
	 * Merges two fingerprints
	 * @return
	 */

	public void merge(Fingerprint fp){
		Beacon[] thisBeacons = mBeacons;
		Beacon[] FPBeacons = fp.getBeacons();
		Integer thisLength = mBeacons.length;
		Integer FPLength = FPBeacons.length;
		int i, j;
		Boolean dupFound; //To check if dup was found for this beacon
		// Initialize boolean masks for identifying identical beacons
		// Can be used to check latter on which beacons in this fingerprint 
		//had no identical beacon in other Fingerprint. 
		Boolean[] FPUsed = new Boolean[FPLength];
		for ( i = 0; i < FPLength; i++ ) {
			FPUsed[i] = false;
		}
		for ( i=0; i < thisLength; i++ ) {
			j = 0; 
			dupFound = false; 
			while (!dupFound && (j < FPLength)) {
				if (!FPUsed[j]) {
					if (thisBeacons[i].getBSSID().equals(FPBeacons[j].getBSSID())) {
						//Log.v(Helper.TAG, "Averaging Ranks");
						// The same beacon was found in the other fingerprint. Ranks must be averaged.
						thisBeacons[i].setRank((thisBeacons[i].getRank() +  FPBeacons[j].getRank())/2);
						FPUsed[j] = true; //The identical beacon found.
						dupFound = true;  
					}
				}
				j++;
			}
			if (!dupFound) {  
				/*
				 * The dup was not found and the rank value of this beacon in this fingerprint is halved.
				 */
				//Log.v(Helper.TAG, "Halving Ranks for" + thisBeacons[i].getBSSID());
				thisBeacons[i].setRank(thisBeacons[i].getRank()/2);
			}
		}
		// Add remaining beacons from the other fingerprint
		ArrayList<Beacon> BeaconList = new ArrayList<Beacon>(Arrays.asList(thisBeacons));
		//Log.v(Helper.TAG, "Merge Size Check Before Adding : " + Integer.toString(BeaconList.size()));
		Beacon beacon = new Beacon();
		for ( i = 0; i < FPLength; i++ ) {
			// Adding the new fingerprint's i-th beacon to the URN
			if (!FPUsed[i]) {
				//Log.v(Helper.TAG, "Adding new Ranks");
				beacon.setBSSID(FPBeacons[i].getBSSID());
				beacon.setRank(FPBeacons[i].getRank()/2); //Averaging the Rank of the new Beacon
				BeaconList.add(beacon);
				//Log.v(Helper.TAG, "Adding new Ranks: " +  beacon.getBSSID());
			}
		}
		//Log.v(Helper.TAG, "Merge Size Check After Adding : " + Integer.toString(BeaconList.size()));
		this.setBeacons(BeaconList.toArray(new Beacon[BeaconList.size()]));
	}

	public Beacon[] getBeacons() { return mBeacons; }
	
	public String getTime() { return mTime; }

}
