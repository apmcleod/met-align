package metalign.parsing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.sound.midi.InvalidMidiDataException;

import metalign.Runner;
import metalign.beat.Beat;
import metalign.harmony.Chord;
import metalign.harmony.Chord.ChordQuality;
import metalign.harmony.ChordEmissionProbabilityTracker;
import metalign.harmony.ChordTransitionProbabilityTracker;
import metalign.time.FuncHarmTimeTracker;
import metalign.utils.MidiNote;
import metalign.voice.Voice;
import metalign.voice.VoiceSplittingModel;
import metalign.voice.hmm.HmmVoiceSplittingModel;
import metalign.voice.hmm.HmmVoiceSplittingModelParameters;

/**
 * A <code>FuncHarmParser</code> parses a text file derived from the Beethoven piano sonata dataset from [1].
 * The text file is generated from my python code.
 * <br>
 * [1] Tsung-Ping Chen and Li Su, “Functional Harmony Recognition with Multi-task Recurrent Neural Networks,”
 * International Society of Music Information Retrieval Conference (ISMIR), September 2018.
 * 
 * @author Andrew McLeod
 */
public class FuncHarmParser implements EventParser {
	
	private static final int MICROS_PER_TICK = 500000;
	
	/**
	 * The chord vocabulary reduction.
	 */
	private Map<ChordQuality, ChordQuality> vocab;
	
	/**
	 * The note list generator to send notes to.
	 */
	private final NoteListGenerator nlg;
	
	/**
	 * The time tracker to send beats and downbeats to.
	 */
	private final FuncHarmTimeTracker tt;
	
	/**
	 * The File to parse.
	 */
	private final File funcHarmFile;
	
	/**
	 * The time of the first onset note of the piece.
	 */
	private long firstNoteTime = Long.MAX_VALUE;
	
	/**
	 * The ground truth voices from this piece.
	 */
	private List<List<MidiNote>> groundTruthVoices = null;
	
	/**
	 * Boolean indicating whether the parsed notes included voices (true) or not (false).
	 */
	private boolean includesVoices;
	
	/**
	 * A SortedSet of the chords present in this piece.
	 */
	private SortedSet<Chord> chords;
	
	/**
	 * Create a new parser with the given fields.
	 * 
	 * @param funcHarmFile The File to parse the piece from.
	 * @param tt {@link #tt}
	 * @param nep {@link #nep}
	 */
	public FuncHarmParser(File funcHarmFile, FuncHarmTimeTracker tt, NoteListGenerator nlg) {
		this.funcHarmFile = funcHarmFile;
		this.tt = tt;
		this.nlg = nlg;
		
		includesVoices = false;
		
		chords = new TreeSet<Chord>();
		
		vocab = Chord.DEFAULT_VOCAB_MAP;
	}

	@Override
	public void run() throws InvalidMidiDataException, InterruptedException, IOException {
		BufferedReader br = new BufferedReader(new FileReader(funcHarmFile));
		
		String line;
		while ((line = br.readLine()) != null) {
			if (line.trim().length() == 0) {
				continue;
			}
			
			String[] lineSplit = line.split(" ");
			
			if (lineSplit.length != 2) {
				System.err.println("FuncHarm File error. Lines should be empty, or contain 2 space-separated fields.");
				System.err.println("Skipping line: " + line);
				continue;
			}
			
			switch (lineSplit[0]) {
			case "Beat":
				try {
					tt.addBeat(Math.round(Double.parseDouble(lineSplit[1]) * MICROS_PER_TICK));
				} catch (NumberFormatException e) {
					System.err.println("Beat line malformed. Should be \"Beat time\", but time not given as double.");
					System.err.println("Skipping line: " + line);
				}
				break;
				
			case "Downbeat":
				try {
					tt.addDownbeat(Math.round(Double.parseDouble(lineSplit[1]) * MICROS_PER_TICK));
				} catch (NumberFormatException e) {
					System.err.println("Downbeat line malformed. Should be \"Dowbnbeat time\", but time not given as double.");
					System.err.println("Skipping line: " + line);
				}
				break;
				
			case "Chord":
				try {
					Chord chord = parseChord(lineSplit[1]);
					chords.add(chord);
					
				} catch (IOException e) {
					System.err.println(e.getMessage());
					System.err.println("Skipping line: " + line);
				}
				break;
				
			case "Note":
				try {
					MidiNote note = parseNote(lineSplit[1]);
					firstNoteTime = Long.min(firstNoteTime, note.getOnsetTime());
					nlg.noteOn(note.getPitch(), 100, note.getOnsetTime(), note.getCorrectVoice());
					nlg.noteOff(note.getPitch(), note.getOffsetTime(), note.getCorrectVoice());
				} catch (Exception e) {
					System.err.println(e.getMessage());
					System.err.println("Skipping line: " + line);
				}
				break;
				
			case "Key":
				// Unused
				break;
				
			default:
				System.err.println("Line type not recognized: " + lineSplit[0]);
			}
		}
		
		br.close();
	}
	
