package metalign.harmony;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import metalign.harmony.Chord.ChordQuality;

public class ChordTransitionProbabilityTracker {

	/**
	 * The transition matrix, for each ChordQuality, the count of transitions for each other
	 * Quality, relative to the root.
	 */
	private final Map<ChordQuality, List<Integer>> transitionMatrix;
	
	/**
	 * A List of the ChordQualities allowed in this transition matrix.
	 */
	private final List<ChordQuality> qualities;
	
	/**
	 * Create a new ChordTransitionProbabilityTracker with the given ChordQualities.
	 * 
	 * @param qualities A Collection containing the valid ChordQualities we will use.
	 */
	public ChordTransitionProbabilityTracker(Collection<ChordQuality> qualities) {
		this.qualities = new ArrayList<ChordQuality>(new HashSet<ChordQuality>(qualities));
		
		// Remove NO_CHORD (we will handle it explicitly)
		this.qualities.remove(ChordQuality.NO_CHORD);
		
		transitionMatrix = new HashMap<ChordQuality, List<Integer>>();
		for (ChordQuality quality : qualities) {
			List<Integer> counts = new ArrayList<Integer>(12 * qualities.size() + 1);
			for (int i = 0; i < 12 * qualities.size() + 1; i++) {
				counts.add(0);
			}
			
			transitionMatrix.put(quality, counts);
		}
		
		// Add NO_CHORD transition
		List<Integer> counts = new ArrayList<Integer>(12 * qualities.size() + 1);
		for (int i = 0; i < 12 * qualities.size() + 1; i++) {
			counts.add(0);
		}
		
		transitionMatrix.put(ChordQuality.NO_CHORD, counts);
		
		this.qualities.add(ChordQuality.NO_CHORD);
	}
	
	/**
	 * Add a transition from one chord to another. If either input is null, it is treated as a NO_CHORD.
	 * 
	 * @param from The chord to transition from. Null counts as NO_CHORD.
	 * @param to The chord to transition to. Null counts as NO_CHORD.
	 * 
	 * @throws IOException If a given chord quality is not valid according to {@link #qualities}.
	 */
	public void addTransition(Chord from, Chord to) throws IOException {
		// Treat nulls as NO_CHORDs
		if (from == null) {
			from = new Chord(0, 0, -1, ChordQuality.NO_CHORD);
		}
		
		if (to == null) {
			to = new Chord(0, 0, -1, ChordQuality.NO_CHORD);
		}
		
		// 2 NO_CHORDs is really a non-transition
		if (from.quality == ChordQuality.NO_CHORD && to.quality == ChordQuality.NO_CHORD) {
			return;
		}
		
		// Check for invalid qualities
		if ((!qualities.contains(from.quality) && from.quality != ChordQuality.NO_CHORD) ||
				(!qualities.contains(to.quality) && to.quality != ChordQuality.NO_CHORD)) {
			throw new IOException("ERROR: Invalid Chord Quality found: " + from.quality + " or " + to.quality + ".\n" +
						"Valid qualities are: " + qualities + " or NO_CHORD.");
		}
		
		// Add transition
		List<Integer> toList = transitionMatrix.get(from.quality);
		
		int rootDifference = from.quality == ChordQuality.NO_CHORD ? to.root : ((to.root - from.root + 12) % 12);
		if (to.quality == ChordQuality.NO_CHORD) {
			rootDifference = 0;
		}
		
		int index = qualities.indexOf(to.quality) * 12 + rootDifference;
		
		toList.set(index, toList.get(index) + 1);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		for (ChordQuality quality : qualities) {
			List<Integer> toList = transitionMatrix.get(quality);
			
			sb.append(quality).append('\n');
			
			int sum = 0;
			for (int count : toList) {
				sum += count;
			}
			
			// All except NO_CHORD
			for (int toQualityIndex = 0; toQualityIndex < qualities.size() - 1; toQualityIndex++) {
				sb.append(' ').append(qualities.get(toQualityIndex)).append('\n');
				
				for (int i = 0; i < 12; i++) {
					sb.append("  ").append(i).append(": ").append(100.0 * toList.get(12 * toQualityIndex + i) / sum).append('\n');
				}
			}
			
			// NO_CHORD
			sb.append(" NO_CHORD: ").append(100.0 * toList.get(toList.size() - 1) / sum).append('\n');
		}
		
		return sb.toString();
	}
}
