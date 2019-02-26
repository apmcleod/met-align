package metalign.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sound.midi.InvalidMidiDataException;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import metalign.Runner;
import metalign.beat.Beat;
import metalign.beat.fromfile.FromFileBeatTrackingModelState;
import metalign.hierarchy.fromfile.FromFileHierarchyModelState;
import metalign.joint.JointModel;
import metalign.parsing.EventParser;
import metalign.parsing.NoteEventParser;
import metalign.parsing.NoteListGenerator;
import metalign.time.FromOutputTimeTracker;
import metalign.time.MidiTimeTracker;
import metalign.time.TimeSignature;
import metalign.time.TimeTracker;
import metalign.voice.fromfile.FromFileVoiceSplittingModelState;

public class Temperley {
	/**
	 * Generate an output file with Voices (blank), beats, and hierarchy in our format from a Temperley polyph output
	 * (from standard in). The input file is given to find the onset time of the first note in order
	 * to convert times.
	 * 
	 * @param inputFile The file which was fed into polyph to create Temperley's output.
	 * @throws IOException 
	 * @throws SAXException 
	 * @throws ParserConfigurationException 
	 * @throws InterruptedException 
	 * @throws InvalidMidiDataException 
	 */
	public static void generateFromTemperley(File inputFile) throws IOException, InvalidMidiDataException, InterruptedException {
		long firstNoteTime = -1L;
		
		TimeTracker tt = new MidiTimeTracker();
		NoteEventParser nep = new NoteListGenerator(tt);
		EventParser ep = Runner.parseFile(inputFile, nep, tt, true);
		firstNoteTime = ep.getFirstNoteTime();
		
		tt = getTimeTrackerFromTemperleyOutput(new Scanner(System.in), firstNoteTime);
		JointModel jm = new JointModel(new FromFileVoiceSplittingModelState(ep),
									new FromFileBeatTrackingModelState(tt),
									new FromFileHierarchyModelState(tt));
		
		jm.close();
		
		// Print results
		System.out.println("Voices: " + jm.getHypotheses().first().getVoiceState());
		System.out.println("Beats: " + jm.getHypotheses().first().getBeatState());
		System.out.println("Hierarchy: " + jm.getHypotheses().first().getHierarchyState());
	}
	
	/**
	 * Convert the given input file out into a note file.
	 * 
	 * @param file The input file to be converted into a note file.
	 * @return The String of the note file for the given input file.
	 * 
	 * @throws InterruptedException If some interrupt occurred while parsing.
	 */
	public static String getNoteFileString(File file) throws InterruptedException {
		TimeTracker tt = new MidiTimeTracker();
		NoteListGenerator nlg = new NoteListGenerator(tt);
			
		try {
			Runner.parseFile(file, nlg, tt, false);
				
		} catch (IOException | InvalidMidiDataException e) {	
			System.err.println(e.getLocalizedMessage());
		}
			
		return new NoteFileWriter(nlg).toString();
	}
	
