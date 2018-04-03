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

import metalign.Runner;
import metalign.time.NoteBTimeTracker;
import metalign.time.TimeSignature;
import metalign.utils.MidiNote;
import metalign.voice.Voice;
import metalign.voice.hmm.HmmVoiceSplittingModel;
import metalign.voice.hmm.HmmVoiceSplittingModelParameters;

public class NoteBParser implements EventParser {
	
	private final File file;
	private final NoteListGenerator nlg;
	private final NoteBTimeTracker tt;
	
	private final List<List<MidiNote>> voices;
	
	private static final Pattern barPattern = Pattern.compile("Bar ([0-9]) ([0-9]) ([0-9]+)");
	private static final Pattern beatPattern = Pattern.compile("Beat +([0-9]+) +([0-9])");
	private static final Pattern notePattern = Pattern.compile("Note +([0-9]+) +([0-9]+) +([0-9]+)");

	public NoteBParser(File file, NoteListGenerator nlg, NoteBTimeTracker tt) {
		this.file = file;
		this.nlg = nlg;
		this.tt = tt;
		
		voices = new ArrayList<List<MidiNote>>();
	}

	@Override
	public void run() throws InvalidMidiDataException, InterruptedException, IOException {
		BufferedReader br = new BufferedReader(new FileReader(file));
		
		long lastTick = Long.MIN_VALUE;
		while (br.ready()) {
			String line = br.readLine();
			
			Matcher barMatcher = barPattern.matcher(line);
			Matcher beatMatcher = beatPattern.matcher(line);
			Matcher noteMatcher = notePattern.matcher(line);
			
			if (barMatcher.matches()) {
				int beatsPerBar = Integer.parseInt(barMatcher.group(1));
				int subBeatsPerBeat = Integer.parseInt(barMatcher.group(2));
				int anacrusis = Integer.parseInt(barMatcher.group(3));
				
				int numerator = beatsPerBar;
				int denominator = 4;
				
				if (subBeatsPerBeat == 3) {
					numerator *= 3;
					denominator = 8; 
				}
				tt.setTimeSignature(new TimeSignature(numerator, denominator));
				tt.setAnacrusisSubBeats(anacrusis);
				
			} else if (beatMatcher.matches()) {
				long time = Long.parseLong(beatMatcher.group(1)) * 1000;
				int level = Integer.parseInt(beatMatcher.group(2));
				lastTick = Math.max(lastTick, time);
				tt.addBeat(time, level);
				
			} else if (noteMatcher.matches()) {
				long onsetTime = Long.parseLong(noteMatcher.group(1)) * 1000;
				long offsetTime = Long.parseLong(noteMatcher.group(2)) * 1000;
				int pitch = Integer.parseInt(noteMatcher.group(3));
				lastTick = Math.max(lastTick, offsetTime);
				
				nlg.noteOn(pitch, 100, onsetTime, 0);
				nlg.noteOff(pitch, offsetTime, 0);
			}
		}
		
		br.close();
		
		tt.setLastTick(lastTick);
		
		// Populate voices with Hmm
		HmmVoiceSplittingModel voiceModel = new HmmVoiceSplittingModel(new HmmVoiceSplittingModelParameters());
		Runner.performInference(voiceModel, nlg);
		
		for (Voice voice : voiceModel.getHypotheses().first().getVoices()) {
			voices.add(voice.getNotes());
		}
	}

	@Override
	public List<List<MidiNote>> getGoldStandardVoices() {
		return voices;
	}

	@Override
	public long getFirstNoteTime() {
		return nlg.getIncomingLists().isEmpty() ? 0L : nlg.getIncomingLists().get(0).get(0).getOnsetTime();
	}
}
