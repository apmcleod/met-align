package metalign.parsing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sound.midi.InvalidMidiDataException;

import metalign.time.MidiTimeTracker;
import metalign.time.TimeSignature;
import metalign.utils.MidiNote;

/**
 * A <code>KernEventParser</code> parses notes in from a kern file.
 * <br>
 * One KernEventParser is required per kern file.
 * 
 * @author Andrew McLeod - 5 Sept, 2016
 *
 */
public class KernEventParser implements EventParser {
	
	/**
	 * The duration of a whole note, in ticks.
	 */
	public static final int WHOLE_NOTE_DURATION_TICKS = 960;
	
	/**
	 * A Regex pattern to grab a note duration from a **kern file.
	 * <br>
	 * Group 1 will be the number duration, and group 2 will be any dots.
	 */
	private static final Pattern noteDurationPattern = Pattern.compile("([0-9]+)(\\.*)");
	
	/**
	 * A Regex pattern to grab a note pitch from a **kern file.
	 * <br>
	 * Group 1 will be the pitch, and group 2 will be the accidentals.
	 */
	private static final Pattern notePitchPattern = Pattern.compile("(a+|b+|c+|d+|e+|f+|g+)(-+|#*)", Pattern.CASE_INSENSITIVE);
	
	/**
	 * The TimeTracker which will handle timing information for this song.
	 */
	private MidiTimeTracker timeTracker;
	
	/**
	 * The NoteTracker which will keep track of the notes for this song.
	 */
	private final NoteEventParser noteEventParser;
	
	/**
	 * The song we are parsing.
	 */
	private final File song;
	
	/**
	 * The gold standard voices from this song.
	 */
	private List<List<MidiNote>> goldStandard;
	
	/**
	 * The first note time.
	 */
	private long firstNoteTime;

	/**
	 * Creates a new KernEventParser
	 * 
	 * @param kernFile The kern file we will parse.
	 * @param noteEventParser The NoteEventParser to pass events to when we run this parser.
	 * @param timeTracker {@link #timeTracker}
	 */
	public KernEventParser(File kernFile, NoteEventParser noteEventParser, MidiTimeTracker timeTracker) {
		this.timeTracker = timeTracker;
		this.noteEventParser = noteEventParser;
		song = kernFile;
		
		timeTracker.setPPQ(WHOLE_NOTE_DURATION_TICKS / 4);
		
		goldStandard = new ArrayList<List<MidiNote>>();
		
		firstNoteTime = Long.MAX_VALUE;
	}
	
	/**
	 * Parse the loaded file to the loaded NoteEventParser.
	 */
	@Override
	public void run() throws InterruptedException, IOException {
		BufferedReader br = new BufferedReader(new FileReader(song));

		String line;
		int lineNum = 0;
		int numVoices = 0;
		int barNum = 0;
		long lastTick = 0;
		long[] voiceTicks = null;
		boolean isKern = false;
		while ((line = br.readLine()) != null) {
			
			// Parse a line of the kern file
			lineNum++;
			
			// Check for comment
			if (line.startsWith("!!")) {
				// Global comment
				
			
			// Check for valid kern file
			} else if (!isKern) {
				if (!line.equals("**kern")) {
					br.close();
					throw new IOException("Invalid kern format. First uncommented line should be \"**kern\".");
				}
				
				isKern = true;
			
				
			// Check for barline
			} else if (line.startsWith("=")) {
				barNum++;
				if (barNum == 1) {
					// First bar
					timeTracker.setAnacrusis((int) voiceTicks[0]);
				}
			
			// If not global comment, **kern, or barline, MUST be split into voices
			} else {
				String[] voices = line.split("\t");
				if (numVoices == 0) {
					// First split
					numVoices = voices.length;
					voiceTicks = new long[numVoices];
					for (int i = 0; i < numVoices; i++) {
						goldStandard.add(new ArrayList<MidiNote>());
					}
				}
				
				// Check for expected number of voices
				if (voices.length != numVoices) {
					br.close();
					throw new IOException("Unexpected number of voices on line " + lineNum + ". Expected=" + numVoices + " Found=" + voices.length);
				}
				
				for (int voiceNum = 0; voiceNum < numVoices; voiceNum++) {
					// Go through each voice
					String voice = voices[voiceNum];
					
					if (voice.length() == 0) {
						br.close();
						throw new IOException("Empty voice data found on line " + lineNum + ".");
					}
					
					if (voice.equals(".")) {
						continue;
					}
					
					switch (voice.charAt(0)) {
						case '*':
							handleIndicator(voice, lineNum);
							break;
							
						case '!':
							// Comment
							break;
							
						default:
							// Note or rest
							int duration = getDuration(voice, lineNum);
							int pitch = getPitch(voice, lineNum);
							
							if (pitch == -1) {
								// Rest
								voiceTicks[voiceNum] += duration;
								
							} else {
								if (!voice.contains("_") && !voice.contains("]")) {
									// Need an onset event
									MidiNote note = noteEventParser.noteOn(pitch, 100, voiceTicks[voiceNum], voiceNum);
									while (goldStandard.size() <= voiceNum) {
		                        		goldStandard.add(new ArrayList<MidiNote>());
		                        	}
		                        	goldStandard.get(voiceNum).add(note);
		                        	
		                        	if (note.getOnsetTime() < firstNoteTime) {
		                        		firstNoteTime = note.getOnsetTime();
		                        	}
								}
								
								voiceTicks[voiceNum] += duration;
								
								if (!voice.contains("_") && !voice.contains("[")) {
									// Need an offset event
									try {
										noteEventParser.noteOff(pitch, voiceTicks[voiceNum], voiceNum);
										
									} catch (InvalidMidiDataException e) {
										throw new IOException("No onset detected for offset " + voice + " on line " + lineNum + ".");
									}
								}
							}
							lastTick = Math.max(lastTick, voiceTicks[voiceNum]);
					}
				}
			}
		}
		
		timeTracker.setLastTick(lastTick);
		br.close();
	}
	