	private static TimeTracker getTimeTrackerFromTemperleyOutput(Scanner in, long firstNoteTime) {
		// Parsing variables
		Pattern linePattern = Pattern.compile(" *([0-9]+) \\( *[0-9]+\\)(.+)");
		
		// Time variable
		List<Integer> times = new ArrayList<Integer>();
		int subtractiveFactor = 1150;
		int multiplicativeFactor = 1000;
		int additiveFactor = (int) firstNoteTime;
		
		// Bar structure variables
		int barCount = 0;
		int tatumsPerBar = 0;
		int beatsPerBar = 0;
		int subBeatsPerBeat = 0;
		
		int anacrusisLengthTatums = 0;
		
		int lineNum = -1;
		
		List<Integer> levels = new ArrayList<Integer>();
		while (in.hasNextLine()) {
			String line = in.nextLine();
			
			Matcher lineMatcher = linePattern.matcher(line);
			
			if (!lineMatcher.matches()) {
				continue;
			}
			
			// Found a matching line. Get its time
			int time = Integer.parseInt(lineMatcher.group(1));
			int convertedTime = (time - subtractiveFactor) * multiplicativeFactor + additiveFactor;
			
			times.add(convertedTime);
				
			lineNum++;
			
			// Bar structure
			int level = getNumLevels(line);
			levels.add(level);
			if (level == 4) {
				// bar found
				barCount++;
				
				if (barCount == 1) {
					anacrusisLengthTatums = lineNum;
					
				} else if (barCount == 2) {
					tatumsPerBar = lineNum - anacrusisLengthTatums;
				}
			}
			
			if (barCount == 1 && level >= 3) {
				// Beat found
				beatsPerBar++;
			}
			
			if (barCount == 1 && beatsPerBar == 1 && level >= 2) {
				// sub beat found
				subBeatsPerBeat++;
			}
		}
		in.close();
		
		if (anacrusisLengthTatums == tatumsPerBar) {
			anacrusisLengthTatums = 0;
		}
		
		int tatumsPerSubBeat = tatumsPerBar / beatsPerBar / subBeatsPerBeat;
		int anacrusisLengthSubBeats = anacrusisLengthTatums / tatumsPerSubBeat;
		
		FromOutputTimeTracker tt = new FromOutputTimeTracker();
		int numerator = beatsPerBar;
		int denominator = 4;
		
		if (subBeatsPerBeat == 3) {
			numerator *= 3;
			denominator = 8; 
		}
		tt.setTimeSignature(new TimeSignature(numerator, denominator));
		tt.setAnacrusisSubBeats(anacrusisLengthSubBeats);
		
		// -1 is needed here because we increment subBeat in the level == 2 case
		int subBeat = (-anacrusisLengthSubBeats + subBeatsPerBeat * beatsPerBar) % subBeatsPerBeat - 1;
		int beat = (-anacrusisLengthSubBeats + subBeatsPerBeat * beatsPerBar) / subBeatsPerBeat;
		int bar = 0;
		
		for (int i = 0; i < times.size(); i++) {
			long time = times.get(i);
			int level = levels.get(i);
			
			if (level == 4) {
				bar++;
				beat = 0;
				subBeat = 0;
			}
			
			if (level == 3) {
				beat++;
				subBeat = 0;
			}
			
			if (level == 2) {
				subBeat++;
			}
			
			tt.addBeat(new Beat(bar, beat, subBeat, 0, time , time));
		}
		
		return tt;
	}
	
	/**
	 * Return the number of levels on the given line of Temperley output. That is,
	 * the number of occurrences of "x " on that line.
	 * 
	 * @param line The line we are searching.
	 * @return The number of levels on the given line.
	 */
	private static int getNumLevels(String line) {
		int lastIndex = 0;
		int count = 0;
		
		while (lastIndex != -1) {
			lastIndex = line.indexOf("x ", lastIndex);
			
			if (lastIndex != -1) {
				count++;
				lastIndex += 2;
			}
		}
		
		return count;
	}
}

/**
 * A <code>NoteFileWriter</code> is used to generate note files, as used by
 * Temperley's model. These are files of the format:
 * <p>
 * Note 0 1000 60
 * </p>
 * Where that line would represent a middle C which starts at time 0 and ends at
 * time 1000 (measured in milliseconds). The resulting output will be shifted
 * such that any anacrusis is now built into the file. That is, the first measure
 * is extended with rests so that it becomes a full measure.
 * <p>
 * It does not actually write this out to a File. Rather, it returns the String
 * via it's toString method. Thus, it can be used to either write to a File or to
 * print out to std out, like so:
 * <p>
 * <code>
 * System.out.println(new NoteFileWriter(tt, nlg));
 * </code>
 * 
 * @author Andrew McLeod - 10 September, 2016
 */
class NoteFileWriter {
	// TODO: Why offset was here?
	/**
	 * The NoteListGenerator which contains the notes we want to write out.
	 */
	private NoteListGenerator nlg;
	
	/**
	 * Create a new NoteFileWriter with the given TimeTracker and NoteListGenerator.
	 * 
	 * @param tt The TimeTracker to use to get the correct anacrusis {@link #offsetLength}.
	 * @param nlg The NoteListGenerator containing the notes we want to write out.
	 */
	public NoteFileWriter(NoteListGenerator nlg) {
		this.nlg = nlg;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		for (MidiNote note : nlg.getNoteList()) {
			long onTime = note.getOnsetTime() / 1000;
			long offTime = note.getOffsetTime() / 1000;
			
			sb.append("Note ");
			sb.append(onTime).append(' ');
			sb.append(offTime).append(' ');
			sb.append(note.getPitch()).append('\n');
		}
		
		if (sb.length() > 0) {
			sb.deleteCharAt(sb.length() - 1);
		}
		
		return sb.toString();
	}
}
