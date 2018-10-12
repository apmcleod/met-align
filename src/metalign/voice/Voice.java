package metalign.voice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import metalign.utils.MathUtils;
import metalign.utils.MidiNote;
import metalign.voice.hmm.HmmVoiceSplittingModelParameters;

/**
 * A <code>SingleNoteVoice</code> is a node in the LinkedList representing a
 * voice. Each node has only a previous pointer and a {@link MidiNote}.
 * Only a previous pointer is needed because we allow for Voices to split and clone themselves,
 * keeping the beginning of their note sequences identical. This allows us to have multiple
 * LinkedLists of notes without needing multiple full List objects. Rather, they all point
 * back to their common prefix LinkedLists.
 * 
 * @author Andrew McLeod - 6 April, 2015
 */
public class Voice implements Comparable<Voice> {
	/**
	 * The Voice ending at second to last note in this voice.
	 */
	private final Voice previous;
	
	/**
	 * The most recent note of this voice.
	 */
	private final MidiNote mostRecentNote;
	
	/**
	 * The onset time of the first note in this voice.
	 */
	private final long firstNoteTime;
	
	/**
	 * A List of the MidiNotes in this voice.
	 */
	private List<MidiNote> notes;
	
	/**
	 * Create a new Voice with the given previous voice.
	 * 
	 * @param note {@link #mostRecentNote}
	 * @param prev {@link #previous}
	 */
	public Voice(MidiNote note, Voice prev) {
		previous = prev;
		mostRecentNote = note;
		
		firstNoteTime = prev == null ? note.getOnsetTime() : prev.firstNoteTime;
		
		notes = new ArrayList<MidiNote>(prev == null ? 1 : prev.getNumNotes() + 1);
		if (prev != null) {
			notes.addAll(prev.notes);
		}
		notes.add(note);
	}
	
	/**
	 * Create a new Voice.
	 * 
	 * @param note {@link #mostRecentNote}
	 */
	public Voice(MidiNote note) {
		this(note, null);
	}
	
	/**
	 * Get the probability score of adding the given note to this Voice.
	 * 
	 * @param note The note we want to add.
	 * @return The probability score for the given note.
	 */
	public double getProbability(MidiNote note, HmmVoiceSplittingModelParameters params) {
		double pitch = pitchScore(getWeightedLastPitch(params), note.getPitch(), params);
		double gap = gapScore(note.getOnsetTime(), mostRecentNote.getOffsetTime(), params);
		return pitch * gap;
	}

	/**
	 * Get the pitch closeness of the two given pitches. This value should be higher
	 * the closer together the two pitch values are. The first input parameter is a double
	 * because it is drawn from {@link #getWeightedLastPitch(HmmVoiceSplittingModelParameters)}.
	 * 
	 * @param weightedPitch A weighted pitch, drawn from {@link #getWeightedLastPitch(HmmVoiceSplittingModelParameters)}.
	 * @param pitch An exact pitch.
	 * @return The pitch score of the given two pitches, a value between 0 and 1.
	 */
	private double pitchScore(double weightedPitch, int pitch, HmmVoiceSplittingModelParameters params) {
		return MathUtils.gaussianWindow(weightedPitch, pitch, params.PITCH_STD);
	}

	/**
	 * Get the pitch closeness of the two given pitches. This value should be higher
	 * the closer together the two pitch values are.
	 * 
	 * @param time1 A time.
	 * @param time2 Another time.
	 * @return The gap score of the two given time values, a value between 0 and 1.
	 */
	private double gapScore(long time1, long time2, HmmVoiceSplittingModelParameters params) {
		double timeDiff = Math.abs(time2 - time1);
		double inside = Math.max(0, -timeDiff / params.GAP_STD_MICROS + 1);
		double log = Math.log(inside) + 1;
		return Math.max(log, params.MIN_GAP_SCORE);
	}
	
	/**
	 * Decide if we can add a note with the given length at the given time based on the given parameters.
	 * 
	 * @param time The onset time of the note we want to add.
	 * @param length The length of the note we want to add.
	 * @param params The parameters we're using.
	 * @return True if we can add a note of the given duration at the given time. False otherwise.
	 */
	public boolean canAddNoteAtTime(long time, long length, HmmVoiceSplittingModelParameters params) {
		long overlap = mostRecentNote.getOffsetTime() - time;
		
		return overlap <= mostRecentNote.getDurationTime() / 2 && overlap < length;
	}

