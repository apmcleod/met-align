package metalign.utils;

import java.util.List;

import metalign.beat.Beat;

/**
 * A <code>MidiNote</code> represents a single instance of a played midi note. It contains
 * information on the note's pitch, onset, offset, and velocity, as well as which MidiChord
 * it is assigned to.
 * <p>
 * MidiNotes are Comparable, and their natural ordering is determined strictly by each note's
 * {@link #onsetTime}. That means that the {@link #compareTo(MidiNote)} method will return 0
 * if two notes are compared which have the same onset time. For this reason, MidiNotes should
 * not be used in any SortedSets, since they test for equality based soleley on the compareTo
 * method, and will therefore not be able to hold multiple notes with the same onset time.
 * 
 * @author Andrew McLeod - 23 October, 2014
 */
public class MidiNote implements Comparable<MidiNote> {
	
	/**
	 * The gold standard voice which this note came from.
	 */
	private int correctVoice;
	
	/**
	 * The onset time of this note, measured in microseconds.
	 */
	private long onsetTime;
	
	/**
	 * The onset tick of this note.
	 */
	private long onsetTick;
	
	/**
	 * The offset time of this note, measured in microseconds, or 0 if it is still active.
	 */
	private long offsetTime;
	
	/**
	 * The offset tick of this note, or 0 if it is still active.
	 */
	private long offsetTick;
	
	/**
	 * The velocity of this note.
	 */
	private int velocity;
	
	/**
	 * The key number of this note. For piano, it should be on the range 21 to 108 inclusive.
	 */
	private int pitch;

	/**
	 * The index of the guessed voice of this note.
	 */
	private int guessedVoice;
	
	/**
	 * Constructor for a new note.
	 * 
	 * @param key {@link #pitch}
	 * @param velocity {@link #velocity}
	 * @param onsetTime {@link #onsetTime}
	 * @param onsetTick {@link #onsetTick}
	 * @param correctVoice {@link #correctVoice}
	 * @param guessedVoice {@link #guessedVoice}
	 */
	public MidiNote(int key, int velocity, long onsetTime, long onsetTick, int correctVoice, int guessedVoice) {
		this.pitch = key;
		this.velocity = velocity;
		this.onsetTime = onsetTime;
		this.onsetTick = onsetTick;
		this.correctVoice = correctVoice;
		offsetTime = 0;
		offsetTick = 0;
		this.guessedVoice = guessedVoice;
	}
	
	/**
	 * Move this note's onset to the given location.
	 * 
	 * @param onsetTime {@link #onsetTime}
	 * @param onsetTick {@link #onsetTick}
	 */
	public void setOnset(long onsetTime, long onsetTick) {
		this.onsetTime = onsetTime;
		this.onsetTick = onsetTick;
	}
	
	/**
	 * Move this note's offset to the given location.
	 * 
	 * @param offsetTime {@link #offsetTime}
	 * @param offsetTick {@link #offsetTick}
	 */
	public void setOffset(long offsetTime, long offsetTick) {
		this.offsetTime = offsetTime;
		this.offsetTick = offsetTick;
	}
	
	/**
	 * Returns whether this note is active (still on) or not. A note will be active
	 * 
	 * @return True if this note is active. False otherwise.
	 */
	public boolean isActive() {
		return offsetTime == 0;
	}
	
	/**
	 * Turns off this note at the given time and tick.
	 * 
	 * @param offsetTime {@link #offsetTime}
	 * @param offsetTick {@link #offsetTick}
	 */
	public void close(long offsetTime, long offsetTick) {
		setOffset(offsetTime, offsetTick);
	}

	/**
	 * Return whether this note overlaps another MidiNote in time.
	 * 
	 * @param other The note we want to check for overlap. This can be null, in which case
	 * we will return false.
	 * @return True if the notes overlap. False otherwise.
	 */
	public boolean overlaps(MidiNote other) {
		if (other == null) {
			return false;
		}
		
		if (onsetTick < other.getOffsetTick() && offsetTick > other.getOnsetTick()) {
			// We start before the other finishes, and finish after it starts.
			return true;
		}

		return false;
	}
	
	/**
	 * Gets the onset time of this note.
	 * 
	 * @return {@link #onsetTime}
	 */
	public long getOnsetTime() {
		return onsetTime;
	}
	
	/**
	 * Gets the onset tick of this note.
	 * 
	 * @return {@link #onsetTick}
	 */
	public long getOnsetTick() {
		return onsetTick;
	}

	/**
	 * Gets the offset time of this note.
	 * 
	 * @return {@link #offsetTime}
	 */
	public long getOffsetTime() {
		return offsetTime;
	}
	
	/**
	 * Gets the offset tick of this note.
	 * 
	 * @return {@link #offsetTick}
	 */
	public long getOffsetTick() {
		return offsetTick;
	}
	
	/**
	 * Get the duration of this MidiNote in microseconds.
	 * 
	 * @return The duration in microseconds.
	 */
	public long getDurationTime() {
		return offsetTime - onsetTime;
	}
	
	/**
	 * Gets the key number of this note.
	 * 
	 * @return {@link #pitch}
	 */
	public int getPitch() {
		return pitch;
	}
	
	/**
	 * Get the velocity of this note.
	 * 
	 * @return {@link #velocity}
	 */
	public int getVelocity() {
		return velocity;
	}

	/**
	 * Get the gold standard voice of this note.
	 * 
	 * @return {@link #correctVoice}
	 */
	public int getCorrectVoice() {
		return correctVoice;
	}
	
