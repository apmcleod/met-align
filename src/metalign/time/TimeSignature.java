package metalign.time;

import metalign.hierarchy.Measure;

/**
 * A <code>TimeSignature</code> represents some MIDI data's beat structure (time signature).
 * Equality is based only on the numerator and denominator.
 * 
 * @author Andrew McLeod - 11 Feb, 2015
 */
public class TimeSignature {
	
	/**
	 * The numerator used to signify an irregular meter. It can be used with any denominator (4, for example).
	 */
	public static final int IRREGULAR_NUMERATOR = Integer.MAX_VALUE / 32;

	/**
	 * The numerator of the time signature.
	 */
	private final int numerator;
	
	/**
	 * The denominator of the time signature.
	 */
	private final int denominator;
	
	/**
	 * Create a new default TimeSignature (4/4 time)
	 */
	public TimeSignature() {
		this(new byte[] {4, 2, 24, 8});
	}
	
	/**
	 * Create a new TimeSignature from the given data array.
	 * 
	 * @param data Data array, parsed directly from midi.
	 */
	public TimeSignature(byte[] data) {
		numerator = data[0];
		denominator = (int) Math.pow(2, data[1]);
	}
	
	/**
	 * Create a new TimeSignature with the given numerator and denominator.
	 * 
	 * @param numerator {@link #numerator}
	 * @param denominator {@link #denominator}
	 */
	public TimeSignature(int numerator, int denominator) {
		this.numerator = numerator;
		this.denominator = denominator;
	}
	
	/**
	 * Get the number of 32nd notes per measure at this time signature.
	 * 
	 * @return The number of 32nd notes per measure.
	 */
	public int getNotes32PerBar() {
		return (8 * numerator * 4 / denominator);
	}
	
	/**
	 * Get the numerator of this time signature.
	 * 
	 * @return {@link #numerator}
	 */
	public int getNumerator() {
		return numerator;
	}
	
	/**
	 * Get the denominator of this time signature.
	 * 
	 * @return {@link #denominator}
	 */
	public int getDenominator() {
		return denominator;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(4);
		sb.append(numerator);
		sb.append('/');
		sb.append(denominator);
		return sb.toString();
	}
	
	@Override
	public int hashCode() {
		return getNumerator() + getDenominator();
	}
	
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof TimeSignature)) {
			return false;
		}
		
		TimeSignature ts = (TimeSignature) other;
		
		return getDenominator() == ts.getDenominator() && getNumerator() == ts.getNumerator();
	}

	/**
	 * Get the BeatHierarchy of this time signature.
	 * 
	 * @return The BeatHierarchy of this time signature.
	 */
	public Measure getMeasure() {
		int beatsPerMeasure = numerator;
		int subBeatsPerBeat = 2;
		
		// Check for compound
		if (numerator > 3 && numerator % 3 == 0) {
			beatsPerMeasure = numerator / 3;
			subBeatsPerBeat = 3;	
		}
		
		Measure measure = new Measure(beatsPerMeasure, subBeatsPerBeat);
		
		return measure;
	}
}
