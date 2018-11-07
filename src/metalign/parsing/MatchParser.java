package metalign.parsing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import metalign.beat.Beat;
import metalign.hierarchy.Measure;

public class MatchParser {
	
	private final File matchFile;
	
	private double tickToTimeMultiplier = 1.0;
	
	private Measure measure;
	
	private List<Beat> tatums;
	
	private boolean versionFive = false;
	
	private static final Pattern infoPattern = Pattern.compile("info\\(([a-zA-Z]+),\\[?([^\\[\\]]*)\\]?\\)\\.");
	
	private static final Pattern notePattern = Pattern.compile("snote\\((.*)\\)-note\\((.*)\\)\\.");

	public MatchParser(File matchFile) {
		this.matchFile = matchFile;
		tatums = new ArrayList<Beat>();
	}

	public void run() throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(matchFile));
		int tatumsPerBeat = 0;
		int tatumsPerBar = 0;
		
		String line;
		while ((line = br.readLine()) != null) {
			Matcher infoMatcher = infoPattern.matcher(line);
			if (infoMatcher.matches()) {
				if (infoMatcher.group(1).equals("matchFileVersion")) {
					if (infoMatcher.group(2).equals("5.0")) {
						versionFive = true;
					}
					
				} else if (infoMatcher.group(1).equals("midiClockUnits")) {
					tickToTimeMultiplier /= Integer.parseInt(infoMatcher.group(2));
					
				} else if (infoMatcher.group(1).equals("midiClockRate")) {
					tickToTimeMultiplier *= Integer.parseInt(infoMatcher.group(2));
					
				} else if (infoMatcher.group(1).equals("timeSignature")) {
					String timeSig = infoMatcher.group(2);
					int numerator = Integer.parseInt(timeSig.substring(0, timeSig.indexOf("/")));
					
					int subBeatsPerBeat = 2;
					int beatsPerBar = numerator;
					
					if (numerator > 5 && numerator % 3 == 0) {
						subBeatsPerBeat = 3;
						beatsPerBar /= 3;
					}
					
					measure = new Measure(beatsPerBar, subBeatsPerBeat);
					
					tatumsPerBeat = subBeatsPerBeat * 12;
					tatumsPerBar = tatumsPerBeat * beatsPerBar;
				}
				
			} else {
				Matcher noteMatcher = notePattern.matcher(line);
				
				if (noteMatcher.matches()) {
					if (tatumsPerBeat == 0) {
						br.close();
						throw new IOException("No time signature found.");
					}
					
					String[] scoreNote = noteMatcher.group(1).split(",");
					String[] note = noteMatcher.group(2).split(",");
					
					double beatDouble = Double.parseDouble(scoreNote[7]);
					
					if (measure.getSubBeatsPerBeat() == 3) {
						beatDouble /= 3;
					}
					
					double tatumEstimate = beatDouble * tatumsPerBeat + tatumsPerBar;
					int roundedTatumEstimate = (int) Math.round(tatumEstimate);
					if (Math.abs(tatumEstimate - roundedTatumEstimate) > 0.01) {
						System.err.println("Warning, large tatum time deviation for line " + line + ". Skipping note.");
						continue;
					}
					
					int tatum = roundedTatumEstimate % tatumsPerBar;
					int bar = roundedTatumEstimate / tatumsPerBar;
					
					double tick = Double.parseDouble(note[4]);
					long time = versionFive ? Math.round(tick * 1000) : Math.round(tick * tickToTimeMultiplier);
					
					Beat beat = new Beat(bar, 0, 0, tatum, time, Math.round(tick));
					tatums.add(beat);
				}
			}
		}
		
		br.close();
		
		Collections.sort(tatums);
		fixTatums();
	}

	private void fixTatums() {
		// De-duplicate tatums
		int numMatches = 1;
		int bar = tatums.get(0).getBar();
		int tatum = tatums.get(0).getTatum();
		long sumTimes = tatums.get(0).getTime();
		List<Beat> deduplicated = new ArrayList<Beat>();
		
		for (int i = 1; i < tatums.size(); i++) {
			Beat beat = tatums.get(i);
			if (beat.getBar() != bar || beat.getTatum() != tatum) {
				long time = Math.round(((double) sumTimes) / numMatches);
				deduplicated.add(new Beat(bar, 0, 0, tatum, time, (long) (((double) sumTimes) / numMatches / tickToTimeMultiplier)));
				
				bar = beat.getBar();
				tatum = beat.getTatum();
				sumTimes = beat.getTime();
				numMatches = 1;
				
			} else {
				numMatches++;
				sumTimes += beat.getTime();
			}
		}
		
		long time = Math.round(((double) sumTimes) / numMatches);
		deduplicated.add(new Beat(bar, 0, 0, tatum, time, (long) (((double) sumTimes) / numMatches / tickToTimeMultiplier)));
		
		// Interpolate between tatums
		int tatumsPerBeat = measure.getSubBeatsPerBeat() * 12;
		int tatumsPerBar = tatumsPerBeat * measure.getBeatsPerBar();
		
		tatums.clear();
		tatums.add(deduplicated.get(0));
		for (int i = 1; i < deduplicated.size(); i++) {
			Beat previous = deduplicated.get(i - 1);
			Beat beat = deduplicated.get(i);
			
			bar = previous.getBar();
			tatum = previous.getTatum();
			time = previous.getTime();
			long tick = previous.getTick();
			
			int tatumsBetween = tatumsPerBar * (beat.getBar() - bar) + beat.getTatum() - tatum;
			double timeDelta = ((double) beat.getTime() - time) / tatumsBetween;
			double tickDelta = ((double) beat.getTick() - tick) / tatumsBetween;
			
			for (int tatumNum = 1; tatumNum < tatumsBetween; tatumNum++) {
				tatum++;
				if (tatum >= tatumsPerBar) {
					tatum -= tatumsPerBar;
					bar++;
				}
				
				int beatNum = tatum / tatumsPerBeat;
				int subBeatNum = (tatum % tatumsPerBeat) / 12;
				int tatumNumber = (tatum % tatumsPerBeat) % 12;
				
				if (tatumNumber == 0) {
					tatums.add(new Beat(bar, beatNum, subBeatNum, tatumNumber, (long) (time + tatumNum * timeDelta), (long) (tick + tatumNum * tickDelta)));
				}
			}
			
			tatums.add(beat);
		}
	}

	public List<Beat> getTatums() {
		return tatums;
	}

	public Measure getMeasure() {
		return measure;
	}

}
