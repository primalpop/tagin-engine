package ca.idi.taginsdk;

/**
 * Komodo Lab: Tagin! Project: 3D Tag Cloud
 * Google Summer of Code 2011
 * @authors Jorge Silva and Primal Pappachan
 */

public class Neighbour implements Comparable<Neighbour>{
	
	public Long id;
	public Double rankDistance;
	
	public Neighbour(){
		id = null;
		rankDistance = null;
	}
	
	public Neighbour(Long id, Double rankDistance){
		this.id = id;
		this.rankDistance = rankDistance;
	}

	@Override
	public int compareTo(Neighbour another) {
		if(another.rankDistance > rankDistance )
			return -1;
		else
			return 1;
	}
}
