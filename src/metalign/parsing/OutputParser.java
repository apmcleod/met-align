package metalign.parsing;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import metalign.beat.Beat;
import metalign.hierarchy.Measure;
import metalign.utils.MidiNote;
import metalign.voice.Voice;

public class OutputParser {
	
	private List<Voice> voiceList;
	private List<Beat> beatList;
	private Measure measure;
	
	public OutputParser() throws IOException {
		this(System.in);
	}
	
	public OutputParser(InputStream in) throws IOException {
		// Parse input
		String voices = null;
		String beats = null;
		String hierarchy = null;
		int found = 0;
		
		Scanner input = new Scanner(in);
		while (input.hasNextLine()) {
			String line = input.nextLine();
			
			int breakPoint = line.indexOf(": ");
			if (breakPoint == -1) {
				continue;
			}
			
			String prefix = line.substring(0, breakPoint);
			
			// Check for matching prefixes
			if (voices == null && prefix.equalsIgnoreCase("Voices")) {
				voices = line.substring(breakPoint + 2);
				found++;
				if (found == 3) {
					break;
				}
				
			} else if (beats == null && prefix.equalsIgnoreCase("Beats")) {
				beats = line.substring(breakPoint + 2);
				found++;
				if (found == 3) {
					break;
				}
				
			} else if (hierarchy == null && prefix.equalsIgnoreCase("Hierarchy")) {
				hierarchy = line.substring(breakPoint + 2);
				found++;
				if (found == 3) {
					break;
				}
			}
		}
		input.close();
		
		if (found != 3) {
			throw new IOException("Input malformed");
		}
		
		// Parse string
		voiceList = parseVoices(voices);
		beatList = parseBeats(beats);
		measure = parseHierarchy(hierarchy);
	}
	
	public List<Voice> getVoices() {
		return voiceList;
	}
	
	public List<Beat> getBeats() {
		return beatList;
	}
	
	public Measure getMeasure() {
		return measure;
	}
	
	/**
	 * Parse the given voice result String into a List of Voices.
	 * 
	 * @param voices The output voice string.
	 * @return A List of Voices.
	 * @throws IOException
	 */
	private static List<Voice> parseVoices(String voices) throws IOException {
		if (voices.length() <= 1) {
			return new ArrayList<Voice>(0);
		}
		Pattern notePattern = Pattern.compile("\\(K:([A-G]#?[0-9])  V:([0-9]+)  \\[([0-9]+)\\-([0-9]+)\\] ([0-9]+)\\)");
		String[] splitVoices = voices.split("\\], ?\\[");
		splitVoices[0] = splitVoices[0].replace("[[", "");
		splitVoices[splitVoices.length - 1] = splitVoices[splitVoices.length - 1].substring(0, splitVoices[splitVoices.length - 1].lastIndexOf(')') + 1);
		
		List<Voice> voicesList = new ArrayList<Voice>(splitVoices.length);
		
		// Parsing
		for (String voiceString : splitVoices) {
			Voice voice = null;
			
			// Parse each note
			String[] noteStrings = voiceString.split(", ");
			for (String noteString : noteStrings) {
				
				Matcher noteMatcher = notePattern.matcher(noteString);
				if (noteMatcher.matches()) {
					String pitchString = noteMatcher.group(1);
					int pitch = MidiNote.getPitch(pitchString);
					
					int velocity = Integer.parseInt(noteMatcher.group(2));
					int onsetTime = Integer.parseInt(noteMatcher.group(3));
					int offsetTime = Integer.parseInt(noteMatcher.group(4));
					int correctVoice = Integer.parseInt(noteMatcher.group(5));
					
					MidiNote note = new MidiNote(pitch, velocity, onsetTime, onsetTime, correctVoice, -1);
					note.close(offsetTime, offsetTime);
					voice = new Voice(note, voice);
					
				} else {
					throw new IOException("Voice separation results malformed: " + noteString);
				}
			}
			
			voicesList.add(voice);
		}
		
		return voicesList;
	}

