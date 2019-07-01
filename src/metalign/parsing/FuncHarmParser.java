package metalign.parsing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;

import metalign.harmony.Chord;
import metalign.harmony.Chord.ChordQuality;
import metalign.time.FuncHarmTimeTracker;
import metalign.utils.MidiNote;

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
	
	/**
	 * The note event parser to send notes to.
	 */
	private final NoteEventParser nep;
	
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
	 * Create a new parser with the given fields.
	 * 
	 * @param funcHarmFile The File to parse the piece from.
	 * @param tt {@link #tt}
	 * @param nep {@link #nep}
	 */
	public FuncHarmParser(File funcHarmFile, FuncHarmTimeTracker tt, NoteEventParser nep) {
		this.funcHarmFile = funcHarmFile;
		this.tt = tt;
		this.nep = nep;
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
					tt.addBeat(Math.round(Double.parseDouble(lineSplit[1]) * 1000000));
				} catch (NumberFormatException e) {
					System.err.println("Beat line malformed. Should be \"Beat time\", but time not given as double.");
					System.err.println("Skipping line: " + line);
				}
				break;
				
			case "Downbeat":
				try {
					tt.addDownbeat(Math.round(Double.parseDouble(lineSplit[1]) * 1000000));
				} catch (NumberFormatException e) {
					System.err.println("Downbeat line malformed. Should be \"Dowbnbeat time\", but time not given as double.");
					System.err.println("Skipping line: " + line);
				}
				break;
				
			case "Chord":
				try {
					Chord chord = parseChord(lineSplit[1]);
				} catch (IOException e) {
					System.err.println(e.getMessage());
					System.err.println("Skipping line: " + line);
				}
				break;
				
			case "Note":
				try {
					MidiNote note = parseNote(lineSplit[1]);
					firstNoteTime = Long.min(firstNoteTime, note.getOnsetTime());
					nep.noteOn(note.getPitch(), 100, note.getOnsetTime(), 0);
					nep.noteOff(note.getPitch(), note.getOffsetTime(), 0);
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

	@Override
	public List<List<MidiNote>> getGoldStandardVoices() {
		if (groundTruthVoices != null) {
			return groundTruthVoices;
		}
		
		// TODO Run voice separation, default non-live settings
		return null;
	}

	@Override
	public long getFirstNoteTime() {
		return firstNoteTime;
	}
	
	/**
	 * Create and return a MidiNote from a comma-separated note String.
	 * 
	 * @param noteString The note String, in the format: "pitch,onset,offset". pitch is an int, and
	 * onset and offset are doubles.
	 * 
	 * @return The parsed MidiNote.
	 * 
	 * @throws IOException If the note String is somehow malformed.
	 */
	private static MidiNote parseNote(String noteString) throws IOException {
		String[] noteSplit = noteString.split(",");
		
		// pitch, onset, offset
		
		if (noteSplit.length != 3) {
			throw new IOException("Malformed note string. Should have 3 comma-separated fields: " + noteString);
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
		
		MidiNote note = new MidiNote(pitch, 100, Math.round(onset * 1000000), 0, 0);
		note.close(Math.round(offset * 1000000));
		
		return note;
	}

	
	/**
	 * Create and return a Chord from a comma-separated chord String.
	 * 
	 * @param chordString The chord String, in the format: "onset,offset,tonic,quality". onset and offset are doubles,
	 * tonic is an int between -1 and 11 (inclusive), and quality is a String.
	 * @return The Chord object from the given String.
	 * @throws IOException If the chord String is somehow malformed.
	 */
	private static Chord parseChord(String chordString) throws IOException {
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
			throw new IOException("Malformed chord string. Third field should be tonic (int (-1)-11), but is: " + chordSplit[2]);
		}
		
		if (tonic < 0 || tonic > 11) {
			throw new IOException("Malformed chord string. Third field should be tonic (int (-1)-11), but is: " + chordSplit[2]);
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
									"Should be one of: M, m.");
		}
		
		// TODO: Reduce chord quality down to some vocabulary
		
		return new Chord(Math.round(onset * 1000000), Math.round(offset * 1000000), tonic, quality);
	}
}