	/**
	 * Get the duration, in ticks, of the given note or rest marker.
	 * 
	 * @param voice A note or rest marker.
	 * @param lineNum The line number the note or rest marker came from.
	 * @return The duration of the given marker in ticks.
	 * 
	 * @throws IOException If no duration is found. 
	 */
	private int getDuration(String voice, int lineNum) throws IOException {
		Matcher durationMatcher = noteDurationPattern.matcher(voice);
		
		if (durationMatcher.find()) {
			return getDuration(Integer.parseInt(durationMatcher.group(1)), durationMatcher.group(2).length(), lineNum);
		}
		
		throw new IOException("No duration found for token " + voice + " on line " + lineNum + ".");
	}
	
	/**
	 * Get the length in ticks of a note or rest with the given value and dots.
	 * 
	 * @param length The value of the note, in **kern format. 1 = whole note, 2 = half note, etc.
	 * @param dotCount The number of dots after the note.
	 * @param lineNum The line number this duration was found on.
	 * 
	 * @return The length of the note in ticks.
	 */
	private int getDuration(int length, int dotCount, int lineNum) {
		double duration = length == 0 ? 2.0 * WHOLE_NOTE_DURATION_TICKS : ((double) WHOLE_NOTE_DURATION_TICKS) / length;
		
		double dotBonus = 0.0;
		for (int i = 0, denominator = 2; i < dotCount; i++, denominator *= 2) {
			dotBonus += duration / denominator;
		}
		
		return (int) Math.round(dotBonus + duration);
	}

	/**
	 * Get the pitch of the given note or rest marker. A rest will return -1.
	 * 
	 * @param voice A note or rest marker.
	 * @param lineNum The line number the note or rest marker came from.
	 * @return The pitch of the given note, or -1 if it is a rest.
	 * @throws IOException If no rest or pitch is found.
	 */
	private int getPitch(String voice, int lineNum) throws IOException {
		if (voice.contains("r")) {
			// Rest
			return -1;
		}
		
		Matcher pitchMatcher = notePitchPattern.matcher(voice);
		
		if (pitchMatcher.find()) {
			String pitch = pitchMatcher.group(1);
			
			int accidentalDifference = pitchMatcher.group(2).length();
			if (accidentalDifference > 0 && pitchMatcher.group(2).charAt(0) == '-') {
				accidentalDifference = -accidentalDifference;
			}
			
			return getPitch(pitch) + accidentalDifference;
		}
		
		throw new IOException("No pitch or rest found for token " + voice + " on line " + lineNum + ".");
	}

	/**
	 * Get the pitch number of the given note data.
	 * 
	 * @param pitch The **kern pitch String.
	 * @return The pitch number, where 60 is C4.
	 */
	private int getPitch(String pitch) {
		int pitchNum = 60 + getOffsetAboveCFromChar(pitch.charAt(0));
		
		int octaveDifference = 12;
		if (Character.isUpperCase(pitch.charAt(0))) {
			octaveDifference = -12;
			pitchNum -= 12;
		}
		
		return pitchNum + octaveDifference * (pitch.length() - 1);
	}