	/**
	 * Update the ChordEmissionProbabilityTracker from this parsed piece.
	 * 
	 * @params emit The ChordEmissionProbabilityTracker to update.
	 */
	public void updateChordEmissionProbabilityTracker(ChordEmissionProbabilityTracker emit) {
		List<MidiNote> notes = nlg.getNoteList();
		int noteIndex = 0;
		
		// Fast-forward to first note which onsets at least after the first chord begins
		while (noteIndex < notes.size() && notes.get(noteIndex).getOnsetTime() < chords.first().onsetTime) {
			noteIndex++;
		}
		
		// Go through each chord
		for (Chord chord : chords) {
			
			while (noteIndex < notes.size() && notes.get(noteIndex).getOnsetTime() < chord.offsetTime) {
				// This note is within the current chord
				emit.addNote(chord, notes.get(noteIndex));
				
				noteIndex++;
			}
		}
	}
	
	/**
	 * Update the ChordTransitionProbabilityTracker from this parsed piece.
	 * 
	 * @params trans The ChordTransitionProbabilityTracker to update.
	 */
	public void updateChordTransitionProbabilityTracker(ChordTransitionProbabilityTracker trans) {
		Chord prevChord = null;
		
		for (Chord chord : chords) {
			try {
				trans.addTransition(prevChord, chord);
			} catch (IOException e) {
				System.err.println("Error calculating chord transitions:");
				System.err.println(e.getMessage());
			}
			
			prevChord = chord;
		}
	}
	
	/**
	 * Get a List of the Beats on which there is a chord change in the parsed song.
	 * 
	 * @return A List of the beats on which there is a chord change.
	 */
	public List<Beat> getChordChangeBeats() {
		List<Beat> changes = new ArrayList<Beat>();
		
		List<Beat> beats = tt.getTatums();
		Iterator<Chord> chordIterator = chords.iterator();
		
		// Skip 1st chord (not really a "change")
		chordIterator.next();
		
		// No beats on which to change
		if (beats.isEmpty()) {
			return changes;
		}
		
		int beatNum = 0;
		
		// Add each chord's change
		while (chordIterator.hasNext()) {
			Chord chord = chordIterator.next();
			
			// Find the correct beat
			while (beatNum < beats.size() - 1 && beats.get(beatNum).getTime() < chord.onsetTime) {
				beatNum++;
			}
			
			if (beatNum == 0) {
				// The first beat is already past the chord onset
				changes.add(beats.get(beatNum));
				
			} else if (Math.abs(beats.get(beatNum - 1).getTime() - chord.onsetTime) < Math.abs(beats.get(beatNum).getTime() - chord.onsetTime)) {
				// The closest beat is the previous one
				changes.add(beats.get(beatNum - 1));
				// Rewind the beat pointer
				beatNum--;
				
			} else {
				// The closest beat is the current one
				changes.add(beats.get(beatNum));
			}
		}
		
		return changes;
	}

	@Override
	public List<List<MidiNote>> getGoldStandardVoices() {
		if (groundTruthVoices != null) {
			return groundTruthVoices;
		}
		
		if (includesVoices) {
			// Use given voices
			groundTruthVoices = new ArrayList<List<MidiNote>>();
			
			for (MidiNote note : nlg.getNoteList()) {
				while (groundTruthVoices.size() <= note.getCorrectVoice()) {
					groundTruthVoices.add(new ArrayList<MidiNote>());
				}
				
				groundTruthVoices.get(note.getCorrectVoice()).add(note);
			}
			
			for (List<MidiNote> gT : groundTruthVoices) {
	        	Collections.sort(gT);
	        }
			
		} else {
			// Perform voice separation (once)
			VoiceSplittingModel model = new HmmVoiceSplittingModel(new HmmVoiceSplittingModelParameters(5));
			Runner.performInference(model, nlg);
			
			// Parse results and separate notes into voices (and add to ground truth voice lists)
			List<Voice> voices = model.getHypotheses().first().getVoices();
			groundTruthVoices = new ArrayList<List<MidiNote>>();
			
			for (int i = 0; i < voices.size(); i++) {
				groundTruthVoices.add(voices.get(i).getNotes());
				
				for (MidiNote note : groundTruthVoices.get(i)) {
					note.setCorrectVoice(i);
					note.setGuessedVoice(i);
				}
			}
		}
		
		return groundTruthVoices;
	}

	@Override
	public long getFirstNoteTime() {
		return firstNoteTime;
	}
	
