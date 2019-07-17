package metalign.hierarchy.lpcfg;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import metalign.Main;
import metalign.beat.Beat;
import metalign.harmony.ChordTransitionProbabilityTracker;
import metalign.hierarchy.Measure;
import metalign.joint.JointModel;
import metalign.joint.JointModelState;
import metalign.time.TimeTracker;
import metalign.utils.MidiNote;
import metalign.voice.Voice;

/**
 * The <code>MetricalLpcfgGenerator</code> class will generate and calculate probabilities
 * for a {@link MetricalLpcfg}.
 * 
 * @author Andrew McLeod - 23 February, 2016
 */
public class MetricalLpcfgGenerator {

	/**
	 * The grammar which we are building.
	 */
	private final MetricalLpcfg grammar;
	
	/**
	 * The chord tracker (used only with {@link #parseSong(JointModel, TimeTracker, FuncHarmEventParser)}).
	 */
	private final ChordTransitionProbabilityTracker cpt;
	
	/**
	 * Create a new default MetricalGrammarGenerator.
	 */
	public MetricalLpcfgGenerator() {
		grammar = new MetricalLpcfg();
		cpt = new ChordTransitionProbabilityTracker();
	}
	
	/**
	 * Parse a song, given its JointModel (which has already been run), and its TimeTracker.
	 * 
	 * @param jm The JointModel, which has already been run. We will get the voices and beats from here.
	 * @param tt The TimeTracker, which will be used to get gold standard beat divisions.
	 */
	public void parseSong(JointModel jm, TimeTracker tt) {
		JointModelState topHypothesis = jm.getHypotheses().first();
		
		// Get beats and save downbeats
		List<Beat> beats = topHypothesis.getBeatState().getBeats();
		List<Integer> downbeatIndices = new ArrayList<Integer>();
		for (int i = 0; i < beats.size(); i++) {
			if (beats.get(i).isDownbeat()) {
				downbeatIndices.add(i);
			}
		}
		
		// Get notes for each voice
		List<List<MidiNote>> notes = new ArrayList<List<MidiNote>>();
		for (Voice voice : topHypothesis.getVoiceState().getVoices()) {
			
			List<MidiNote> voiceNotes = new ArrayList<MidiNote>();
			notes.add(voiceNotes);
			long prevOnset = -Main.MIN_NOTE_LENGTH;
			
			for (MidiNote note : voice.getNotes()) {
				if (Main.MIN_NOTE_LENGTH == -1 || note.getOnsetTime() - prevOnset >= Main.MIN_NOTE_LENGTH) {
					voiceNotes.add(note);
				}
				prevOnset = note.getOnsetTime();
			}
		}
		
		// Go through each voice, creating its trees
		for (List<MidiNote> voice : notes) {
			for (MetricalLpcfgTree tree : parseVoice(voice, beats, downbeatIndices, tt)) {
				if (tree != null) {
					grammar.addTree(tree);
				}
			}
		}
	}
	
	/**
	 * Parse a song, given its JointModel (which has already been run), and its TimeTracker.
	 * Also, populate {@link #cpt} with chord change probabilities.
	 * 
	 * @param jm The JointModel, which has already been run. We will get the voices and beats from here.
	 * @param tt The TimeTracker, which will be used to get gold standard beat divisions.
	 * @param changes A List of the Beats on which there are chord changes.
	 */
	public void parseSong(JointModel jm, TimeTracker tt, List<Beat> changes) {
		JointModelState topHypothesis = jm.getHypotheses().first();
		
		// Get beats and save downbeats
		List<Beat> beats = topHypothesis.getBeatState().getBeats();
		List<Integer> downbeatIndices = new ArrayList<Integer>();
		for (int i = 0; i < beats.size(); i++) {
			if (beats.get(i).isDownbeat()) {
				downbeatIndices.add(i);
			}
		}
		
		// Get notes for each voice
		List<List<MidiNote>> notes = new ArrayList<List<MidiNote>>();
		for (Voice voice : topHypothesis.getVoiceState().getVoices()) {
			
			List<MidiNote> voiceNotes = new ArrayList<MidiNote>();
			notes.add(voiceNotes);
			long prevOnset = -Main.MIN_NOTE_LENGTH;
			
			for (MidiNote note : voice.getNotes()) {
				if (Main.MIN_NOTE_LENGTH == -1 || note.getOnsetTime() - prevOnset >= Main.MIN_NOTE_LENGTH) {
					voiceNotes.add(note);
				}
				prevOnset = note.getOnsetTime();
			}
		}
		
		// Go through each voice, creating its trees
		List<List<MetricalLpcfgTree>> treesLists = new ArrayList<List<MetricalLpcfgTree>>(notes.size());
		int numBars = 0;
		for (List<MidiNote> voice : notes) {
			treesLists.add(parseVoice(voice, beats, downbeatIndices, tt));
			numBars = Math.max(numBars, treesLists.get(treesLists.size() - 1).size());
		}
		
		// Add trees to grammar and cpt
		for (int bar = beats.get(0).isDownbeat() ? 0 : -1; bar < numBars; bar++) {
			List<MetricalLpcfgTree> trees = new ArrayList<MetricalLpcfgTree>(treesLists.size());
			
			for (int voice = 0; voice < treesLists.size(); voice++) {
				if (bar >= 0 && bar < treesLists.get(voice).size()) {
					MetricalLpcfgTree tree = treesLists.get(voice).get(bar);
					
					if (tree != null) {
						grammar.addTree(tree);
						trees.add(tree);
					}
				}
			}
			
			// Find start and end of changes list
			int start;
			int end;
			for (start = 0; start < changes.size() && changes.get(start).getBar() < bar + 1; start++);
			for (end = start; end < changes.size() && changes.get(end).getBar() == bar + 1; end++);
			
			cpt.addBar(bar == -1 ? tt.getFirstTimeSignature().getMeasure() :
				tt.getTimeSignatureAtTime(beats.get(downbeatIndices.get(bar)).getTime()).getMeasure(),
					trees, changes.subList(start, end));
		}
	}
	
