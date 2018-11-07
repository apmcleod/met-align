package metalign.hierarchy.lpcfg;

import java.util.ArrayList;
import java.util.List;

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
	 * @param list A List of ALL of the beat times of the current song.
	 * @param measure The measure type for the tree we will make.
	 * @param subBeatLength The sub beat length of the tree we will make.
	 * @param anacrusisLengthSubBeats The anacrusis length of the current song, measured in sub beats.
	 * @param measureNum The measure number of the tree we want.
	 * @param hasBegun A boolean for whether this voice has begun or not. If it has, and Main.EXTEND_NOTES is true,
	 * we want to fill this tree with ties rather than rests (unless it is totally empty).
	 * 
	 * @return A tree of the given measure type, containing the given notes.
	 */
	public static MetricalLpcfgTree makeTree(List<MidiNote> notes, List<Integer> list, Measure measure, int subBeatLength, int anacrusisLengthSubBeats, int measureNum, boolean hasBegun) {
		int beatsPerMeasure = measure.getBeatsPerBar();
		int subBeatsPerBeat = measure.getSubBeatsPerBeat();
		
		return makeTree(makeQuantumList(notes, list, measure, subBeatLength, anacrusisLengthSubBeats, measureNum, hasBegun), beatsPerMeasure, subBeatsPerBeat);
	}
	
	/**
	 * Make a new tree based on a List of MidiNotes.
	 * 
	 * @param notes A List of the notes which lie within the tree we want.
	 * @param list A List of ALL of the beat times of the current song.
	 * @param measure The measure type for the tree we will make.
	 * @param subBeatLength The sub beat length of the tree we will make.
	 * @param anacrusisLengthSubBeats The anacrusis length of the current song, measured in sub beats.
	 * @param measureNum The measure number of the tree we want.
	 * @param hasBegun A boolean for whether this voice has begun or not. If it has, and Main.EXTEND_NOTES is true,
	 * we want to fill this tree with ties rather than rests (unless it is totally empty).
	 * 
	 * @return A tree of the given measure type, containing the given notes.
	 */
	public static List<MetricalLpcfgQuantum> makeQuantumList(List<MidiNote> notes, List<Integer> list, Measure measure, int subBeatLength, int anacrusisLengthSubBeats, int measureNum, boolean hasBegun) {
		int beatsPerMeasure = measure.getBeatsPerBar();
		int subBeatsPerBeat = measure.getSubBeatsPerBeat();
		
		int measureLength = subBeatLength * beatsPerMeasure * subBeatsPerBeat;
		int anacrusisLength = subBeatLength * anacrusisLengthSubBeats;
		
		List<MetricalLpcfgQuantum> quantums = new ArrayList<MetricalLpcfgQuantum>(measureLength);
		for (int i = 0; i < measureLength; i++) {
			quantums.add(MetricalLpcfgQuantum.REST);
		}
		
		int firstBeatIndex = measureLength * measureNum + anacrusisLength; 
		int lastBeatIndex = firstBeatIndex + measureLength;
		
		int fromIndex = Math.max(firstBeatIndex - 1, 0);
		int toIndex = Math.min(lastBeatIndex + 1, list.size());
		List<Integer> toSearch = list.subList(fromIndex, toIndex);
		
		for (MidiNote note : notes) {
			addNote(note, quantums, list, toSearch, firstBeatIndex, lastBeatIndex);
		}
		
		// Check for first extend notes
		if (Main.EXTEND_NOTES && !notes.isEmpty()) {
			boolean firstOnsetFound = false;
			for (int i = 0; i < quantums.size(); i++) {
				if (!firstOnsetFound) {
					if (quantums.get(i) == MetricalLpcfgQuantum.ONSET) {
						firstOnsetFound = true;
					}
					
				} else {
					if (quantums.get(i) == MetricalLpcfgQuantum.REST) {
						quantums.set(i, MetricalLpcfgQuantum.TIE);
					}
				}
			}
		}
		
		return quantums;
	}

	/**
	 * Add the given note into the given quantums array. The quantums parameter here is changed
	 * as a result of this call.
	 * 
	 * @param note The note we want to add into our quantums array.
	 * @param quantums The quantums array for tracking the current tree's quantums. This array may be
	 * changed as a result of this call.
	 * @param list A List of ALL of the beats in the current song.
	 * @param firstBeatIndex The index of the beat which represents the first quantum in the quantum array.
	 * @param lastBeatIndex The index of the beat after the last quantum in the quantum array.
	 */
	private static void addNote(MidiNote note, List<MetricalLpcfgQuantum> quantums, List<Integer> list, List<Integer> toSearch, int firstBeatIndex, int lastBeatIndex) {
		int fromIndex = Math.max(firstBeatIndex - 1, 0);
		
		int beatIndex = note.getOnsetBeatIndex(toSearch) + fromIndex;
		int offsetBeatIndex = Math.min(note.getOffsetBeatIndex(toSearch) + fromIndex, list.size());
		
		// Add onset
		if (beatIndex >= firstBeatIndex && beatIndex < lastBeatIndex) {
			addQuantum(MetricalLpcfgQuantum.ONSET, quantums, beatIndex - firstBeatIndex);
		}
		
		// Add ties
		beatIndex = Math.max(beatIndex + 1, firstBeatIndex);
		while (beatIndex < lastBeatIndex && beatIndex < offsetBeatIndex) {
			addQuantum(MetricalLpcfgQuantum.TIE, quantums, beatIndex - firstBeatIndex);
			beatIndex++;
		}
	}

	/**
	 * Add the given quantum into the given index of the given quantums array, if the type overrides that index's
	 * current value. That is, if the current value is not already an ONSET. The quantums array may be changed
	 * as a result of this call.
	 * 
	 * @param quantum The quantum we want to add to the quantums array.
	 * @param quantums The quantums array. This array may be changed as a result of this call.
	 * @param index The index at which we want to try to insert the given quantum.
	 */
	private static void addQuantum(MetricalLpcfgQuantum quantum, List<MetricalLpcfgQuantum> quantums, int index) {
		// Update value if not ONSET. (We don't want a TIE to overwrite an ONSET)
		if (quantums.get(index) != MetricalLpcfgQuantum.ONSET) {
			quantums.set(index, quantum);
		}
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
		List<List<MetricalLpcfgQuantum>> beatQuantums = new ArrayList<List<MetricalLpcfgQuantum>>(beatsPerMeasure);
		for (int beat = 0; beat < beatsPerMeasure; beat++) {
			beatQuantums.add(quantums.subList(beatLength * beat, beatLength * (beat + 1)));
		}
		
		// Create beat nodes
		for (List<MetricalLpcfgQuantum> beatQuantum : beatQuantums) {
			measure.addChild(makeBeatNonterminal(beatQuantum, subBeatsPerBeat));
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
		
		MetricalLpcfgTerminal beatTerminal = new MetricalLpcfgTerminal(beatQuantum, subBeatsPerBeat);
		if (beatTerminal.reducesToOne()) {
			beatNonterminal.addChild(beatTerminal);
			
		} else {
			// Need to split into sub beats
			int subBeatLength = beatQuantum.size() / subBeatsPerBeat;
			
			// Create sub beat quantum arrays
			List<List<MetricalLpcfgQuantum>> subBeatQuantums = new ArrayList<List<MetricalLpcfgQuantum>>(subBeatsPerBeat);
			for (int subBeat = 0; subBeat < subBeatsPerBeat; subBeat++) {
				subBeatQuantums.add(beatQuantum.subList(subBeatLength * subBeat, subBeatLength * (subBeat + 1)));
			}
			
			// Create sub beat nodes
			for (List<MetricalLpcfgQuantum> subBeatQuantum : subBeatQuantums) {
				beatNonterminal.addChild(makeSubBeatNonterminal(subBeatQuantum));
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
