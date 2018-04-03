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
	 * The beat number on which this Beat lies (measured in 32nd notes)
	 */
	private final int beat;
	
	/**
	 * The measure number on which this Beat lies.
	 */
	private final int measure;
	
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
		this(0, 0, 0, 0);
	}
	
	/**
	 * Standard constructor for all fields.
	 * 
	 * @param measure {@link #measure}
	 * @param beat {@link #beat}
	 * @param time {@link #time}
	 * @param tick {@link #tick}
	 */
	public Beat(int measure, int beat, long time, long tick) {
		this.measure = measure;
		this.beat = beat;
		this.time = time;
		this.tick = tick;
	}
	
	/**
	 * Get this Beat's measure number.
	 * 
	 * @return {@link #measure}
	 */
	public int getBar() {
		return measure;
	}
	
	/**
	 * Get this Beat's beat number.
	 * 
	 * @return {@link #beat}
	 */
	public int getTatum() {
		return beat;
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
		
		sb.append(measure).append('.');
		sb.append(beat).append(',');
		sb.append(time).append(')');
		
		return sb.toString();
	}
	
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof Beat)) {
			return false;
		}
		
		Beat beat = (Beat) other;
		
		return beat.beat == this.beat && beat.measure == measure && beat.time == time;
	}
	
	@Override
	public int hashCode() {
		return Integer.valueOf(getTatum() * 50 + getBar()).hashCode();
	}

	@Override
	public int compareTo(Beat o) {
		if (o == null) {
			return -1;
		}
		
		int value = Integer.compare(measure, o.measure);
		if (value != 0) {
			return value;
		}
		
		return Integer.compare(beat, o.beat);
	}
}
