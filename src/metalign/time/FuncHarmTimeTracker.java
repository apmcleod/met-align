package metalign.time;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.SortedSet;
import java.util.TreeSet;

import metalign.beat.Beat;
import metalign.hierarchy.Measure;

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
		
		// Initialize fields
		timeSignatures = new ArrayList<TimeSignature>();
		timeSignatureTimes = new ArrayList<Long>();
		tatums = new ArrayList<Beat>();
		
		// We need ListIterators in order to rewind
		ListIterator<Long> downbeatIterator = (new ArrayList<Long>(downbeats)).listIterator();
		ListIterator<Long> beatIterator = (new ArrayList<Long>(beats)).listIterator();
		
		// Detect any anacrusis (pickups)
		List<Long> anacrusisBeats = getBeatTimesUntilNextDownbeat(downbeatIterator, beatIterator);
		boolean hasAnacrusis = !anacrusisBeats.isEmpty();
		
		int barNum = 0;
		
		// Create each bar
		while (beatIterator.hasNext() || downbeatIterator.hasNext()) {
			List<Long> beats = getBeatTimesUntilNextDownbeat(downbeatIterator, beatIterator);
			
			int subBeatsPerBeat = 2;
			int beatsPerBar = beats.size();
			
			// Fix for compound meters
			if (beatsPerBar > 3 && beatsPerBar % 3 == 0) {
				beatsPerBar /= 3;
				subBeatsPerBeat = 3;
			}

			int anchorPoints = beats.size();
			int anchorsPerBeat = anchorPoints / beatsPerBar;
			
			// Create anacrusis (once)
			if (hasAnacrusis) {
				timeSignatureTimes.add(anacrusisBeats.get(0));
				timeSignatures.add(new TimeSignature(new Measure(beatsPerBar, subBeatsPerBeat)));
				
				for (int i = beatsPerBar - anacrusisBeats.size(); i < anchorPoints; i++) {
					tatums.add(new Beat(-1, i / anchorsPerBeat, i % anchorsPerBeat, 0, anacrusisBeats.get(i)));
				}
				
				// Don't create anacrusis again
				hasAnacrusis = false;
			}
			
			// Add new time signature (if has changed AND we aren't in the final bar)
			TimeSignature ts = new TimeSignature(new Measure(beatsPerBar, subBeatsPerBeat));
			if (timeSignatures.isEmpty() || (!ts.equals(timeSignatures.get(timeSignatures.size() - 1)) && downbeatIterator.hasNext())) {
				timeSignatures.add(ts);
				timeSignatureTimes.add(beats.get(0));
			}
			
			// Place beats
			// TODO: Check how compound meters are done in dataset
			for (int beatNum = 0; beatNum < beats.size(); beatNum++) {
				tatums.add(new Beat(barNum, beatNum / anchorsPerBeat, beatNum % anchorsPerBeat, 0, beats.get(beatNum)));
			}
			
			barNum++;
		}
		
		return tatums;
	}

	/**
	 * Get a List of the beat times (from the beatIterator) until the first downbeat time.
	 * 
	 * @param downbeatIterator An iterator of the downbeat times in this piece.
	 * @param beatIterator An iterator of the unused beat times in this piece.
	 * @return A List of the beat times which we've missed
	 */
	private List<Long> getBeatTimesUntilNextDownbeat(ListIterator<Long> downbeatIterator, ListIterator<Long> beatIterator) {
		List<Long> beatTimes = new ArrayList<Long>();
		
		// No downbeats left. This is the last bar
		if (!downbeatIterator.hasNext() || !beatIterator.hasNext()) {
			while (beatIterator.hasNext()) {
				beatTimes.add(beatIterator.next());
			}
			
			return beatTimes;
		}
		
		// There is another downbeat somewhere
		long nextDownbeatTime = downbeatIterator.next();
		
		// Get all beat times until (not including) the next downbeat
		long beatTime = 0;
		while (beatIterator.hasNext() && (beatTime = beatIterator.next()) < nextDownbeatTime) {
			beatTimes.add(beatTime);
		}
		
		// Rewind the beat iterator so that the beat on the next downbeat is the next item 
		if (beatTime >= nextDownbeatTime) {
			beatIterator.previous();
		}
		
		return beatTimes;
	}

}
