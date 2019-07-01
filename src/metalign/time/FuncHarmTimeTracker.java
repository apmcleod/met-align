package metalign.time;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import metalign.beat.Beat;

/**
 * A <code>FuncHarmTimeTracker</code> contains the code for detecting the beats, downbeats, and time
 * signature of a parsed text file, generated from the Beethoven sonata dataset [1]. The text file
 * is generated using my python code.
 * <br>
 * [1] Tsung-Ping Chen and Li Su, “Functional Harmony Recognition with Multi-task Recurrent Neural Networks,”
 * International Society of Music Information Retrieval Conference (ISMIR), September 2018.
 * 
 * @author Andrew McLeod
 */
public class FuncHarmTimeTracker extends TimeTracker {
	
	/**
	 * A List of the tatums of this piece.
	 */
	private List<Beat> tatums = null;
	
	/**
	 * A List of the time signatures in this piece.
	 */
	private List<TimeSignature> timeSignatures = null;
	
	/**
	 * The times at which each time signature in {@link #timeSignatures} begins.
	 */
	private List<Long> timeSignatureTimes = null;
	
	/**
	 * An ordered set of the beats to track.
	 */
	private final SortedSet<Long> beats;
	
	/**
	 * An ordered set of the downbeats to track.
	 */
	private final SortedSet<Long> downbeats;
	
	/**
	 * Create a new empty time tracker.
	 */
	public FuncHarmTimeTracker() {
		beats = new TreeSet<Long>();
		downbeats = new TreeSet<Long>();
	}
	
	/**
	 * Add a beat at the given time.
	 * 
	 * @param time The time of a beat, in microseconds.
	 */
	public void addBeat(long time) {
		tatums = null;
		timeSignatures = null;
		timeSignatureTimes = null;
		
		beats.add(time);
	}
	
	/**
	 * Add a downbeat at the given time.
	 * 
	 * @param time The time of a downbeat, in microseconds.
	 */
	public void addDownbeat(long time) {
		tatums = null;
		timeSignatures = null;
		timeSignatureTimes = null;
		
		downbeats.add(time);
	}

	@Override
	public List<TimeSignature> getAllTimeSignatures() {
		if (timeSignatures == null) {
			getTatums();
		}
		
		return timeSignatures;
	}

	@Override
	public TimeSignature getTimeSignatureAtTime(long time) {
		if (timeSignatures == null) {
			getTatums();
		}
		
		for (int i = 0; i < timeSignatures.size() - 1; i++) {
			if (timeSignatureTimes.get(i + 1) > time) {
				return timeSignatures.get(i);
			}
		}
		
		return timeSignatures.get(timeSignatures.size() - 1);
	}

	@Override
	public List<Beat> getTatums() {
		if (tatums != null) {
			return tatums;
		}
		
		// TODO Create tatums, timeSignatures, and timeSignatureTimes lists.
		return null;
	}

}