	/**
	 * Set the correctVoice of this note to a new value.
	 * 
	 * @param correctVoice {@link #correctVoice}
	 */
	public void setCorrectVoice(int correctVoice) {
		this.correctVoice = correctVoice;
	}
	
	/**
	 * Set the guessed voice of this note to the given value.
	 * 
	 * @param voice {@link #guessedVoice}
	 */
	public void setGuessedVoice(int voice) {
		guessedVoice = voice;
	}
	
	/**
	 * Get the guessed voice of this note.
	 * 
	 * @return {@link #guessedVoice}
	 */
	public int getGuessedVoice() {
		return guessedVoice;
	}
	
	public Beat getOnsetSubBeat(List<Beat> beats) {
		if (beats.isEmpty()) {
			return null;
		}
		
		return MathUtils.getBeatsAroundTime(onsetTime, beats).get(0);
	}

	public Beat getOffsetSubBeat(List<Beat> beats) {
		if (beats.isEmpty()) {
			return null;
		}
		
		List<Beat> beatList = MathUtils.getBeatsAroundTime(offsetTime, beats);
		
		return beatList.get(beatList.size() - 1);
	}

	public Beat getOnsetTatum(List<Beat> beats) {
		List<Beat> beatList = MathUtils.getBeatsAroundTime(onsetTime, beats);
		
		long minDiff = Long.MAX_VALUE;
		Beat closestBeat = null;
		for (Beat beat : beatList) {
			long diff = Math.abs(onsetTime - beat.getTime());
			
			if (diff < minDiff) {
				minDiff = diff;
				closestBeat = beat;
			}
		}
		
		return closestBeat;
	}

	public Beat getOffsetTatum(List<Beat> beats) {
		List<Beat> beatList = MathUtils.getBeatsAroundTime(offsetTime, beats);
		
		long minDiff = Long.MAX_VALUE;
		Beat closestBeat = null;
		for (Beat beat : beatList) {
			long diff = Math.abs(offsetTime - beat.getTime());
			
			if (diff - 1 <= minDiff) {
				minDiff = diff;
				closestBeat = beat;
			}
		}
		
		return closestBeat;
	}
	
	public int getOffsetTatumIndex(List<Integer> beats) {
		int index = MathUtils.getFirstIndexAroundTime(offsetTime, beats);
		
		long diff = Math.abs(offsetTime - beats.get(index));
		
		if (index + 1 < beats.size()) {
			long newDiff = Math.abs(offsetTime - beats.get(index + 1));
			
			if (newDiff - 1 <= diff) {
				return index + 1;
			}
		}
		
		return index;
	}

	public int getOnsetTatumIndex(List<Integer> beats) {
		int index = MathUtils.getFirstIndexAroundTime(onsetTime, beats);
		
		long diff = Math.abs(onsetTime - beats.get(index));
		
		if (index + 1 < beats.size()) {
			long newDiff = Math.abs(onsetTime - beats.get(index + 1));
			
			if (newDiff - 1 <= diff) {
				return index + 1;
			}
		}
		
		return index;
	}

	@Override
	public String toString() {
		return String.format("(K:%s  V:%d  [%d-%d] %d)", MidiNote.getNoteName(pitch), velocity, onsetTime, offsetTime, correctVoice);
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof MidiNote)) {
			return false;
		}
		
		MidiNote note = (MidiNote) o;
		
		return onsetTime == note.onsetTime &&
				offsetTime == note.offsetTime &&
				pitch == note.pitch &&
				velocity == note.velocity &&
				correctVoice == note.correctVoice &&
				guessedVoice == note.guessedVoice;
	}
	
	public boolean equalsIgnoreVoice(MidiNote note) {
		return onsetTime == note.onsetTime &&
				offsetTime == note.offsetTime &&
				pitch == note.pitch &&
				velocity == note.velocity;
	}

	@Override
	public int compareTo(MidiNote o) {
		if (o == null) {
			return 1;
		}
		
		int result = Long.compare(onsetTime, o.onsetTime);
		if (result != 0) {
			return result;
		}
		
		result = Long.compare(offsetTime, o.offsetTime);
		if (result != 0) {
			return result;
		}
		
		result = Integer.compare(pitch, o.pitch);
		if (result != 0) {
			return result;
		}
		
		result = Integer.compare(velocity, o.velocity);
		if (result != 0) {
			return result;
		}
		
		result = Integer.compare(correctVoice,  o.correctVoice);
		if (result != 0) {
			return result;
		}
		
		return Integer.compare(guessedVoice, o.guessedVoice);
	}
	
	/**
	 * Get the note name of the given MIDI pitch value. Accidentals are currently all written
	 * as sharps.
	 * 
	 * @param pitch The MIDI pitch value whose note name we want, on the range 0-127
	 * @return The note name of the given MIDI pitch value
	 */
	public static String getNoteName(int pitch) {
		String[] notes = new String[] {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
		
		return notes[pitch % 12] + (pitch / 12 - 1);
	}
	
	/**
	 * Get the MIDI pitch value of the given note name. Accidentals are currently all written
	 * as sharps.
	 * 
	 * @param noteName The note name whose MIDI pitch value we want
	 * @return The MIDI pitch value for the given note name
	 */
	public static int getPitch(String noteName) {
		String[] notes = new String[] {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
		
		String pitch = noteName.substring(0, noteName.length() - 1);
		int octave = Integer.parseInt(noteName.substring(noteName.length() - 1));
		
		int pitchValue = 12 * (octave + 1);
		for (int i = 0; i < notes.length; i++) {
			if (notes[i].equals(pitch)) {
				return pitchValue + i;
			}
		}
		
		return 0;
	}
}
