package metalign.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.sound.midi.InvalidMidiDataException;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import metalign.Main;
import metalign.Runner;
import metalign.beat.Beat;
import metalign.hierarchy.Measure;
import metalign.hierarchy.lpcfg.MetricalLpcfgGeneratorRunner;
import metalign.joint.JointModelState;
import metalign.parsing.NoteEventParser;
import metalign.parsing.NoteListGenerator;
import metalign.parsing.XMLParser;
import metalign.time.FromOutputTimeTracker;
import metalign.time.TimeSignature;
import metalign.time.TimeTracker;
import metalign.voice.Voice;

/**
 * An <code>Evaluator</code> is used to evaluate some voice, beat, and hierarchy hypothesis,
 * given a ground truth file.
 * 
 * @author Andrew - 4 February, 2018
 */
public class Evaluator {
	/**
	 * The ground truth voices to use for evaluation.
	 */
	private List<List<MidiNote>> groundTruthVoices;
	
	/**
	 * The ground truth tatums.
	 */
	private List<Beat> tatums;
	
	/**
	 * The ground truth beat times.
	 */
	private List<Long> beatTimes;
	
	/**
	 * The ground truth downbeat times.
	 */
	private List<Long> downbeatTimes;
	
	/**
	 * The ground truth metrical groupings.
	 */
	private Set<MetricalGrouping> groundTruthGroupings;
	
	/**
	 * The ground truth number of beats per bar.
	 */
	private int beatsPerBar;
	
	/**
	 * The ground truth number of sub beats per beat.
	 */
	private int subBeatsPerBeat;
	
