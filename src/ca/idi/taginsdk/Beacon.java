package ca.idi.taginsdk;

/**
 * Komodo Lab: Tagin! Project: 3D Tag Cloud
 * Google Summer of Code 2011
 * @authors Jorge Silva and Primal Pappachan
 */

/*This class can be used to model the JSON object */

public class Beacon implements Comparable<Beacon> {
	
	private String BSSID; //MAC ID
	private Integer RSSI; //Recieved Signal Strength
	private Double Rank; //Rank value in the range [0, 1]

	// Constructors
	public Beacon() {
		BSSID = null;
		RSSI = null;
		Rank = null;
	}

	public Beacon(String bssid, Integer rssi, int mMaxRSSIEver) {
		BSSID = bssid;
		RSSI = rssi;
		Rank = calculateRank(rssi, mMaxRSSIEver); //TODO Check 
	}
	
	public Beacon(String bssid, Integer rssi, Double rank) {
		BSSID = bssid;
		RSSI = rssi;
		Rank = rank;
	}

	public void setBSSID(String bssid) { BSSID = bssid; }
	public void setRSSI(Integer rssi) { RSSI = rssi; }
	public void setRank(Double rank) {Rank = rank;}
	public Double getRank() { return Rank;}
	public String getBSSID() { return BSSID; }
	public Integer getRSSI() { return RSSI; }

	@Override
	public int compareTo(Beacon another) {
		/*Natural Comparison method of the class. The ordering given by this method is considered 
		the natural ordering of the objects of the class.
		*/
		return another.RSSI - RSSI;
	}
	
	/**
	 * Calculates the Rank of the beacon by normalizing w.r.t Max Power
	 * @param rssi - RSSI of beacon
	 * @param mMaxRSSIEver - Max Power
	 * @return Rank value
	 */
	public Double calculateRank(int rssi, int mMaxRSSIEver){
		Double maxPowerEver = dBm2Power(mMaxRSSIEver);
		Double mRank = Math.pow(dBm2Power(rssi) / maxPowerEver, 0.25);
		return mRank;
	}
	
	private double dBm2Power(int rssi) {
		return Math.pow(10.0, Double.valueOf(rssi - 30) / 10.0);
	}

}

