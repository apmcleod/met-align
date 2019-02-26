package metalign.hierarchy.lpcfg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

import metalign.Main;
import metalign.beat.Beat;
import metalign.hierarchy.Measure;
import metalign.hierarchy.lpcfg.MetricalLpcfgNonterminal.MetricalLpcfgLevel;
import metalign.utils.MathUtils;
import metalign.utils.MidiNote;

/**
 * A <code>MetricalLpcfgTreeFactory</code> is a class whose static methods aid in the
 * creation of {@link MetricalLpcfgTree}s. It cannot be instantiated.
 * 
 * @author Andrew McLeod - 25 April, 2016
 */
public class MetricalLpcfgTreeFactory {
	/**
	 * Private constructor to ensure that no factory is instantiated.
	 */
	private MetricalLpcfgTreeFactory() {}
	
	/**
	 * Make and return the tree corresponding to the given beats.
	 * 
	 * @param measure The measure type we are in.
	 * @param prevTimeReturn The last time of the previous tree. We will edit this value in place to the last
	 * time of the returned tree.
	 * @param barBeats The beats corresponding to this tree.
	 * @param nextTime The downbeat of the following bar.
	 * @param notes The notes that might overlap with this tree.
	 * 
	 * @return The created tree.
	 * 
	 * @throws MalformedTreeException If the given barBeats do not match up with the structure given by measure.
	 * Specifically, if the number of beats (not sub beats or tatums) within barBeats is not equal to the number
	 * of beats per bar given by measure.
	 */
	public static MetricalLpcfgTree makeTree(Measure measure, List<Long> prevTimeReturn, List<Beat> barBeats, long nextTime,
			List<MidiNote> notes) throws MalformedTreeException {
		MetricalLpcfgMeasure root = new MetricalLpcfgMeasure(measure);
		
		// Get beats
		List<Integer> beatIndices = new ArrayList<Integer>();
		for (int i = 0; i < barBeats.size(); i++) {
			if (barBeats.get(i).isBeat()) {
				beatIndices.add(i);
			}
		}
		
		if (beatIndices.size() != measure.getBeatsPerBar()) {
			throw new MalformedTreeException("Expected " + measure.getBeatsPerBar() + " beats per bar (from measure), but only "
					+ "given " + beatIndices.size() + " (from beat tracker).");
		}
		
		ListIterator<MidiNote> offsetIterator = notes.listIterator(); // Note offset times
		
		// Go through each beat, creating its tree
		for (int beatNum = 0; beatNum < beatIndices.size(); beatNum++) {
			// Get beats and metrical info
			int startIndex = beatIndices.get(beatNum);
			int endIndex = beatNum < beatIndices.size() - 1 ? beatIndices.get(beatNum + 1) : barBeats.size();
			List<Beat> beatBeats = barBeats.subList(startIndex, endIndex);
			
			long nextBeatTime = endIndex < barBeats.size() ? barBeats.get(endIndex).getTime() : nextTime;
			
			// Get notes
			while (offsetIterator.hasNext() && offsetIterator.next().getOffsetTime() <= prevTimeReturn.get(0));
			List<MidiNote> beatNotes = notes.subList(offsetIterator.previousIndex(), notes.size());
			
			// Rewind iterators for next beat
			offsetIterator.previous();
			
			root.addChild(makeBeat(measure, prevTimeReturn, beatBeats, nextBeatTime, beatNotes, root.isEmpty()));
		}
		
		return new MetricalLpcfgTree(root);
	}