	/**
	 * Create a new Evaluator from the given ground truth file. This performs all of the
	 * parsing necessary to get the ground truth times, groupings, etc.
	 * 
	 * @param groundTruth The ground truth file to parse.
	 * @param anacrusisFiles The anacrusis files to use.
	 * @param useChannel Whether to use channels or tracks for MIDI voices.
	 * 
	 * @throws IOException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws InvalidMidiDataException
	 * @throws InterruptedException
	 */
	public Evaluator(File groundTruth, List<File> anacrusisFiles, boolean useChannel) throws IOException, ParserConfigurationException, SAXException, InvalidMidiDataException, InterruptedException {
		groundTruthVoices = new ArrayList<List<MidiNote>>();
		tatums = new ArrayList<Beat>(0);
		
		beatsPerBar = 1;
		subBeatsPerBeat = 1;

		// Parse ground truth file
		TimeTracker tt = new TimeTracker();
		tt.setAnacrusis(MetricalLpcfgGeneratorRunner.getAnacrusisLength(groundTruth, anacrusisFiles));
		XMLParser xml = null;
		NoteEventParser nep = new NoteListGenerator(tt);
		
		if (groundTruth.toString().endsWith(".xml")) {
			File deviationFile = new File(groundTruth.getParentFile() + File.separator + "deviation_nodoctype.xml");
			xml = new XMLParser(deviationFile, groundTruth);
			xml.run();
			tatums = xml.getBeats();
			
			int numerator = xml.getNumerators(1);
			beatsPerBar = numerator;
			subBeatsPerBeat = 2;
			if (numerator != 3 && numerator % 3 == 0) {
				beatsPerBar /= 3;
				subBeatsPerBeat = 3;
			}
			tatums = FromOutputTimeTracker.fixBeatsGivenSubBeatLength(tatums, Main.SUB_BEAT_LENGTH * subBeatsPerBeat);
			
		} else {
			groundTruthVoices = Runner.parseFile(groundTruth, nep, tt, useChannel).getGoldStandardVoices();
			tatums = tt.getTatums();
			
			TimeSignature timeSig = tt.getNodeAtTime(tatums.get(0).getTime()).getTimeSignature();
			Measure tmpMeasure = timeSig.getMetricalMeasure();
			beatsPerBar = tmpMeasure.getBeatsPerMeasure();
			subBeatsPerBeat = tmpMeasure.getSubBeatsPerBeat();
		}
		
		// Get ground truth sub-beat, beat, and downbeat times
		List<Long> subBeatTimes = new ArrayList<Long>();
		beatTimes = new ArrayList<Long>();
		downbeatTimes = new ArrayList<Long>();
		
		List<Integer> notes32PerSubBeatList = new ArrayList<Integer>();
		List<Integer> subBeatsPerBeatList = new ArrayList<Integer>();
		
		for (Beat beat : tatums) {
			long time = beat.getTime();
			
			int notes32PerSubBeat = 0;
			int notes32PerBeat = 0;
			int subBeatsPerBeat = 2;
			
			if (xml == null) {
				TimeSignature timeSig = tt.getNodeAtTime(time).getTimeSignature();
				int notes32PerBar = timeSig.getNotes32PerBar();
				Measure tmpMeasure = timeSig.getMetricalMeasure();
				int beatsPerBar = tmpMeasure.getBeatsPerMeasure();
				subBeatsPerBeat = tmpMeasure.getSubBeatsPerBeat();
				notes32PerBeat = notes32PerBar / beatsPerBar;
				notes32PerSubBeat = notes32PerBeat / tmpMeasure.getSubBeatsPerBeat();
				
			} else {
				int tatumsPerBar = xml.getBeatsPerBar(beat.getBar());
				int numerator = xml.getNumerators(beat.getBar());
				
				int beatsPerBar = numerator;
				subBeatsPerBeat = 2;
				if (numerator != 3 && numerator % 3 == 0) {
					beatsPerBar /= 3;
					subBeatsPerBeat = 3;
				}
	
				notes32PerBeat = tatumsPerBar / beatsPerBar;
				notes32PerSubBeat = notes32PerBeat / subBeatsPerBeat;
			}
			
			// Found a sub-beat
			if (notes32PerSubBeat != 0 && beat.getTatum() % notes32PerSubBeat == 0) {
				subBeatTimes.add(time);
			}
			
			// Found a beat
			if (beat.getTatum() % notes32PerBeat == 0) {
				beatTimes.add(time);
				notes32PerSubBeatList.add(notes32PerSubBeat);
				subBeatsPerBeatList.add(subBeatsPerBeat);
			}
			
			// Found a downbeat
			if (beat.getTatum() == 0) {
				downbeatTimes.add(time);
			}
		}
		
		// Fix in case xml and subbeats weren't listed
		for (int i = 0; i < beatTimes.size(); i++) {
			int notes32PerSubBeat = notes32PerSubBeatList.get(i);
			int subBeatsPerBeat = subBeatsPerBeatList.get(i);
			
			if (notes32PerSubBeat == 0) {
				double thisTime = beatTimes.get(i);
				subBeatTimes.add((long) thisTime);
				
				if (beatTimes.size() > i + 1) {
					double nextTime = beatTimes.get(i + 1);
					double diff = nextTime - thisTime;
					
					for (int division = 1; division < subBeatsPerBeat; division++) {
						subBeatTimes.add(Math.round(thisTime + diff * division / subBeatsPerBeat));
					}
				}
			}
		}
		
		Collections.sort(subBeatTimes);
		
		// Generate ground truth groupings set
		groundTruthGroupings = new TreeSet<MetricalGrouping>();
		
		for (int i = 1; i < subBeatTimes.size(); i++) {
			groundTruthGroupings.add(new MetricalGrouping(subBeatTimes.get(i - 1), subBeatTimes.get(i)));
		}
		
		for (int i = 1; i < beatTimes.size(); i++) {
			groundTruthGroupings.add(new MetricalGrouping(beatTimes.get(i - 1), beatTimes.get(i)));
		}
		
		for (int i = 1; i < downbeatTimes.size(); i++) {
			groundTruthGroupings.add(new MetricalGrouping(downbeatTimes.get(i - 1), downbeatTimes.get(i)));
		}
	}
	
	/**
	 * Evaluate the given JointModelState and return its evaluation String.
	 * 
	 * @param jms The JointModelState to evaluate.
	 * @return Its evaluation String.
	 */
	public String evaluate(JointModelState jms) {
		return evaluate(jms.getVoiceState().getVoices(),
						jms.getBeatState().getBeats(),
						jms.getHierarchyState().getMetricalMeasure());
	}
	
	/**
	 * Evaluate the given voices, beats, and hierarchy and return their evaluation String.
	 * 
	 * @param voiceList The voices.
	 * @param beatList The beats.
	 * @param measure The hierarchy.
	 * @return Their evaluation String.
	 */
	public String evaluate(List<Voice> voiceList, List<Beat> beatList, Measure measure) {
		// Get scores
		double voiceScore = getVoiceScore(voiceList);
		double meterScore = getMeterScore(beatList, measure);
		
		// Build score string scores
		StringBuilder sb = new StringBuilder();
		sb.append("Voice Score: ").append(voiceScore).append('\n');
		sb.append("Meter Score: ").append(meterScore);
		
		return sb.toString();
	}
	