	/**
	 * Create and return a MidiNote from a comma-separated note String.
	 * 
	 * @param noteString The note String, in the format: "pitch,onset,offset[,voice]". pitch is an int, and
	 * onset and offset are doubles.
	 * 
	 * @return The parsed MidiNote.
	 * 
	 * @throws IOException If the note String is somehow malformed.
	 */
	private MidiNote parseNote(String noteString) throws IOException {
		String[] noteSplit = noteString.split(",");
		
		if (noteSplit.length < 3 || noteSplit.length > 4) {
			throw new IOException("Malformed note string. Should have 3 or 4 comma-separated fields: " + noteString);
		}
		
		int pitch;
		try {
			pitch = Integer.parseInt(noteSplit[0]);
		} catch (NumberFormatException e) {
			throw new IOException("Malformed note string. First field should be pitch (int), but is: " + noteSplit[0]);
		}
		
		double onset;
		try {
			onset = Double.parseDouble(noteSplit[1]);
		} catch (NumberFormatException e) {
			throw new IOException("Malformed note string. Second field should be onset time (a float), but is: " + noteSplit[1]);
		}
		
		double offset;
		try {
			offset = Double.parseDouble(noteSplit[2]);
		} catch (NumberFormatException e) {
			throw new IOException("Malformed note string. Third field should be offset time (a float), but is: " + noteSplit[2]);
		}
		
		int voice = 0;
		if (noteSplit.length == 4) {
			includesVoices = true;
			try {
				voice = Integer.parseInt(noteSplit[3]);
			} catch (NumberFormatException e) {
				throw new IOException("Malformed note string. Fourth field (if included) should be voice (an int), but is: " + noteSplit[3]);
			}
		}
		
		MidiNote note = new MidiNote(pitch, 100, Math.round(onset * MICROS_PER_TICK), voice, voice);
		note.close(Math.round(offset * MICROS_PER_TICK));
		
		return note;
	}

	
	/**
	 * Create and return a Chord from a comma-separated chord String.
	 * 
	 * @param chordString The chord String, in the format: "onset,offset,tonic,quality". onset and offset are doubles,
	 * tonic is an int between 0 and 11 (inclusive), and quality is a String.
	 * @return The Chord object from the given String, with its quality reduced given {@link #vocab}.
	 * @throws IOException If the chord String is somehow malformed.
	 */
	private Chord parseChord(String chordString) throws IOException {
		String[] chordSplit = chordString.split(",");
		
		if (chordSplit.length != 4) {
			throw new IOException("Malformed chord string. Should have 4 comma-separated fields: " + chordString);
		}
		
		double onset;
		try {
			onset = Double.parseDouble(chordSplit[0]);
		} catch (NumberFormatException e) {
			throw new IOException("Malformed chord string. First field should be onset time (a float), but is: " + chordSplit[0]);
		}
		
		double offset;
		try {
			offset = Double.parseDouble(chordSplit[1]);
		} catch (NumberFormatException e) {
			throw new IOException("Malformed chord string. Second field should be offset time (a float), but is: " + chordSplit[1]);
		}
		
		int tonic;
		try {
			tonic = Integer.parseInt(chordSplit[2]);
		} catch (NumberFormatException e) {
			throw new IOException("Malformed chord string. Third field should be tonic (int 0-11), but is: " + chordSplit[2]);
		}
		
		if (tonic < 0 || tonic > 11) {
			throw new IOException("Malformed chord string. Third field should be tonic (int 0-11), but is: " + chordSplit[2]);
		}
		
		ChordQuality quality;
		switch (chordSplit[3]) {
		case "M":
			quality = ChordQuality.MAJOR;
			break;
			
		case "m":
			quality = ChordQuality.MINOR;
			break;
			
		case "a":
			quality = ChordQuality.AUGMENTED;
			break;
			
		case "a6":
			quality = ChordQuality.AUGMENTED_6;
			break;
			
		case "d":
			quality = ChordQuality.DIMINISHED;
			break;
			
		case "D7":
			quality = ChordQuality.DOMINANT_7;
			break;
			
		case "M7":
			quality = ChordQuality.MAJOR_7;
			break;
			
		case "m7":
			quality = ChordQuality.MINOR_7;
			break;
			
		case "d7":
			quality = ChordQuality.DIMINISHED_7;
			break;
			
		case "h7":
			quality = ChordQuality.HALF_DIMINISHED_7;
			break;
			
		default:
			throw new IOException("Malformed chord string. Fourth filed (quality) not recognized " + chordSplit[3] + "\n" +
									"Should be one of: M, m, a, d, a6, D7, M7, m7, d7, h7.");
		}
		
		return new Chord(Math.round(onset * MICROS_PER_TICK), Math.round(offset * MICROS_PER_TICK), tonic, vocab.get(quality));
	}
}