	/**
	 * Parse the given beats String into a List of Beats.
	 * 
	 * @param beats The Beat output String to parse.
	 * @return A List of Beats.
	 * @throws IOException
	 */
	private static List<Beat> parseBeats(String beats) throws IOException {
		if (beats.length() <= 1) {
			return new ArrayList<Beat>(0);
		}
		Pattern beatPattern = Pattern.compile("(-?[0-9]+)\\.([0-9]+)\\.([0-9]+)\\.([0-9]+),(-?[0-9]+)");
		String[] splitBeats = beats.split("\\),\\(");
		splitBeats[0] = splitBeats[0].replace("[(", "");
		splitBeats[splitBeats.length - 1] = splitBeats[splitBeats.length - 1].substring(0, splitBeats[splitBeats.length - 1].indexOf(')'));
		
		List<Beat> beatsList = new ArrayList<Beat>(splitBeats.length);
		
		for (String beatString : splitBeats) {
			Matcher beatMatcher = beatPattern.matcher(beatString);
			
			if (beatMatcher.matches()) {
				int bar = Integer.parseInt(beatMatcher.group(1));
				int beatNum = Integer.parseInt(beatMatcher.group(2));
				int subBeat = Integer.parseInt(beatMatcher.group(3));
				int tatum = Integer.parseInt(beatMatcher.group(4));
				int time = Integer.parseInt(beatMatcher.group(5));
				
				Beat beat = new Beat(bar, beatNum, subBeat, tatum, time, time);
				beatsList.add(beat);
				
			} else {
				throw new IOException("Beat tracking results malformed");
			}
		}
		
		return beatsList;
	}

	/**
	 * Parse the given hierarchy result String into a Measure.
	 * 
	 * @param hierarchy A hierarchy result String.
	 * @return The Measure, parsed from the given String.
	 * @throws IOException
	 */
	private static Measure parseHierarchy(String hierarchy) throws IOException {
		Pattern hierarchyPattern = Pattern.compile("M_([0-9]+),([0-9]+).*");

		Matcher hierarchyMatcher = hierarchyPattern.matcher(hierarchy);
		Measure measure = null;
		if (hierarchyMatcher.matches()) {
			int beatsPerBar = Integer.parseInt(hierarchyMatcher.group(1));
			int subBeatsPerBeat = Integer.parseInt(hierarchyMatcher.group(2));
			
			measure = new Measure(beatsPerBar, subBeatsPerBeat);
			
		} else {
			throw new IOException("Hierarchy results malformed");
		}
		
		return measure;
	}
	
	/**
	 * Calculate mean and standard deviation of Voice, Beat, Downbeat, and Meter scores as produced by
	 * Evaluation -E, read from std in.
	 */
	public static void checkFull() {
		int voiceCount = 0;
		double voiceSum = 0.0;
		double voiceSumSquared = 0.0;
		
		int beatCount = 0;
		double beatSum = 0.0;
		double beatSumSquared = 0.0;
		
		int downBeatCount = 0;
		double downBeatSum = 0.0;
		double downBeatSumSquared = 0.0;
		
		int meterCount = 0;
		double meterSum = 0.0;
		double meterSumSquared = 0.0;
		
		Scanner input = new Scanner(System.in);
		while (input.hasNextLine()) {
			String line = input.nextLine();
			
			int breakPoint = line.indexOf(": ");
			if (breakPoint == -1) {
				continue;
			}
			
			String prefix = line.substring(0, breakPoint);
			
			// Check for matching prefixes
			if (prefix.equalsIgnoreCase("Voice Score")) {
				double score = Double.parseDouble(line.substring(breakPoint + 2));
				voiceSum += score;
				voiceSumSquared += score * score;
				voiceCount++;
				
			} else if (prefix.equalsIgnoreCase("Beat Score")) {
				double score = Double.parseDouble(line.substring(breakPoint + 2));
				beatSum += score;
				beatSumSquared += score * score;
				beatCount++;
				
			} else if (prefix.equalsIgnoreCase("Downbeat Score")) {
				double score = Double.parseDouble(line.substring(breakPoint + 2));
				downBeatSum += score;
				downBeatSumSquared += score * score;
				downBeatCount++;
				
			} else if (prefix.equalsIgnoreCase("Meter Score")) {
				double score = Double.parseDouble(line.substring(breakPoint + 2));
				meterSum += score;
				meterSumSquared += score * score;
				meterCount++;
			}
		}
		input.close();
		
		double voiceMean = voiceSum / voiceCount;
		double voiceVariance = voiceSumSquared / voiceCount - voiceMean * voiceMean;
		
		double beatMean = beatSum / beatCount;
		double beatVariance = beatSumSquared / beatCount - beatMean * beatMean;
		
		double downBeatMean = downBeatSum / downBeatCount;
		double downBeatVariance = downBeatSumSquared / downBeatCount - downBeatMean * downBeatMean;
		
		double meterMean = meterSum / meterCount;
		double meterVariance = meterSumSquared / meterCount - meterMean * meterMean;
		
		System.out.println("Voice: mean=" + voiceMean + " stdev=" + Math.sqrt(voiceVariance));
		System.out.println("Beat: mean=" + beatMean + " stdev=" + Math.sqrt(beatVariance));
		System.out.println("Downbeat: mean=" + downBeatMean + " stdev=" + Math.sqrt(downBeatVariance));
		System.out.println("Meter: mean=" + meterMean + " stdev=" + Math.sqrt(meterVariance));
	}
}
