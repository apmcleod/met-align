package metalign.beat;

/**
 * A <code>Beat</code> represents a single MIDI beat. It stores information about the onset
 * time and tick number of the beat, and the beat number (in 32nd notes). Beats are Comparable
 * and their natural ordering uses only {@link #beat}, not any absolute timing information.
 * 
 * @author Andrew McLeod - 3 March, 2014
 */
public class Beat implements Comparable<Beat> {
	/**
	 * The beat number on which this Beat lies in its bar. 0-indexed.
	 */
	private final int beat;
	
	/**
	 * The measure number on which this Beat lies.
	 */
	private final int bar;
	
	/**
	 * The sub beat on which this Beat lies in its beat. 0-indexed.
	 */
	private final int subBeat;
	
	/**
	 * The tatum on which this Beat lies in its sub beats. 0-indexed.
	 */
	private final int tatum;
	
	/**
	 * The time in microseconds at which this Beat lies.
	 */
	private final long time;
	
	/**
	 * The tick at which this Beat lies.
	 */
	private final long tick;
	
	/**
	 * Create a new default Beat, at time, tick, and measure 0 and beat 0.
	 */
	public Beat() {
		this(0, 0, 0, 0, 0, 0);
	}
	
	/**
	 * Standard constructor for all fields.
	 * 
	 * @param bar {@link #bar}
	 * @param beat {@link #beat}
	 * @param tatum {@link #tatum}
	 * @param time {@link #time}
	 * @param tick {@link #tick}
	 */
	public Beat(int bar, int beat, int subBeat, int tatum, long time, long tick) {
		this.bar = bar;
		this.beat = beat;
		this.subBeat = subBeat;
		this.tatum = tatum;
		this.time = time;
		this.tick = tick;
	}
	
	/**
	 * Return whether this beat is a downbeat or not.
	 * 
	 * @return True if this Beat is a downbeat.
	 */
	public boolean isDownbeat() {
		return beat == 0 && subBeat == 0 && tatum == 0;
	}
	
	/**
	 * Return whether this beat is a beat or not.
	 * 
	 * @return True if this Beat is a beat.
	 */
	public boolean isBeat() {
		return subBeat == 0 && tatum == 0;
	}
	
	/**
	 * Return whether this beat is a sub beat or not.
	 * 
	 * @return True if this Beat is a sub beat.
	 */
	public boolean isSubBeat() {
		return tatum == 0;
	}
	
	/**
	 * Get this Beat's bar number.
	 * 
	 * @return {@link #bar}
	 */
	public int getBar() {
		return bar;
	}
	
	/**
	 * Get this Beat's beat number.
	 * 
	 * @return {@link #beat}
	 */
	public int getBeat() {
		return beat;
	}
	
	/**
	 * Get this Beat's sub beat number.
	 * 
	 * @return {@link #subBeat}
	 */
	public int getSubBeat() {
		return subBeat;
	}
	
	/**
	 * Get this Beat's tatum number.
	 * 
	 * @return {@link #tatum}
	 */
	public int getTatum() {
		return tatum;
	}
	
	/**
	 * Get this Beat's time.
	 * 
	 * @return {@link #time}
	 */
	public long getTime() {
		return time;
	}
	
	/**
	 * Get this Beat's tick.
	 * 
	 * @return {@link #tick}
	 */
	public long getTick() {
		return tick;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("(");
		
		sb.append(bar).append('.');
		sb.append(beat).append('.');
		sb.append(subBeat).append('.');
		sb.append(tatum).append(',');
		sb.append(time).append(')');
		
		return sb.toString();
	}
	
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof Beat)) {
			return false;
		}
		
		Beat beat = (Beat) other;
		
		return beat.tatum == this.tatum && beat.subBeat == this.subBeat && beat.beat == this.beat && beat.bar == bar && beat.time == time;
	}
	
	@Override
	public int hashCode() {
		return Integer.valueOf(tatum * 5000 + beat * 500 + subBeat * 50 + bar).hashCode();
	}

	@Override
	public int compareTo(Beat o) {
		if (o == null) {
			return -1;
		}
		
		int value = Integer.compare(bar, o.bar);
		if (value != 0) {
			return value;
		}
		
		value = Integer.compare(beat, o.beat);
		if (value != 0) {
			return value;
		}
		
		value = Integer.compare(subBeat, o.subBeat);
		if (value != 0) {
			return value;
		}
		
		return Integer.compare(tatum, o.tatum);
	}
}