	/**
	 * Get the weighted pitch of this voice.
	 * 
	 * @param params The paramters we're using.
	 * @return The weighted pitch of this voice.
	 */
	public double getWeightedLastPitch(HmmVoiceSplittingModelParameters params) {
		double weight = 1;
		double totalWeight = 0;
		double sum = 0;
		
		// Most recent PITCH_HISTORY_LENGTH notes
		Voice noteNode = this;
		for (int i = 0; i < params.PITCH_HISTORY_LENGTH && noteNode != null; i++, noteNode = noteNode.previous) {
			sum += noteNode.mostRecentNote.getPitch() * weight;
			
			totalWeight += weight;
			weight *= 0.5;
		}
		
		return sum / totalWeight;
	}

	/**
	 * Get the number of notes we've correctly grouped into this voice, based on the most common voice in the voice.
	 * 
	 * @return The number of notes we've assigned into this voice correctly.
	 */
	public int getNumNotesCorrect() {
		Map<Integer, Integer> counts = new HashMap<Integer, Integer>();
		
		for (Voice noteNode = this; noteNode != null; noteNode = noteNode.previous) {
			int channel = noteNode.mostRecentNote.getCorrectVoice();
			if (!counts.containsKey(channel)) {
				counts.put(channel, 0);
			}
				
			counts.put(channel, counts.get(channel) + 1);
		}
				
		int maxCount = -1;
		for (int count : counts.values()) {
			maxCount = Math.max(maxCount, count);
		}
		
		return maxCount;
	}
	
	/**
	 * Get the number of links in this Voice which are correct. That is, the number of times
	 * that two consecutive notes belong to the same midi channel.
	 * 
	 * @param goldStandard The gold standard voices for this song.
	 * @return The number of times that two consecutive notes belong to the same midi channel.
	 */
	public int getNumLinksCorrect(List<List<MidiNote>> goldStandard) {
		int count = 0;
		
		for (Voice node = this; node.previous != null; node = node.previous) {
			int index = -1;
			MidiNote guessedPrev = node.previous.mostRecentNote;
			MidiNote note = node.mostRecentNote;
			
			for (int channel = 0; channel < goldStandard.size(); channel++) {
				for (int i = 0; i < goldStandard.get(channel).size(); i++) {
					MidiNote goldNote = goldStandard.get(channel).get(i);
					if (goldNote.equalsIgnoreVoice(note)) {
						index = i;
						break;
					}
				}
				
				if (index > 0 && goldStandard.get(channel).get(--index).equalsIgnoreVoice(guessedPrev)) {
					// Match!
					count++;
					break;
					
				} else if (index != -1) {
					// No match - invalidate index
					index = -1;
				}
			}
		}
		
		return count;
	}
	
	/**
	 * Get the number of notes in the linked list with this node as its tail.
	 * 
	 * @return The number of notes.
	 */
	public int getNumNotes() {
		return notes.size();
	}

	/**
	 * Get the List of notes which this node is the tail of, in chronological order.
	 * 
	 * @return A List of notes in chronological order, ending with this one.
	 */
	public List<MidiNote> getNotes() {
		return notes;
	}
	
	/**
	 * Get the most recent note in this voice.
	 * 
	 * @return {@link #mostRecentNote}
	 */
	public MidiNote getMostRecentNote() {
		return mostRecentNote;
	}
	
	/**
	 * Get the voice ending at the previous note in this voice.
	 * 
	 * @return {@link #previous}
	 */
	public Voice getPrevious() {
		return previous;
	}
	
	/**
	 * Return true if this voice has been newly added this step and false otherwise.
	 * 
	 * @return
	 */
	public boolean isNew(long time) {
		return firstNoteTime == time;
	}
	
