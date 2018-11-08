package metalign.hierarchy.lpcfg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import metalign.Main;
import metalign.beat.Beat;
import metalign.joint.JointModel;
import metalign.time.NoteBTimeTracker;
import metalign.time.TimeSignature;
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
		if (tt.getFirstTimeSignature().getNumerator() == TimeSignature.IRREGULAR_NUMERATOR) {
			// We don't want to parse an irregular time signature song.
			return;
		}
		
		// Grab beats from the JointModel
		List<Beat> beats = jm.getBeatHypotheses().get(0).getBeats();
		
		// Go through actual voices and notes
		for (Voice voice : jm.getVoiceHypotheses().get(0).getVoices()) {
			MetricalLpcfgQuantum[][] quantums = new MetricalLpcfgQuantum[beats.size()][];
			
			List<List<MidiNote>> notesBySubBeat = new ArrayList<List<MidiNote>>(beats.size());
			for (int i = 0; i < beats.size(); i++) {
				notesBySubBeat.add(new ArrayList<MidiNote>());
			}
			
			// Add notes to sub beats
			List<MidiNote> notes = voice.getNotes();
			for (int i = 0; i < notes.size(); i++) {
				MidiNote note = notes.get(i);
				MidiNote previous = i == 0 ? null : notes.get(i - 1);
				
				if (previous == null || Main.MIN_NOTE_LENGTH == -1 || note.getOnsetTime() - previous.getOnsetTime() >= Main.MIN_NOTE_LENGTH) {
					addNote(note, beats, notesBySubBeat);
				}
			}
			
			// Make quantums for each sub beat
			List<Beat> previous = new ArrayList<Beat>(1);
			previous.add(new Beat(0, 0, 0, 0, Long.MIN_VALUE, Long.MIN_VALUE));
			for (int i = 0; i < quantums.length - 1; i++) {
				quantums[i] = makeQuantum(previous, beats.subList(i, i + 2), notesBySubBeat.get(i));
			}
			
			// TODO: Combine sub beats into bars
			
			
			// Save valid measures
			boolean hasBegun = false;
			for (int i = 0; i < numMeasures; i++) {
				if (quantums[i] != null) {
					if (Main.EXTEND_NOTES) {
						boolean firstOnsetFound = false;
						for (int j = 0; j < quantums[i].length; j++) {
							if (!firstOnsetFound) {
								if (quantums[i][j] == MetricalLpcfgQuantum.ONSET) {
									firstOnsetFound = true;
								}
									
							} else {
								if (quantums[i][j] == MetricalLpcfgQuantum.REST) {
									quantums[i][j] = MetricalLpcfgQuantum.TIE;
								}
							}
						}
					}
					
					MetricalLpcfgTree tree = MetricalLpcfgTreeFactory.makeTree(Arrays.asList(quantums[i]), beatsPerMeasure[i], subBeatsPerBeat[i]);
					if (!hasBegun) {
						hasBegun = true;
						
						if (tree.startsWithRest()) {
							// Skip anacrusis measure
							continue;
						}
					}
					grammar.addTree(tree);
				}
			}
		}
	}
	
	private MetricalLpcfgQuantum[] makeQuantum(List<Beat> previous, List<Beat> edges, List<MidiNote> notes) {
		List<Beat> tatums3 = addTatums(edges, 3);
		List<Beat> tatums4 = addTatums(edges, 4);
		
		double distance3 = getDistance(previous, tatums3, notes);
		double distance4 = getDistance(previous, tatums4, notes);
		
		List<Beat> tatums = distance3 < distance4 ? tatums3 : tatums4;
		tatums.add(0, previous.get(0));
		
		previous.set(0, tatums.get(tatums.size() - 2));
		
		MetricalLpcfgQuantum[] quantums = new MetricalLpcfgQuantum[tatums.size() - 1];
		Arrays.fill(quantums, MetricalLpcfgQuantum.REST);
		
		for (MidiNote note : notes) {
			Beat onsetTatum = note.getOnsetTatum(tatums);
			Beat offsetTatum = note.getOffsetTatum(tatums);
			
			boolean started = onsetTatum.equals(tatums.get(0));
			
			for (int i = 1; i < tatums.size() - 1; i++) {
				if (!started) {
					if (onsetTatum.equals(tatums.get(i))) {
						quantums[i] = MetricalLpcfgQuantum.ONSET;
						started = true;
					}
					
				} else {
					if (offsetTatum.equals(tatums.get(i))) {
						break;
					}
					
					if (quantums[i] == MetricalLpcfgQuantum.REST) {
						quantums[i] = MetricalLpcfgQuantum.TIE;
					}
				}
			}
		}
		
		return quantums;
	}

	private double getDistance(List<Beat> previous, List<Beat> tatums, List<MidiNote> notes) {
		double distancePerNote = 0.0;
		int noteCount = 0;
		
		for (MidiNote note : notes) {
			double minDistance = Double.POSITIVE_INFINITY;
			
			for (int i = 1; i < tatums.size(); i++) {
				double distance = Math.abs(tatums.get(i).getTime() - note.getOnsetTime());
				
				minDistance = Math.min(distance, minDistance);
			}
			
			if (minDistance < Math.abs(previous.get(0).getTime() - note.getOnsetTime())) {
				distancePerNote += minDistance;
				noteCount++;
			}
		}
		
		return distancePerNote / noteCount;
	}
	
	private List<Beat> addTatums(List<Beat> edges, int divisions) {
		List<Beat> tatums = new ArrayList<Beat>(divisions + 1);
		tatums.add(edges.get(0));
		
		double timePerTatum = ((double) (edges.get(1).getTime() - edges.get(0).getTime())) / divisions;
		double ticksPerTatum = ((double) (edges.get(1).getTick() - edges.get(0).getTick())) / divisions;
		
		for (int i = 1; i < divisions; i++) {
			tatums.add(new Beat(edges.get(0).getBar(), edges.get(0).getBeat(), edges.get(0).getSubBeat(), i,
					Math.round(edges.get(0).getTime() + divisions * timePerTatum),
					Math.round(edges.get(0).getTick() + divisions * ticksPerTatum)));
		}
		
		tatums.add(edges.get(1));
		
		return tatums;
	}

	/**
	 * Add the given note into the correct sub beat list.
	 * 
	 * @param note The note we want to add into the tracking arrays.
	 * @param beats The beats of the song we are parsing.
	 * @param notesBySubBeat The note tracking list, divided by sub beat.
	 */
	private void addNote(MidiNote note, List<Beat> beats, List<List<MidiNote>> notesBySubBeat) {
		Beat onsetBeat = note.getOnsetSubBeatIndex(beats);
		Beat offsetBeat = note.getOffsetSubBeat(beats);
		
		// Iterate to onset beat
		int i;
		boolean started = false;
		for (i = 0; i < beats.size(); i++) {
			if (beats.get(i).equals(onsetBeat)) {
				started = true;
			}
			
			if (started) {
				if (beats.get(i).equals(offsetBeat)) {
					break;
				}
				
				notesBySubBeat.get(i).add(note);
			}
		}
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