	/**
	 * Make and return a {@link MetricalLpcfgNonterminal} node corresponding to a beat. If the given
	 * beatBeats List contains sub beats, this method will return a node using them. Otherwise,
	 * this will check both 2 and 3 sub-beats and return the one which seems to match the underlying
	 * notes the best, with a preference towards the number of sub-beats per beat dictated by the
	 * given measure.
	 * 
	 * @param measure The measure structure for this beat.
	 * @param prevTimeReturn The last tatum time from the previous beat. This value will be edited in place to
	 * return the last tatum time from this beat.
	 * @param beatBeats The Beats within this beat node.
	 * @param nextTime The first time of the following beat.
	 * @param beatNotes The notes that might fall within this beat.
	 * @param hasStarted True if some note has already occurred within this bar. False otherwise. This is used
	 * to handle extending notes.
	 * 
	 * @return The {@link MetricalLpcfgNonterminal} node corresponding to this beat.
	 * 
	 * @throws MalformedTreeException If the given beatBeats do not match up with a valid structure.
	 * Specifically, if the number of sub-beats within beatBeats is not 1 (only the first beat), 2, or 3.
	 */
	private static MetricalLpcfgNode makeBeat(Measure measure, List<Long> prevTimeReturn, List<Beat> beatBeats,
			long nextTime, List<MidiNote> beatNotes, boolean hasStarted) throws MalformedTreeException {
		
		if (beatBeats.size() == 1) {
			// No sub-beats given, try splitting into both 2 and 3.
			
			// Try in 2
			List<Long> timesIn2 = new ArrayList<Long>(Main.SUB_BEAT_LENGTH * 2);
			
			double diff = ((double) nextTime - beatBeats.get(0).getTime()) / (Main.SUB_BEAT_LENGTH * 2);
			if (nextTime == Long.MAX_VALUE) {
				// Fix for no following bar: keep previous bar's tatum length 
				diff = beatBeats.get(0).getTime() - prevTimeReturn.get(0);
			}
			
			for (int i = 0; i < Main.SUB_BEAT_LENGTH * 2; i++) {
				timesIn2.add(Math.round(beatBeats.get(0).getTime() + i * diff));
			}
			MetricalLpcfgNode in2 = makeBeat(prevTimeReturn.get(0), timesIn2, nextTime, beatNotes, hasStarted);
			
			// Try in 3
			List<Long> timesIn3 = new ArrayList<Long>(Main.SUB_BEAT_LENGTH * 3);
			
			diff = ((double) nextTime - beatBeats.get(0).getTime()) / (Main.SUB_BEAT_LENGTH * 3);
			if (nextTime == Long.MAX_VALUE) {
				// Fix for no following bar: keep previous bar's tatum length 
				diff = beatBeats.get(0).getTime() - prevTimeReturn.get(0);
			}
			
			for (int i = 0; i < Main.SUB_BEAT_LENGTH * 3; i++) {
				timesIn2.add(Math.round(beatBeats.get(0).getTime() + i * diff));
			}
			MetricalLpcfgNode in3 = makeBeat(prevTimeReturn.get(0), timesIn3, nextTime, beatNotes, hasStarted);
			
			// Measure well-formedness of each and return the best one.
			
			// Measure average distance from note onset to tatum
			double avgDiff2 = 0.0;
			double avgDiff3 = 0.0;
			int count = 0;
			
			for (MidiNote note : beatNotes) {
				long time = note.getOnsetTime();
				
				if (time < timesIn2.size() - 10000 || time > nextTime - 10000) {
					continue;
				}
				
				count++;
				
				int closestIndex2 = MathUtils.linearSearch(timesIn2, time, 0);
				int closestIndex3 = MathUtils.linearSearch(timesIn3, time, 0);
				
				avgDiff2 += Math.abs(timesIn2.get(closestIndex2) - time);
				avgDiff3 += Math.abs(timesIn3.get(closestIndex3) - time);
			}
			
			avgDiff2 /= count;
			avgDiff3 /= count;
			
			// Get best (assume in2, it's more common)
			List<Long> bestTimes = timesIn2;
			MetricalLpcfgNode bestNode = in2;
			if ((measure.getSubBeatsPerBeat() == 2 && avgDiff3 < avgDiff2 - 100) ||
				(measure.getSubBeatsPerBeat() == 3 && avgDiff3 - 100 > avgDiff2)) {
				
				// 3 splits is the best choice
				bestTimes = timesIn3;
				bestNode = in3;
			}
			
			prevTimeReturn.set(0, bestTimes.get(bestTimes.size() - 1));
			return bestNode;
		}
		
		// Here, sub beats are given. We will trust them.
		
		// Get beats
		List<Integer> subBeatIndices = new ArrayList<Integer>();
		for (int i = 0; i < beatBeats.size(); i++) {
			if (beatBeats.get(i).isBeat()) {
				subBeatIndices.add(i);
			}
		}
		
		if (subBeatIndices.size() != 2 && subBeatIndices.size() != 3) {
			throw new MalformedTreeException("Expected 2 or 3 sub-beats per bar, but only given "
					+ subBeatIndices.size() + " (from beat tracker).");
		}
		
		// Go through each sub-beat, creating its times
		List<Long> times = new ArrayList<Long>();
		for (int subBeatNum = 0; subBeatNum < subBeatIndices.size(); subBeatNum++) {
			int numAnchors = (subBeatNum == subBeatIndices.size() - 1 ? beatBeats.size() : subBeatIndices.get(subBeatNum + 1))
					- subBeatIndices.get(subBeatNum);
			
			if (Main.SUB_BEAT_LENGTH % numAnchors != 0) {
				throw new MalformedTreeException("The number of anchor points between consecutive sub-beats (" + numAnchors +
						", from beat tracker) does not divide Main.SUB_BEAT_LENGTH (" + Main.SUB_BEAT_LENGTH + ")");
			}
			
			int tatumsBetweenAnchors = Main.SUB_BEAT_LENGTH / numAnchors;
			// Pre-load this to allow for saving previous tatum in case of nextTime = Long.MAX_VALUE
			double diff = beatBeats.get(0).getTime() - prevTimeReturn.get(0);
			
			// Go through each anchor Beat in beatBeats
			for (int anchor = 0; anchor < numAnchors; anchor++) {
				long startTime = beatBeats.get(subBeatIndices.get(subBeatNum) + anchor).getTime();
				long endTime = subBeatNum < subBeatIndices.size() - 1 ?
									beatBeats.get(subBeatIndices.get(subBeatNum + 1)).getTime() : nextTime;
				
				if (endTime != Long.MAX_VALUE) {
					diff = ((double) endTime - startTime) / tatumsBetweenAnchors;
				}
				
				for (int tatum = 0; tatum < tatumsBetweenAnchors; tatum++) {
					times.add((long) (startTime + diff * tatum));
				}
			}
		}
		
		MetricalLpcfgNode beat = makeBeat(prevTimeReturn.get(0), times, nextTime, beatNotes, hasStarted);
		prevTimeReturn.set(0, times.get(times.size() - 1));
		return beat;
	}

