package metalign.harmony;

import java.util.HashMap;
import java.util.Map;

/**
 * A <code>Chord</code> object represents a single chord at some time. Chords are naturally ordered by
 * increasing onset time, then increasing offset time, then increasing root, then quality (arbitrary
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
	 * The default vocabulary reduction for the chord qualities, down to the 4 triads.
	 */
	public static Map<ChordQuality, ChordQuality> DEFAULT_VOCAB_MAP = createDefaultVocabMap();
	
	/**
	 * Create the default chord reduction vocabulary, down to the 4 triads.
	 * 
	 * @return The default chord quality reduction.
	 */
	public static Map<ChordQuality, ChordQuality> createDefaultVocabMap() {
		Map<ChordQuality, ChordQuality> defaultMap = new HashMap<ChordQuality, ChordQuality>();
		
		defaultMap.put(ChordQuality.NO_CHORD, ChordQuality.NO_CHORD);
		defaultMap.put(ChordQuality.MAJOR, ChordQuality.MAJOR);
		defaultMap.put(ChordQuality.MINOR, ChordQuality.MINOR);
		defaultMap.put(ChordQuality.AUGMENTED, ChordQuality.AUGMENTED);
		defaultMap.put(ChordQuality.DIMINISHED, ChordQuality.DIMINISHED);
		defaultMap.put(ChordQuality.AUGMENTED_6, ChordQuality.AUGMENTED);
		defaultMap.put(ChordQuality.DOMINANT_7, ChordQuality.MAJOR);
		defaultMap.put(ChordQuality.MAJOR_7, ChordQuality.MAJOR);
		defaultMap.put(ChordQuality.MINOR_7, ChordQuality.MINOR);
		defaultMap.put(ChordQuality.DIMINISHED_7, ChordQuality.DIMINISHED);
		defaultMap.put(ChordQuality.HALF_DIMINISHED_7, ChordQuality.DIMINISHED);
		
		return defaultMap;
	}
	
	/**
	 * The onset time of this chord, in microseconds.
	 */
	public final long onsetTime;
	
	/**
	 * The offset time of this chord, in microseconds.
	 */
	public final long offsetTime;
	
	/**
	 * The root (base note) of this chord.
	 */
	public final int root;
	
	/**
	 * The quality of the chord (major, minor, diminished, etc.)
	 */
	public final ChordQuality quality;
	
	/**
	 * Create a new Chord with the given fields.
	 * 
	 * @param onset {@link #onsetTime}
	 * @param offset {@link #offsetTime}
	 * @param root {@link #root}
	 * @param quality {@link #quality}
	 */
	public Chord(long onset, long offset, int root, ChordQuality quality) {
		onsetTime = onset;
		offsetTime = offset;
		this.root = root;
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
		
		value = Integer.compare(root, o.root);
		if (value != 0) {
			return value;
		}
		
		return quality.compareTo(o.quality);
	}
}
