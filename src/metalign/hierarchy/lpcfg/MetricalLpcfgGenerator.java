package metalign.hierarchy.lpcfg;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import metalign.Main;
import metalign.beat.Beat;
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
	 * Create a new default MetricalGrammarGenerator.
	 */
	public MetricalLpcfgGenerator() {
		grammar = new MetricalLpcfg();
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
			notes.add(voice.getNotes());
		}
		
		// Go through each voice, creating its trees
		for (List<MidiNote> voice : notes) {
			for (MetricalLpcfgTree tree : parseVoice(voice, beats, downbeatIndices, tt)) {
				grammar.addTree(tree);
			}
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
	 * @return An Iterable of all of the non-empty trees parsed from this voice.
	 */
	private Iterable<MetricalLpcfgTree> parseVoice(List<MidiNote> voice, List<Beat> beats, List<Integer> downbeatIndices, TimeTracker tt) {
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
							trees.add(tree);
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
}
