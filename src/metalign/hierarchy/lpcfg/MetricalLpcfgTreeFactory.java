package metalign.hierarchy.lpcfg;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import metalign.Main;
import metalign.hierarchy.Measure;
import metalign.hierarchy.lpcfg.MetricalLpcfgNonterminal.MetricalLpcfgLevel;
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
	 * Make a new tree based on a List of MidiNotes.
	 * 
	 * @param notes A List of the notes which lie within the tree we want.
	 * @param subBeatTimes A List of ALL of the beat times of the current song.
	 * @param measure The measure type for the tree we will make.
	 * @param anacrusisLength The anacrusis length of the current song, measured in sub beats.
	 * @param measureNum The measure number of the tree we want.
	 * @param hasBegun A boolean for whether this voice has begun or not. If it has, and Main.EXTEND_NOTES is true,
	 * we want to fill this tree with ties rather than rests (unless it is totally empty).
	 * 
	 * @return A tree of the given measure type, containing the given notes.
	 */
	public static Set<List<MetricalLpcfgQuantum>> makeQuantumLists(List<MidiNote> notes, List<Integer> previous,
			List<Integer> subBeatTimes, Measure measure, int anacrusisLength, int measureNum, boolean hasBegun, List<List<Integer>> alignments) {
		
		int beatsPerBar = measure.getBeatsPerBar();
		int subBeatsPerBeat = measure.getSubBeatsPerBeat();
		int subBeatsPerBar = beatsPerBar * subBeatsPerBeat;
		
		int firstBeatIndex = subBeatsPerBar * measureNum + anacrusisLength;
		int firstSubBeatNum = firstBeatIndex < 0 ? -firstBeatIndex : 0;
		int lastBeatIndex = firstBeatIndex + subBeatsPerBar;
		firstBeatIndex = Math.max(firstBeatIndex, 0);
		
		// Make quantums for each sub beat
		Set<List<MetricalLpcfgQuantum>> quantumLists = new LinkedHashSet<List<MetricalLpcfgQuantum>>();
		quantumLists.add(new ArrayList<MetricalLpcfgQuantum>());
		
		List<List<Integer>> subBeatSplits = new ArrayList<List<Integer>>();
		subBeatSplits.add(new ArrayList<Integer>());
		
		for (int i = firstBeatIndex; i < lastBeatIndex; i++) {
			quantumLists = makeAllQuantums(previous, quantumLists, subBeatTimes.subList(i, i + 2), notes, alignments,
					subBeatSplits, firstSubBeatNum % subBeatsPerBeat == 0);
		}
		
		// Extend notes
		if (Main.EXTEND_NOTES) {
			for (List<MetricalLpcfgQuantum> barQuantum : quantumLists) {
				boolean firstNonRestFound = false;
				for (int i = 0; i < barQuantum.size(); i++) {
					if (!firstNonRestFound) {
						if (barQuantum.get(i) != MetricalLpcfgQuantum.REST) {
							firstNonRestFound = true;
						}
							
					} else {
						if (barQuantum.get(i) == MetricalLpcfgQuantum.REST) {
							barQuantum.set(i, MetricalLpcfgQuantum.TIE);
						}
					}
				}
			}
		}
		
		return quantumLists;
	}
	
	public static int[] SPLITS = new int[] {3, 4};

	/**
	 * Get all of the possible quantum lists for the given sub beat.
	 * 
	 * @param previous A List of all of the possible previous tatums. THIS WILL BE MODIFIED TO A LIST OF EACH OF
	 * THIS SUB BEAT'S LAST TATUM TIMES.
	 * @param existingQuantums A list of the existing quantums from previous sub beats.
	 * @param edges A list containing the start and end times of this sub beat.
	 * @param notes The notes we will add to this sub beat.
	 * @return A List of length 2*previous.size(), containing for each in previous, a 3-length and a 4-length quantum list.
	 */
	private static Set<List<MetricalLpcfgQuantum>> makeAllQuantums(List<Integer> previous, Set<List<MetricalLpcfgQuantum>> existingQuantums,
			List<Integer> edges, List<MidiNote> notes, List<List<Integer>> alignments, List<List<Integer>> subBeatSplits,
			boolean isFirstSubBeat) {
		List<List<Integer>> tatumLists = new ArrayList<List<Integer>>(2);
		List<List<Integer>> newAlignments = new ArrayList<List<Integer>>();
		List<List<Integer>> newSplits = new ArrayList<List<Integer>>();
		List<List<MetricalLpcfgQuantum>> newQuantums = new ArrayList<List<MetricalLpcfgQuantum>>();
		Set<List<MetricalLpcfgQuantum>> quantumSet = new LinkedHashSet<List<MetricalLpcfgQuantum>>();
		
		List<List<Integer>> tatumsS = new ArrayList<List<Integer>>(SPLITS.length);
		for (int s : SPLITS) {
			tatumsS.add(addTatums(edges, s));
		}
		
		int i = 0;
		for (List<MetricalLpcfgQuantum> existing : existingQuantums) {
			int prev = previous.get(i);
			List<Integer> splits = subBeatSplits.get(i);
			List<Integer> align = alignments.get(i++);
			
			for (List<Integer> tatum : tatumsS) {
				List<Integer> newTatums = new ArrayList<Integer>(tatum.size() + 1);
				newTatums.add(prev);
				newTatums.addAll(tatum);
				tatumLists.add(newTatums);
				List<Integer> newSplit = new ArrayList<Integer>(splits);
				newSplit.add(3);
				newSplits.add(newSplit);
				newAlignments.add(new ArrayList<Integer>(align));
				newQuantums.add(new ArrayList<MetricalLpcfgQuantum>(existing));
			}
		}
		
		previous.clear();
		alignments.clear();
		subBeatSplits.clear();
		
		for (int j = 0; j < tatumLists.size(); j++) {
			List<Integer> tatums = tatumLists.get(j);
			List<Integer> align = newAlignments.get(j);
			List<Integer> split = newSplits.get(j);
			
			if (!isFirstSubBeat && split.size() > 1 && split.get(split.size() - 1) != split.get(split.size() - 2)) {
				continue;
			}
			
			List<MetricalLpcfgQuantum> quantums = new ArrayList<MetricalLpcfgQuantum>(tatums.size() - 2);
			for (i = 0; i < tatums.size() - 2; i++) {
				quantums.add(MetricalLpcfgQuantum.REST);
			}
			
			for (MidiNote note : notes) {
				int onsetTatumIndex = note.getOnsetTatumIndex(tatums);
				int offsetTatumIndex = note.getOffsetTatumIndex(tatums);
				
				boolean started = onsetTatumIndex == 0;
				
				for (i = Math.max(onsetTatumIndex, 1); i < offsetTatumIndex && i < tatums.size() - 1; i++) {
					if (!started) {
						if (onsetTatumIndex == i) {
							quantums.set(i - 1,  MetricalLpcfgQuantum.ONSET);
							align.add((int) (Math.abs(note.getOnsetTime()) - tatums.get(i)));
							started = true;
						}
						
					} else {
						if (quantums.get(i - 1) == MetricalLpcfgQuantum.REST) {
							quantums.set(i - 1, MetricalLpcfgQuantum.TIE);
						}
					}
				}
			}
			
			newQuantums.get(j).addAll(lengthenTo(quantums, 12));
			if (quantumSet.add(newQuantums.get(j))) {
				previous.add(tatums.get(tatums.size() - 2));
				alignments.add(align);
				subBeatSplits.add(split);
			}
		}
		
		return quantumSet;
	}
	
	/**
	 * Lengthen the given quantum array to the given length. Length should be an exact multiple of
	 * the length of the given array. Otherwise, weird things will happen.
	 * 
	 * @param quantums The array we want to lengthen.
	 * @param length The resulting length we want.
	 * @return A new quantums array, where each element is padded with an equal number of rests or ties.
	 */
	public static List<MetricalLpcfgQuantum> lengthenTo(List<MetricalLpcfgQuantum> quantums, int length) {
		int numToAdd = length / quantums.size() - 1;
		List<MetricalLpcfgQuantum> lengthened = new ArrayList<MetricalLpcfgQuantum>(length / quantums.size() * quantums.size());
		
		for (MetricalLpcfgQuantum quantum : quantums) {
			lengthened.add(quantum);
			
			for (int j = 0; j < numToAdd; j++) {
				lengthened.add(quantum == MetricalLpcfgQuantum.REST ? MetricalLpcfgQuantum.REST : MetricalLpcfgQuantum.TIE);
			}
		}
		
		return lengthened;
	}
	
	/**
	 * Lengthen the given quantum array to the given length. Length should be an exact multiple of
	 * the length of the given array. Otherwise, weird things will happen.
	 * 
	 * @param quantums The array we want to lengthen.
	 * @param length The resulting length we want.
	 * @return A new quantums array, where each element is padded with an equal number of rests or ties.
	 */
	public static MetricalLpcfgQuantum[] lengthenTo(MetricalLpcfgQuantum[] quantums, int length) {
		int numToAdd = length / quantums.length - 1;
		MetricalLpcfgQuantum[] lengthened = new MetricalLpcfgQuantum[length / quantums.length * quantums.length];
		
		int i = 0;
		for (MetricalLpcfgQuantum quantum : quantums) {
			lengthened[i++] = quantum;
			
			for (int j = 0; j < numToAdd; j++) {
				lengthened[i++] = quantum == MetricalLpcfgQuantum.REST ? MetricalLpcfgQuantum.REST : MetricalLpcfgQuantum.TIE;
			}
		}
		
		return lengthened;
	}
	
	/**
	 * Add some number of tatums, equally spaced between the 2 given edges.
	 * 
	 * @param edges A List containing the two edges, in index 0 and 1.
	 * @param divisions The number of divisions to split into. We will add divisions-1 new tatums.
	 * @return A new list of tatums, with the new beats added between the edges.
	 */
	private static List<Integer> addTatums(List<Integer> edges, int divisions) {
		List<Integer> tatums = new ArrayList<Integer>(divisions + 1);
		tatums.add(edges.get(0));
		
		double timePerTatum = ((double) (edges.get(1) - edges.get(0))) / divisions;
		
		for (int i = 1; i < divisions; i++) {
			tatums.add((int) Math.round(edges.get(0) + i * timePerTatum));
		}
		
		tatums.add(edges.get(1));
		
		return tatums;
	}

	/**
	 * Make and return a tree from the given quantums with the given structure.
	 * 
	 * @param quantums The quantums which will be contained by this tree, unreduced.
	 * @param beatsPerMeasure The beats per measure which should be in this tree.
	 * @param subBeatsPerBeat The sub beats per beat which should be in this tree.
	 * 
	 * @return A tree generated from the given quantums and measure structure.
	 */
	public static MetricalLpcfgTree makeTree(List<MetricalLpcfgQuantum> quantums, int beatsPerMeasure, int subBeatsPerBeat) {
		MetricalLpcfgMeasure measure = new MetricalLpcfgMeasure(beatsPerMeasure, subBeatsPerBeat);
		
		int beatLength = quantums.size() / beatsPerMeasure;
		
		// Create beat quantum arrays
		for (int beat = 0; beat < quantums.size();) {
			measure.addChild(makeBeatNonterminal(quantums.subList(beat, (beat += beatLength)), subBeatsPerBeat));
		}
		measure.fixChildrenTypes();
		
		return new MetricalLpcfgTree(measure);
	}
	
	/**
	 * Make and return a non-terminal representing a beat.
	 * 
	 * @param beatQuantum The quantums which lie in this non-terminal.
	 * @param subBeatsPerBeat The number of sub beats which lie in this non-terminal.
	 * @return The non-terminal representing the given quantums.
	 */
	private static MetricalLpcfgNonterminal makeBeatNonterminal(List<MetricalLpcfgQuantum> beatQuantum, int subBeatsPerBeat) {
		MetricalLpcfgNonterminal beatNonterminal = new MetricalLpcfgNonterminal(MetricalLpcfgLevel.BEAT);
		
		if (MetricalLpcfgTerminal.reducesToOne(beatQuantum)) {
			beatNonterminal.addChild(new MetricalLpcfgTerminal(beatQuantum, subBeatsPerBeat));
			
		} else {
			// Need to split into sub beats
			int subBeatLength = beatQuantum.size() / subBeatsPerBeat;
			
			// Create sub beat quantum arrays
			for (int subBeat = 0; subBeat < beatQuantum.size();) {
				beatNonterminal.addChild(makeSubBeatNonterminal(beatQuantum.subList(subBeat, (subBeat += subBeatLength))));
			}
			beatNonterminal.fixChildrenTypes();
		}
		
		return beatNonterminal;
	}
	
	/**
	 * Make and return a non-terminal representing a sub beat.
	 * 
	 * @param subBeatQuantum The quantums which lie in this non-terminal.
	 * @return The non-terminal representing the given quantums.
	 */
	private static MetricalLpcfgNonterminal makeSubBeatNonterminal(List<MetricalLpcfgQuantum> subBeatQuantum) {
		MetricalLpcfgNonterminal subBeatNonterminal = new MetricalLpcfgNonterminal(MetricalLpcfgLevel.SUB_BEAT);
		
		subBeatNonterminal.addChild(new MetricalLpcfgTerminal(subBeatQuantum));
		
		return subBeatNonterminal;
	}
}