	/**
	 * Get the gap lengths for the notes in this Voice. That is, the difference between the first note's
	 * offset and the second note's onset for each pair of consecutive notes.
	 * 
	 * @return A List of the gap lengths for each pair of consecutive notes in this Voice.
	 */
	public List<Integer> getGapLengths() {
		List<MidiNote> notes = getNotes();
		
		if (notes.size() <= 1) {
			return new ArrayList<Integer>(0);
		}
		
		List<Integer> gaps = new ArrayList<Integer>(notes.size() - 1);
		for (int i = 0; i < notes.size() - 1; i++) {
			MidiNote prev = notes.get(i);
			MidiNote next = notes.get(i + 1);
			
			int gap = (int) (next.getOnsetTime() - prev.getOffsetTime());
			
			gaps.add(gap);
		}
		
		return gaps;
	}
	
	/**
	 * Get the absolute values of the gap lengths for the notes in this Voice. That is, the absolute value
	 * of the difference between the first note's offset and the second note's onset for each pair of
	 * consecutive notes.
	 * 
	 * @return A List of the absolute values of the gap lengths for each pair of consecutive notes in this Voice.
	 */
	public List<Integer> getGapLengthsPositive() {
		List<MidiNote> notes = getNotes();
		
		if (notes.size() <= 1) {
			return new ArrayList<Integer>(0);
		}
		
		List<Integer> gaps = new ArrayList<Integer>(notes.size() - 1);
		for (int i = 0; i < notes.size() - 1; i++) {
			MidiNote prev = notes.get(i);
			MidiNote next = notes.get(i + 1);
			
			int gap = (int) (next.getOnsetTime() - prev.getOffsetTime());
			
			gaps.add(Math.abs(gap));
		}
		
		return gaps;
	}
	
	/**
	 * Get the gap length percentages for the notes in this Voice. That is, the difference
	 * between the first note's offset and the second note's onset, divided by the first note's
	 * duration, for each pair of consecutive notes.
	 * 
	 * @return A List of the gap length percentages for each pair of consecutive notes in this Voice.
	 */
	public List<Double> getGapLengthsPercentage() {
		List<MidiNote> notes = getNotes();
		
		if (notes.size() <= 1) {
			return new ArrayList<Double>(0);
		}
		
		List<Double> gaps = new ArrayList<Double>(notes.size() - 1);
		for (int i = 0; i < notes.size() - 1; i++) {
			MidiNote prev = notes.get(i);
			MidiNote next = notes.get(i + 1);
			
			double gap = next.getOnsetTime() - prev.getOffsetTime();
			double percent = gap / prev.getDurationTime();
			
			if (Math.abs(percent) < 0.05) {
				percent = 0;
			}
			
			gaps.add(percent);
		}
		
		return gaps;
	}
	
	/**
	 * Get the absolute values of the gap length percentages for the notes in this Voice. That is, the
	 * absolute value of the difference between the first note's offset and the second note's onset, divided
	 * by the first note's duration, for each pair of consecutive notes.
	 * 
	 * @return A List of the absolute values of the gap length percentages for each pair of consecutive notes
	 * in this Voice.
	 */
	public List<Double> getGapLengthsPercentagePositive() {
		List<MidiNote> notes = getNotes();
		
		if (notes.size() <= 1) {
			return new ArrayList<Double>(0);
		}
		
		List<Double> gaps = new ArrayList<Double>(notes.size() - 1);
		for (int i = 0; i < notes.size() - 1; i++) {
			MidiNote prev = notes.get(i);
			MidiNote next = notes.get(i + 1);
			
			double gap = next.getOnsetTime() - prev.getOffsetTime();
			
			gaps.add(Math.abs(gap / prev.getDurationTime()));
		}
		
		return gaps;
	}
	
	@Override
	public String toString() {
		return getNotes().toString();
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Voice)) {
			return false;
		}
		
		return compareTo((Voice) o) == 0;
	}

	@Override
	public int compareTo(Voice o) {
		if (o == null) {
			return -1;
		}
		
		int result = notes.size() - o.notes.size();
		if (result != 0) {
			return result;
		}
		
		for (int i = notes.size() - 1; i >= 0; i--) {
			result = notes.get(i).compareTo(o.notes.get(i));
			if (result != 0) {
				return result;
			}
		}
		
		return 0;
	}
}

