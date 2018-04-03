package metalign.hierarchy;

import java.io.Serializable;

/**
 * A <code>Measure</code> represents a metrical measure, containing information about the number of
 * beats per measure, as well as the number of sub beats per beat.
 * 
 * @author Andrew McLeod - 8 March, 2016
 */
public class Measure implements Comparable<Measure>, Serializable {
	/**
	 * Version 1
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * The number of beats this measure is divided into.
	 */
	private final int beatsPerMeasure;
	
	/**
	 * The number of sub beats each beat of this measure
	 * is divided into.
	 */
	private final int subBeatsPerBeat;
	
	private final int anacrusis;
	
	private final int length;
	
	/**
	 * Create a new Measure with the given fields.
	 * 
	 * @param beatsPerMeasure {@link #beatsPerMeasure}
	 * @param subBeatsPerBeat {@link #subBeatsPerBeat}
	 */
	public Measure(int beatsPerMeasure, int subBeatsPerBeat) {
		this(beatsPerMeasure, subBeatsPerBeat, 0, 0);
	}
	
	public Measure(int beatsPerMeasure, int subBeatsPerBeat, int length, int anacrusis) {
		this.beatsPerMeasure = beatsPerMeasure;
		this.subBeatsPerBeat = subBeatsPerBeat;
		this.length = length;
		this.anacrusis = anacrusis;
	}
	
	/**
	 * Get the number of beats this measure is divided into.
	 * 
	 * @return {@link #beatsPerMeasure}
	 */
	public int getBeatsPerMeasure() {
		return beatsPerMeasure;
	}
	
	/**
	 * Get the number of sub beats each beat of this measure
	 * is divided into.
	 * 
	 * @return {@link #subBeatsPerBeat}
	 */
	public int getSubBeatsPerBeat() {
		return subBeatsPerBeat;
	}
	
	public int getLength() {
		return length;
	}
	
	public int getAnacrusis() {
		return anacrusis;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("M_");
		
		sb.append(beatsPerMeasure).append(',').append(subBeatsPerBeat);
		
		return sb.toString();
	}
	
	@Override
	public int hashCode() {
		return Integer.valueOf(beatsPerMeasure * 4 + subBeatsPerBeat).hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Measure)) {
			return false;
		}
		
		Measure measure = (Measure) o;
		
		return beatsPerMeasure == measure.beatsPerMeasure && subBeatsPerBeat == measure.subBeatsPerBeat;
	}

	@Override
	public int compareTo(Measure o) {
		if (o == null) {
			return 1;
		}
		
		int result = Integer.compare(beatsPerMeasure, o.beatsPerMeasure);
		if (result != 0) {
			return result;
		}
		
		result = Integer.compare(subBeatsPerBeat, o.subBeatsPerBeat);
		return result;
	}
}
