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
			
			root.addChild(makeBeat(measure, prevTimeReturn, beatBeats, nextBeatTime, beatNotes, !root.isEmpty()));
		}
		
		root.fixChildrenTypes();
		
		return new MetricalLpcfgTree(root);
	}

	/**
	 * Make and return a {@link MetricalLpcfgNonterminal} node corresponding to a beat. This will try
	 * both 2 and 3 sub-beats and return the one which seems to match the underlying
	 * notes the best, with a preference towards the number of sub-beats given in beatBeats (if any),
	 * of the number of sub beats per beat dictated by the given measure (otherwise).
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
		
		// TODO: Allow for duple and triplet in different sub beats of the same beat?
		
		// Try splitting into both 2 and 3.
		
		// Try in 2
		List<Long> timesIn2In3 = getAnchoredTimes(beatBeats, 6, nextTime, beatBeats.get(0).getTime() - prevTimeReturn.get(0));
		List<Long> timesIn2In4 = getAnchoredTimes(beatBeats, 8, nextTime, beatBeats.get(0).getTime() - prevTimeReturn.get(0));
		
		MetricalLpcfgNode in2In3 = makeBeat(prevTimeReturn.get(0), timesIn2In3, nextTime, beatNotes, hasStarted, 3);
		MetricalLpcfgNode in2In4 = makeBeat(prevTimeReturn.get(0), timesIn2In4, nextTime, beatNotes, hasStarted, 4);
		
		// Try in 3
		List<Long> timesIn3In3 = getAnchoredTimes(beatBeats, 9, nextTime, beatBeats.get(0).getTime() - prevTimeReturn.get(0));
		List<Long> timesIn3In4 = getAnchoredTimes(beatBeats, 12, nextTime, beatBeats.get(0).getTime() - prevTimeReturn.get(0));
		
		MetricalLpcfgNode in3In3 = makeBeat(prevTimeReturn.get(0), timesIn3In3, nextTime, beatNotes, hasStarted, 3);
		MetricalLpcfgNode in3In4 = makeBeat(prevTimeReturn.get(0), timesIn3In4, nextTime, beatNotes, hasStarted, 4);
		
		// Measure well-formedness of each and return the best one.
		
		// Measure average distance from note onset to tatum
		double avgDiff23 = 0.0;
		double avgDiff24 = 0.0;
		double avgDiff33 = 0.0;
		double avgDiff34 = 0.0;
		int count = 0;
		
		for (MidiNote note : beatNotes) {
			long time = note.getOnsetTime();
			
			// Skip notes not in this bar
			if (time < timesIn2In3.get(0) - 10000) {
				continue;
			}
			if (time > nextTime - 10000) {
				break;
			}
			
			count++;
			
			int closestIndex23 = MathUtils.linearSearch(timesIn2In3, time, 0);
			int closestIndex24 = MathUtils.linearSearch(timesIn2In4, time, 0);
			int closestIndex33 = MathUtils.linearSearch(timesIn3In3, time, 0);
			int closestIndex34 = MathUtils.linearSearch(timesIn3In4, time, 0);
			
			avgDiff23 += Math.abs(timesIn2In3.get(closestIndex23) - time);
			avgDiff24 += Math.abs(timesIn2In4.get(closestIndex24) - time);
			avgDiff33 += Math.abs(timesIn3In3.get(closestIndex33) - time);
			avgDiff34 += Math.abs(timesIn3In4.get(closestIndex34) - time);
		}
		
		avgDiff23 /= count;
		avgDiff24 /= count;
		avgDiff33 /= count;
		avgDiff34 /= count;
		
		// TODO: Handle triplet anchors in duple bar
		
		// Order of preference:
		// 1. Number of subbeats as given by anchors. (With 4 sub beats).
		// 2. Number of sub beats as given by measure type. (With 4 sub beats).
		// 3-4. 1 and 2, with 3 sub beats.
		// 5-8. 1-4, but with the other number of sub beats (if not already used).
		
		// Idea: First, check best in2 and in3.
		// Then, compare those 2
		
		// In2 check
		MetricalLpcfgNode bestNode2 = in2In4;
		List<Long> bestTimes2 = timesIn2In4;
		double bestDiff2 = avgDiff24;
		
		if (avgDiff23 < 0.5 * avgDiff24) {
			
			// 2 triplet sub beats is the best choice
			bestNode2 = in2In3;
			bestTimes2 = timesIn2In3;
			bestDiff2 = avgDiff23;
		}
		
		// In3 check
		MetricalLpcfgNode bestNode3 = in3In4;
		List<Long> bestTimes3 = timesIn3In4;
		double bestDiff3 = avgDiff34;
		
		if (avgDiff33 < 0.5 * avgDiff34) {
			
			// 2 triplet sub beats is the best choice
			bestNode3 = in3In3;
			bestTimes3 = timesIn3In3;
			bestDiff3 = avgDiff33;
		}
		
		// Check for preference between best 2 and best 3
		MetricalLpcfgNode best;
		List<Long> bestTimes;
		
		if (bestDiff2 < 0.5 * bestDiff3) {
			// 2 is clearly better
			bestTimes = bestTimes2;
			best = bestNode2;
			
		} else if (bestDiff3 < 0.5 * bestDiff2) {
			// 3 is clearly better
			bestTimes = bestTimes3;
			best = bestNode3;
			
		} else {
			// Check which one's quantum can be reduced to the shortest length
			List<MetricalLpcfgQuantum> quantum2 = new ArrayList<MetricalLpcfgQuantum>();
			if (bestNode2 instanceof MetricalLpcfgTerminal) {
				quantum2 = reduceQuantum(bestNode2.getQuantum());
			} else {
				for (MetricalLpcfgNode node : ((MetricalLpcfgNonterminal) bestNode2).getChildren()) {
					quantum2.addAll(reduceQuantum(node.getQuantum()));
				}
			}
			
			List<MetricalLpcfgQuantum> quantum3 = new ArrayList<MetricalLpcfgQuantum>();
			if (bestNode3 instanceof MetricalLpcfgTerminal) {
				quantum3 = reduceQuantum(bestNode3.getQuantum());
			} else {
				for (MetricalLpcfgNode node : ((MetricalLpcfgNonterminal) bestNode3).getChildren()) {
					quantum3.addAll(reduceQuantum(node.getQuantum()));
				}
			}
			
			best = bestNode2;
			bestTimes = bestTimes2;
			if (quantum3.size() < quantum2.size() || (quantum3.size() == quantum2.size() && measure.getSubBeatsPerBeat() == 3)) {
				// triplet reduces more OR they reduce equally, but triplet is natural for the bar.
				best = bestNode3;
				bestTimes = bestTimes3;
			}
		}
		
		
		prevTimeReturn.set(0, bestTimes.get(bestTimes.size() - 1));
		return best;
	}
	
	/**
	 * Convert the given pattern into reduced form and return it. That is, divide all constituent
	 * lengths by their GCF.
	 * 
	 * @param beatQuantum The pattern we want to reduce.
	 * @return The given pattern in fully reduced form.
	 */
	private static List<MetricalLpcfgQuantum> reduceQuantum(List<MetricalLpcfgQuantum> beatQuantum) {
		int gcf = getGCF(beatQuantum);
		
		List<MetricalLpcfgQuantum> reducedPattern = new ArrayList<MetricalLpcfgQuantum>(beatQuantum.size() / gcf);
		
		// Are we initially in a rest?
		boolean inRest = beatQuantum.get(0) == MetricalLpcfgQuantum.REST;
		int currentLength = 1;
		
		for (int i = 1; i < beatQuantum.size(); i++) {
			switch (beatQuantum.get(i)) {
			case REST:
				if (inRest) {
					// Rest continues
					currentLength++;
					
				} else {
					// New rest
					int reducedLength = currentLength / gcf;
					
					// Add initial symbol (complex in case pattern begins with a TIE)
					reducedPattern.add(reducedPattern.isEmpty() ? beatQuantum.get(0) : MetricalLpcfgQuantum.ONSET);
					
					// Add all ties
					for (int j = 1; j < reducedLength; j++) {
						reducedPattern.add(MetricalLpcfgQuantum.TIE);
					}
					
					inRest = true;
					currentLength = 1;
				}
				break;
				
			case ONSET:
				// New note
				int reducedLength = currentLength / gcf;

				if (inRest) {
					// Add all RESTs
					for (int j = 0; j < reducedLength; j++) {
						reducedPattern.add(MetricalLpcfgQuantum.REST);
					}
					
				} else {
					// Add initial symbol (complex in case pattern begins with a TIE)
					reducedPattern.add(reducedPattern.isEmpty() ? beatQuantum.get(0) : MetricalLpcfgQuantum.ONSET);
				
					// Add all TIEs
					for (int j = 1; j < reducedLength; j++) {
						reducedPattern.add(MetricalLpcfgQuantum.TIE);
					}
				}
				
				currentLength = 1;
				inRest = false;
				break;
				
			case TIE:
				if (inRest) {
					System.err.println("ERROR: TIE after REST - Treating as ONSET");
					
					reducedLength = currentLength / gcf;
					
					for (int j = 0; j < reducedLength; j++) {
						reducedPattern.add(MetricalLpcfgQuantum.REST);
					}
					
					currentLength = 1;
					inRest = false;
					
				} else {
					// Note continues
					currentLength++;
				}
				break;
			}
		}
		
		// Handle final constituent
		int reducedLength = currentLength / gcf;

		if (inRest) {
			// Add all RESTs
			for (int j = 0; j < reducedLength; j++) {
				reducedPattern.add(MetricalLpcfgQuantum.REST);
			}
			
		} else {
			// Add initial symbol (complex in case pattern begins with a TIE)
			reducedPattern.add(reducedPattern.isEmpty() ? beatQuantum.get(0) : MetricalLpcfgQuantum.ONSET);
		
			// Add all TIEs
			for (int j = 1; j < reducedLength; j++) {
				reducedPattern.add(MetricalLpcfgQuantum.TIE);
			}
		}
		
		return reducedPattern;
	}
	
	/**
	 * Get the greatest common factor of the lengths of all of the constituents in the given
	 * pattern.
	 * 
	 * @param beatQuantum The pattern we want to reduce.
	 * @return The greatest common factor of the constituents of the given pattern.
	 */
	private static int getGCF(List<MetricalLpcfgQuantum> beatQuantum) {
		// Find constituent lengths
		List<Integer> lengths = new ArrayList<Integer>();
		lengths.add(1);
		
		int gcf = 0;
		
		// Are we initially in a rest?
		boolean inRest = beatQuantum.get(0) == MetricalLpcfgQuantum.REST;
		
		for (int i = 1; i < beatQuantum.size(); i++) {
			if (gcf == 1) {
				return 1;
			}
			
			switch (beatQuantum.get(i)) {
			case REST:
				if (inRest) {
					// Rest continues
					incrementLast(lengths);
					
				} else {
					// New rest
					inRest = true;
					int lastLength = lengths.get(lengths.size() - 1);
					gcf = gcf == 0 ? lastLength : MathUtils.getGCF(gcf, lastLength);
					lengths.add(1);
				}
				break;
				
			case ONSET:
				// New note
				int lastLength = lengths.get(lengths.size() - 1);
				gcf = gcf == 0 ? lastLength : MathUtils.getGCF(gcf, lastLength);
				lengths.add(1);
				inRest = false;
				break;
				
			case TIE:
				if (inRest) {
					System.err.println("ERROR: TIE after REST - Treating as ONSET");
					lastLength = lengths.get(lengths.size() - 1);
					gcf = gcf == 0 ? lastLength : MathUtils.getGCF(gcf, lastLength);
					lengths.add(1);
					inRest = false;
					
				} else {
					// Note continues
					incrementLast(lengths);
				}
				break;
			}
		}
		
		// Add last constituent (if we already did, it won't affect the gcf anyways)
		int lastLength = lengths.get(lengths.size() - 1);
		gcf = gcf == 0 ? lastLength : MathUtils.getGCF(gcf, lastLength);
		
		return gcf;
	}
	
	/**
	 * Utility method to increment the last value in an Integer List.
	 * 
	 * @param list The Integer List whose last value we want to increment.
	 */
	private static void incrementLast(List<Integer> list) {
		if (list.isEmpty()) {
			return;
		}
		
		list.set(list.size() - 1, list.get(list.size() - 1) + 1);
	}

	/**
	 * 
	 * 
	 * @param anchorBeats
	 * @param numTimes
	 * @param nextTime
	 * @param defaultLength
	 * @return
	 */
	private static List<Long> getAnchoredTimes(List<Beat> anchorBeats, int numTimes, long nextTime, long defaultLength) {
		List<Long> times = new ArrayList<Long>(numTimes);
		
		int numAnchors = anchorBeats.size();
		
		if (numTimes % numAnchors == 0) {
			// Anchors are valid here. Make sure we align with them.
			
			int timesPerAnchor = numTimes / numAnchors;
			
			for (int anchorNum = 0; anchorNum < numAnchors; anchorNum++) {
				long anchorTime = anchorBeats.get(anchorNum).getTime();
				long nextAnchorTime = anchorNum == numAnchors - 1 ? nextTime : anchorBeats.get(anchorNum + 1).getTime();
				
				double diff = nextAnchorTime == Long.MAX_VALUE ? defaultLength : ((double) nextAnchorTime - anchorTime) / timesPerAnchor;
				defaultLength = (long) diff;
				
				for (int i = 0; i < timesPerAnchor; i++) {
					times.add(Math.round(anchorTime + i * diff));
				}
			}
			
		} else {
			// The anchors weren't valid. Just split evenly.
			double diff = nextTime == Long.MAX_VALUE ? defaultLength : ((double) nextTime - anchorBeats.get(0).getTime()) / numTimes;
			
			for (int i = 0; i < numTimes; i++) {
				times.add(Math.round(anchorBeats.get(0).getTime() + i * diff));
			}
		}
		
		return times;
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
	 * @param subBeatLength
	 * 
	 * @return The {@link MetricalLpcfgNonterminal} representing a Beat with the given properties.
	 */
	private static MetricalLpcfgNode makeBeat(long prevTime, List<Long> times, long nextTime,
			List<MidiNote> notes, boolean hasStarted, int subBeatLength) {
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
			Collections.fill(quantums.subList(onsetIndex, Main.EXTEND_NOTES ? quantums.size() : offsetIndex - 1), MetricalLpcfgQuantum.TIE);
		}
		
		// Here, quantums array is full and correct.
		
		// Check if can directly make a terminal
		if (MetricalLpcfgTerminal.reducesToOne(quantums)) {
			beat.addChild(new MetricalLpcfgTerminal(quantums, 1, 1));
			return beat;
		}
		
		// Can't reduce to 1. Make each sub-beat individually.
		int numSubBeats = quantums.size() / subBeatLength;
		for (int subBeatIndex = 0; subBeatIndex < quantums.size(); subBeatIndex += subBeatLength) {
			MetricalLpcfgNonterminal subBeat = new MetricalLpcfgNonterminal(MetricalLpcfgLevel.SUB_BEAT);
			subBeat.addChild(new MetricalLpcfgTerminal(quantums.subList(subBeatIndex, subBeatIndex + subBeatLength), 1, numSubBeats));
			
			beat.addChild(subBeat);
		}
		
		beat.fixChildrenTypes();
		return beat;
	}
}
