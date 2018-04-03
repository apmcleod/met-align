package metalign.hierarchy.lpcfg;

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
	 * The number of beats per measure, used in the {@link #parseSong(JointModel, TimeTracker)} method.
	 */
	private int beatsPerMeasure;
	
	/**
	 * The number of subBeats per beat, used in the {@link #parseSong(JointModel, TimeTracker)} method.
	 */
	private int subBeatsPerBeat;
	
	/**
	 * The number of 32nd notes per measure, used in the {@link #parseSong(JointModel, TimeTracker)} method.
	 */
	private int notes32PerMeasure;
	
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
		
		int measureOffset = beats.get(0).getBar();
		int numMeasures = beats.get(beats.size() - 1).getBar() - measureOffset + 1;
		
		// Generate beats per measure and sub beats per beat arrays
		int[] beatsPerMeasure = new int[numMeasures];
		int[] subBeatsPerBeat = new int[numMeasures];
		int[] notes32PerMeasure = new int[numMeasures];
		
		for (int i = 0; i < beats.size(); i += this.notes32PerMeasure - beats.get(i).getTatum()) {
			Beat beat = beats.get(i);
			
			updateTimeSignature(tt instanceof NoteBTimeTracker ? tt.getFirstTimeSignature() : tt.getNodeAtTime(beat.getTime()).getTimeSignature());
			if (tt.getSubBeatLength() >= 0) {
				this.notes32PerMeasure = tt.getSubBeatLength() * this.beatsPerMeasure * this.subBeatsPerBeat;
			}
			
			int measure = beat.getBar() - measureOffset;
			beatsPerMeasure[measure] = this.beatsPerMeasure;
			subBeatsPerBeat[measure] = this.subBeatsPerBeat;
			notes32PerMeasure[measure] = this.notes32PerMeasure;
		}
		
		// Go through actual voices and notes
		for (Voice voice : jm.getVoiceHypotheses().get(0).getVoices()) {
			MetricalLpcfgQuantum[][] quantums = new MetricalLpcfgQuantum[numMeasures][];
			
			// Handle notes
			List<MidiNote> notes = voice.getNotes();
			for (int i = 0; i < notes.size(); i++) {
				MidiNote note = notes.get(i);
				MidiNote previous = i == 0 ? null : notes.get(i - 1);
				
				if (previous == null || Main.MIN_NOTE_LENGTH == -1 || note.getOnsetTime() - previous.getOnsetTime() >= Main.MIN_NOTE_LENGTH) {
					addNote(note, beats, quantums, notes32PerMeasure);
				}
			}
			
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
	
	/**
	 * Add the given note into our tracking arrays.
	 * 
	 * @param note The note we want to add into the tracking arrays.
	 * @param beats The beats of the song we are parsing.
	 * @param quantums The quantums tracking array, indexed first by measure and then by quantum.
	 * @param notes32PerMeasure The number of quantums per measure for each measure.
	 */
	private void addNote(MidiNote note, List<Beat> beats, MetricalLpcfgQuantum[][] quantums,
			int[] notes32PerMeasure) {
		
		int measureOffset = beats.get(0).getBar();
		
		Beat onsetBeat = note.getOnsetBeat(beats);
		Beat offsetBeat = note.getOffsetBeat(beats);
		
		// Iterate to onset beat
		Iterator<Beat> beatIterator = beats.iterator();
		Beat beat = beatIterator.next();
		while (!beat.equals(onsetBeat)) {
			beat = beatIterator.next();
		}
		
		// Add onset
		int measure = beat.getBar() - measureOffset;
		addQuantum(MetricalLpcfgQuantum.ONSET, quantums, measure, beat.getTatum(), notes32PerMeasure[measure]);
		
		// Add ties
		while (!beat.equals(offsetBeat)) {
			addQuantum(MetricalLpcfgQuantum.TIE, quantums, measure, beat.getTatum(), notes32PerMeasure[measure]);
			
			beat = beatIterator.next();
			if (beat.getTatum() == 0) {
				measure++;
			}
		}
	}

	/**
	 * Add the given quantum into the quantums array.
	 * 
	 * @param quantum The quantum we want to add into the array. Should be either ONSET or TIE.
	 * @param quantums The quantums tracking array, indexed first by measure and then by quantum.
	 * @param measure The measure at which we want to add the given quantum.
	 * @param beat The beat at which we want to add the given quantum.
	 * @param notes32PerMeasure The number of quantums in the given measure of the song.
	 */
	private void addQuantum(MetricalLpcfgQuantum quantum, MetricalLpcfgQuantum[][] quantums, int measure, int beat,
			int notes32PerMeasure) {
		
		// Create new quantum list if needed
		if (quantums[measure] == null) {
			quantums[measure] = new MetricalLpcfgQuantum[notes32PerMeasure];
			Arrays.fill(quantums[measure], MetricalLpcfgQuantum.REST);
		}
		
		// Update value if not ONSET. (We don't want a TIE to overwrite an ONSET)
		if (quantums[measure][beat] != MetricalLpcfgQuantum.ONSET) {
			quantums[measure][beat] = quantum;
		}
	}

	/**
	 * Update the time signature tracking fields based on the given TimeSignature. The fields updated
	 * are {@link #beatsPerMeasure}, {@link #subBeatsPerBeat}, and {@link #notes32PerMeasure}.
	 * 
	 * @param timeSig The new TimeSignature we will use.
	 */
	private void updateTimeSignature(TimeSignature timeSig) {
		int numerator = timeSig.getNumerator();
		
		// Assume simple
		beatsPerMeasure = numerator;
		subBeatsPerBeat = 2;
		
		// Check for compound
		if (numerator > 3 && numerator % 3 == 0) {
			beatsPerMeasure = numerator / 3;
			subBeatsPerBeat = 3;	
		}
		
		notes32PerMeasure = timeSig.getNotes32PerBar();
	}
	
	/**
	 * Get the current terminal length.
	 * 
	 * @return The current terminal length.
	 */
	public int getTerminalLength() {
		return notes32PerMeasure / beatsPerMeasure / subBeatsPerBeat;
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