	/**
	 * Get a List of the trees parsed from a single voice.
	 * 
	 * @param voice An ordered list of the notes present in the voice.
	 * @param beats A List of all of the beats in the current piece.
	 * @param downbeatIndices A List of the indices of the Beats in beats which are downbeats.
	 * @param tt The TimeTracker for this piece, used to get the measure type of each bar.
	 * 
	 * @return An Iterable of all of the trees parsed from this voice, 1 per bar, with null if it is
	 * invalid (or shouldn't be added to the grammar for any reason).
	 */
	private List<MetricalLpcfgTree> parseVoice(List<MidiNote> voice, List<Beat> beats, List<Integer> downbeatIndices, TimeTracker tt) {
		List<MetricalLpcfgTree> trees = new ArrayList<MetricalLpcfgTree>(downbeatIndices.size());
		
		ListIterator<MidiNote> offsetIterator = voice.listIterator(); // Note offset times
		
		
		// Time of the last tatum of the previous bar.
		// In a list so Factory.makeTree can edit this value.
		List<Long> prevTime = new ArrayList<Long>(1);
		prevTime.add(Long.MIN_VALUE);
		if (downbeatIndices.get(0) != 0) {
			double downbeatTime = beats.get(downbeatIndices.get(0)).getTime();
			Beat previousBeat = beats.get(downbeatIndices.get(0) - 1);
			
			// Split the time between the first downbeat and the previous beat by divisor.
			int divisor = 1; // Default, is tatum.
			
			if (previousBeat.isSubBeat()) {
				divisor *= Main.SUB_BEAT_LENGTH;	
			}
			
			if (previousBeat.isBeat()) {
				divisor *= tt.getTimeSignatureAtTime(previousBeat.getTime()).getMeasure().getBeatsPerBar();
			}
			
			prevTime.set(0, Math.round(downbeatTime - (downbeatTime - previousBeat.getTime()) / divisor));
		}
		
		
		// Go through each full bar, creating its tree
		for (int barNum = 0; barNum < downbeatIndices.size(); barNum++) {
			trees.add(null);
			
			// Get beats and metrical info
			int startIndex = downbeatIndices.get(barNum);
			int endIndex = barNum < downbeatIndices.size() - 1 ? downbeatIndices.get(barNum + 1) : beats.size();
			List<Beat> barBeats = beats.subList(startIndex, endIndex);
			
			long nextTime = endIndex < beats.size() ? beats.get(endIndex).getTime() : Long.MAX_VALUE;
			
			Measure measure = tt.getTimeSignatureAtTime(barBeats.get(0).getTime()).getMeasure();
			
			// Quick exit check
			if (voice.get(voice.size() - 1).getOffsetTime() <= barBeats.get(0).getTime()) {
				break;
			}
			
			// Get notes
			while (offsetIterator.hasNext() && offsetIterator.next().getOffsetTime() <= prevTime.get(0));
			List<MidiNote> notes = voice.subList(offsetIterator.previousIndex(), voice.size());
			
			// Rewind iterator for next bar
			offsetIterator.previous();
			
			if (!notes.isEmpty()) {
				try {
					MetricalLpcfgTree tree = MetricalLpcfgTreeFactory.makeTree(measure, prevTime, barBeats, nextTime, notes);
			
					if (!tree.isEmpty()) {
						// hasStarted OR startsWithNoteOrTie
						if (voice.get(0).getOnsetTime() < barBeats.get(0).getTime() || !tree.startsWithRest()) {
							// Skip bars with irregular meter. We had to do the calculation to update prevTime.
							if (!tt.getTimeSignatureAtTime(barBeats.get(0).getTime()).isIrregular()) {
								trees.set(trees.size() - 1, tree);
							}
						}
					}
				} catch(MalformedTreeException e) {
					System.err.println("Error creating tree in bar " + barNum + "/" + (downbeatIndices.size() - 1) + ". Skipping: ");
					System.err.println(e.getMessage());
				}
			}
		}
		
		return trees;
	}

	/**
	 * Get the MetricalGrammar we have generated.
	 * 
	 * @return {@link #grammar}
	 */
	public MetricalLpcfg getGrammar() {
		return grammar;
	}

	public ChordTransitionProbabilityTracker getCPT() {
		return cpt;
	}
}