	/**
	 * Handle the given indicator read from the **kern file.
	 * 
	 * @param indicator The indicator we need to parse.
	 * @param lineNum The line number the indicator came from.
	 * 
	 * @throws IOException If some error occurred in parsing the indicator.
	 */
	private void handleIndicator(String indicator, int lineNum) throws IOException {
		// Indicator
		if (indicator.startsWith("*MM")) {
			// Tempo marker
			int qpm;
			try {
				qpm = Integer.parseInt(indicator.substring(3));
				
			} catch (NumberFormatException e) {
				throw new IOException("Tempo \"" + indicator + "\" not recognized on line " + lineNum + ".");
			}
			
			// Valid tempo found
			timeTracker.addTempoChange(0L, qpm);
			
		} else if (indicator.startsWith("*M")) {
			// Time signature marker
			
			if (indicator.equals("*MX")) {
				// Irregular time signature
				timeTracker.addTimeSignatureChange(0L, TimeSignature.IRREGULAR_NUMERATOR, 4);
				
			} else if (indicator.equals("*MZ")) {
				// Mixed meter (treat as irregular)
				timeTracker.addTimeSignatureChange(0L, TimeSignature.IRREGULAR_NUMERATOR, 4);
				
			} else if (indicator.startsWith("*MFREI")) {
				// Free meter (treat as irregular)
				timeTracker.addTimeSignatureChange(0L, TimeSignature.IRREGULAR_NUMERATOR, 4);
				
			} else {
				String[] timeSig = indicator.substring(2).split("/");
			
				if (timeSig.length != 2) {
					throw new IOException("Time signature \"" + indicator + "\" not recognized on line " + lineNum + ".");
				}
				
				int numerator, denominator;
				try {
					numerator = Integer.parseInt(timeSig[0]);
					denominator = Integer.parseInt(timeSig[1]);
					
				} catch (NumberFormatException e) {
					throw new IOException("Time signature \"" + indicator + "\" not recognized on line " + lineNum + ".");
				}
				
				// Valid time signature found
				timeTracker.addTimeSignatureChange(0L, numerator, denominator);
			}
			
		} else if (indicator.length() > 1 && indicator.toLowerCase().toCharArray()[1] <= 'g' && indicator.toLowerCase().toCharArray()[1] >= 'a') {
			// Key signature marker
			String key = indicator.substring(1, indicator.length() - 1);
			
			char note = key.charAt(0);
			boolean isMajor = Character.isUpperCase(note);
			
			int keyNumber = getOffsetAboveCFromChar(note);
			if (keyNumber == -1) {
				throw new IOException("Key signature \"" + indicator + "\" not recognized on line " + lineNum + ".");
			}
			
			
			// Handle sharps and flats
			for (int i = 1; i < key.length(); i++) {
				switch (key.charAt(i)) {
					case '-':
						keyNumber--;
						break;
					
					case '#':
						keyNumber++;
						break;
					
					default:
						throw new IOException("Key signature \"" + indicator + "\" not recognized on line " + lineNum + ".");
				}
			}
			
			// Valid key signature found
			timeTracker.addKeySignatureChange(0L, keyNumber, isMajor);
			
		} else if (!indicator.startsWith("*-")) {
			// NOT end of voice marker
			
		}
	}
	
	/**
	 * Get the offset (in semitones) above C of the given pitch.
	 * 
	 * @param pitch The pitch character we want the offset above C for.
	 * @return The offset above C of the given pitch character, or -1 if invalid.
	 */
	private int getOffsetAboveCFromChar(char pitch) {
		switch (Character.toUpperCase(pitch)) {
			case 'C':
				return 0;
				
			case 'D':
				return 2;
				
			case 'E':
				return 4;
				
			case 'F':
				return 5;
				
			case 'G':
				return 7;
				
			case 'A':
				return 9;
				
			case 'B':
				return 11;
				
			default:
				return -1;
		}
	}

	/**
     * Get a List of the gold standard voices from this song.
     * 
     * @return A List of the gold standard voices from this song.
     */
	@Override
	public List<List<MidiNote>> getGoldStandardVoices() {
		return goldStandard;
	}
	
	@Override
	public long getFirstNoteTime() {
		return firstNoteTime == Long.MAX_VALUE ? 0L : firstNoteTime;
	}
}