	/**
	 * Make a {@link MetricalLpcfgNonterminal} representing a single beat with a set number of sub-beats.
	 * The number of sub-beats is passed within the times argument. Specifically, the length of times should
	 * be exactly sub-beats * {@link Main#SUB_BEAT_LENGTH}.
	 * 
	 * @param measure The last time from 
	 * @param prevTime The last time from the previous beat.
	 * @param times A List of the times of the tatums within this node. This should be a List of length
	 * exactly sub-beats * {@link Main#SUB_BEAT_LENGTH}. 
	 * @param nextTime The time of the first tatum in the next beat.
	 * @param notes The notes that might lie within this beat.
	 * @param hasStarted True if some note has already occurred in this bar. False otherwise. This is used
	 * to handle extending notes.
	 * 
	 * @return The {@link MetricalLpcfgNonterminal} representing a Beat with the given properties.
	 */
	private static MetricalLpcfgNode makeBeat(long prevTime, List<Long> times, long nextTime,
			List<MidiNote> notes, boolean hasStarted) {
		MetricalLpcfgNonterminal beat = new MetricalLpcfgNonterminal(MetricalLpcfgLevel.BEAT);
		
		List<MetricalLpcfgQuantum> quantums = new ArrayList<MetricalLpcfgQuantum>(Collections.nCopies(times.size(),
				Main.EXTEND_NOTES && hasStarted ? MetricalLpcfgQuantum.TIE : MetricalLpcfgQuantum.REST));
		
		// Get all times (including previous and next)
		List<Long> allTimes = new ArrayList<Long>(times.size() + 2);
		allTimes.add(prevTime);
		for (Long time : times) {
			allTimes.add(time);
		}
		allTimes.add(nextTime);
		
		// Fill quantums array with notes
		int onsetIndex = 0; // To enable slightly faster searching
		for (int noteNum = 0; noteNum < notes.size(); noteNum++) {
			MidiNote note = notes.get(noteNum);
			
			// Get indices for this note
			onsetIndex = MathUtils.linearSearch(allTimes, note.getOnsetTime(), onsetIndex);
			if (onsetIndex == allTimes.size() - 1) {
				// Onset is after this beat. Exit loop.
				break;
			}
			int offsetIndex = MathUtils.linearSearch(allTimes, note.getOffsetTime(), onsetIndex);
			
			// Place onset if it falls within this beat
			if (onsetIndex > 0) {
				quantums.set(onsetIndex - 1, MetricalLpcfgQuantum.ONSET);
			}
			
			// Fill remainder with ties
			Collections.fill(quantums.subList(onsetIndex, Main.EXTEND_NOTES ? quantums.size() : offsetIndex), MetricalLpcfgQuantum.TIE);
		}
		
		// Here, quantums array is full and correct.
		
		// Check if can directly make a terminal
		if (MetricalLpcfgTerminal.reducesToOne(quantums)) {
			beat.addChild(new MetricalLpcfgTerminal(quantums, 1, 1));
			return beat;
		}
		
		// Can't reduce to 1. Make each sub-beat individually.
		int numSubBeats = quantums.size() / Main.SUB_BEAT_LENGTH;
		for (int subBeatIndex = 0; subBeatIndex < quantums.size(); subBeatIndex += Main.SUB_BEAT_LENGTH) {
			beat.addChild(new MetricalLpcfgTerminal(quantums.subList(subBeatIndex, subBeatIndex + Main.SUB_BEAT_LENGTH), 1, numSubBeats));
		}
		
		beat.fixChildrenTypes();
		return beat;
	}
}