	/**
	 * Get the voice score of the given voices.
	 * 
	 * @param voicesList The voices to evaluate.
	 * @return The F1.
	 */
	private double getVoiceScore(List<Voice> voicesList) {
		if (groundTruthVoices.isEmpty()) {
			return 0.0;
		}
		
		// Evaluation
		int noteCount = 0;
			
		int truePositives = 0;
		int falsePositives = 0;
		
		// The size of this Set will be the true number of voices in this song.
		Set<Integer> voiceCount = new HashSet<Integer>();
		for (List<MidiNote> noteList : groundTruthVoices) {
			for (MidiNote note : noteList) {
				voiceCount.add(note.getCorrectVoice());
			}
		}

		// Evaluate each found voice
		for (Voice voice : voicesList) {
			int voiceNumNotes = voice.getNumNotes();
				
			int voiceTruePositives = voice.getNumLinksCorrect(groundTruthVoices);
			int voiceFalsePositives = voiceNumNotes - voiceTruePositives - 1;
				
			noteCount += voiceNumNotes;
			
			truePositives += voiceTruePositives;
			falsePositives += voiceFalsePositives;
		}
		
		// Overall f-1 evaluation
		int falseNegatives = noteCount - voiceCount.size() - truePositives;
		
		return MathUtils.getF1(truePositives, falsePositives, falseNegatives);
	}
	
	/**
	 * Get a metrical evaluation score, given some beats and a hierarchy.
	 * 
	 * @param beatList The given beats.
	 * @param measure The given hierarchy.
	 * @return The metrical evaluation score.
	 */
	private double getMeterScore(List<Beat> beatList, Measure measure) {
		// Generate sub-beat, beat, and downbeat lists
		List<Long> guessedSubBeatTimes = new ArrayList<Long>();
		List<Long> guessedBeatTimes = new ArrayList<Long>();
		List<Long> guessedDownbeatTimes = new ArrayList<Long>();
		
		int subBeatIncrement = measure.getLength();
		int subBeatStartIndex = 0;
		
		int beatIncrement = measure.getSubBeatsPerBeat() * measure.getLength();
		int beatStartIndex = (measure.getAnacrusis() * measure.getLength()) % beatIncrement;
		
		int downbeatIncrement = measure.getSubBeatsPerBeat() * measure.getLength();
		int downbeatStartIndex = measure.getAnacrusis() * measure.getLength();
		downbeatIncrement *= measure.getBeatsPerMeasure();
		
		// Go through each detected beat
		for (int beatIndex = 0; beatIndex < beatList.size(); beatIndex++) {
			Beat beat = beatList.get(beatIndex);
			long time = beat.getTime();
			
			if ((beatIndex - subBeatStartIndex) % subBeatIncrement == 0) {
				guessedSubBeatTimes.add(time);
			}
			
			if ((beatIndex - beatStartIndex) % beatIncrement == 0) {
				guessedBeatTimes.add(time);
			}
			
			if ((beatIndex - downbeatStartIndex) % downbeatIncrement == 0) {
				guessedDownbeatTimes.add(time);
			}
		}
		
		// Create guessed groupings set
		Set<MetricalGrouping> groupings = new TreeSet<MetricalGrouping>();
		
		for (int i = 1; i < guessedSubBeatTimes.size(); i++) {
			groupings.add(new MetricalGrouping(guessedSubBeatTimes.get(i - 1), guessedSubBeatTimes.get(i)));
		}
		
		for (int i = 1; i < guessedBeatTimes.size(); i++) {
			groupings.add(new MetricalGrouping(guessedBeatTimes.get(i - 1), guessedBeatTimes.get(i)));
		}
		
		for (int i = 1; i < guessedDownbeatTimes.size(); i++) {
			groupings.add(new MetricalGrouping(guessedDownbeatTimes.get(i - 1), guessedDownbeatTimes.get(i)));
		}
		
		// Test guessed groupings
		int truePositives = 0;
		int falsePositives = 0;
		
		for (MetricalGrouping grouping : groupings) {
			if (groundTruthGroupings.contains(grouping)) {
				truePositives++;
				
			} else {
				falsePositives++;
			}
		}
		
		int falseNegatives = groundTruthGroupings.size() - truePositives;
		
		return MathUtils.getF1(truePositives, falsePositives, falseNegatives);
	}
	
	public List<Beat> getTatums() {
		return tatums;
	}
	
	public Measure getHierarchy() {
		return new Measure(beatsPerBar, subBeatsPerBeat);
	}
}
