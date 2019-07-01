package metalign.harmony;

/**
 * A <code>Chord</code> object represents a single chord at some time. Chords are naturally ordered by
 * increasing onset time, then increasing offset time, then increasing tonic, then quality (arbitrary
 * but consistent).
 * 
 * @author Andrew McLeod
 */
public class Chord implements Comparable<Chord> {
	/**
	 * The different possible qualities (major, minor, diminished, etc.) for a chord.
	 * 
	 * @author Andrew McLeod
	 */
	public enum ChordQuality {
		NO_CHORD,
		MAJOR,
		MINOR,
		AUGMENTED,
		DIMINISHED,
		AUGMENTED_6,
		DOMINANT_7,
		MAJOR_7,
		MINOR_7,
		DIMINISHED_7,
		HALF_DIMINISHED_7
	}
	
	/**
	 * The onset time of this chord, in microseconds.
	 */
	private final long onsetTime;
	
	/**
	 * The offset time of this chord, in microseconds.
	 */
	private final long offsetTime;
	
	/**
	 * The tonic (base note) of this chord.
	 */
	private final int tonic;
	
	/**
	 * The quality of the chord (major, minor, diminished, etc.)
	 */
	private final ChordQuality quality;
	
	/**
	 * Create a new Chord with the given fields.
	 * 
	 * @param onset {@link #onsetTime}
	 * @param offset {@link #offsetTime}
	 * @param tonic {@link #tonic}
	 * @param quality {@link #quality}
	 */
	public Chord(long onset, long offset, int tonic, ChordQuality quality) {
		onsetTime = onset;
		offsetTime = offset;
		this.tonic = tonic;
		this.quality = quality;
	}
	
	@Override
	public int compareTo(Chord o) {
		if (o == null) {
			return -1;
		}
		
		int value = Long.compare(onsetTime, o.onsetTime);
		if (value != 0) {
			return value;
		}
		
		value = Long.compare(offsetTime, o.offsetTime);
		if (value != 0) {
			return value;
		}
		
		value = Integer.compare(tonic, o.tonic);
		if (value != 0) {
			return value;
		}
		
		return quality.compareTo(o.quality);
	}
}
