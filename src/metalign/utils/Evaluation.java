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

import metalign.Main;
import metalign.Runner;
import metalign.beat.Beat;
import metalign.beat.frombeats.FromBeatsBeatTrackingModelState;
import metalign.beat.fromfile.FromFileBeatTrackingModelState;
import metalign.hierarchy.Measure;
import metalign.hierarchy.fromfile.FromFileHierarchyModelState;
import metalign.hierarchy.lpcfg.MetricalLpcfg;
import metalign.hierarchy.lpcfg.MetricalLpcfgGenerator;
import metalign.hierarchy.lpcfg.MetricalLpcfgGeneratorRunner;
import metalign.hierarchy.lpcfg.MetricalLpcfgHierarchyModelState;
import metalign.hierarchy.lpcfg.MetricalLpcfgHierarchyModelState.MetricalLpcfgMatch;
import metalign.hierarchy.lpcfg.MetricalLpcfgTree;
import metalign.joint.JointModel;
import metalign.parsing.EventParser;
import metalign.parsing.MidiWriter;
import metalign.parsing.NoteEventParser;
import metalign.parsing.NoteListGenerator;
import metalign.time.FromOutputTimeTracker;
import metalign.time.TimeSignature;
import metalign.time.TimeTracker;
import metalign.voice.Voice;
import metalign.voice.fromfile.FromFileVoiceSplittingModelState;
import metalign.voice.hmm.HmmVoiceSplittingModel;
import metalign.voice.hmm.HmmVoiceSplittingModelParameters;

/**
 * The <code>Evaluation</code> can be used to perform global evaluation on some output file.
 * 
 * @author Andrew McLeod - 9 September, 2016
 */
public class Evaluation {

	/**
	 * The accepted error for a beat location to be considered correct
	 */
	public static long BEAT_EPSILON = 70000;
	
	public static boolean VERBOSE = false;

	/**
	 * The main method for evaluating results.
	 * <p>
	 * Usage: <code>java -cp bin metalign.utils.Evaluation ARGS</code>
	 * <p>
	 * <blockquote>
	 * ARGS:
	 * <ul>
	 *  <li><code>-E FILE</code> = Evaluate the Main output (from std in) given the ground truth FILE.</li>
	 *  <li><code>-F</code> = Calculate means and standard deviations of the -E FILE results (read from std in).</li>
	 *  <li><code>-w INT</code> = Use the given INT as the window length for accepted grouping matches, in microseconds.</li>
	 *  <li><code>-T</code> = Use tracks as correct voice (instead of channels) *Only used for MIDI files.</li>
	 *  <li><code>-s INT</code> = Use INT as the sub beat length.</li>
	 *  <li><code>-a FILE</code> = Search recursively under the given FILE for anacrusis files.</li>
	 * </ul>
	 * </blockquote>
	 * 
	 * @param args The arguments, described above.
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws InvalidMidiDataException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 */
	public static void main(String[] args) throws InterruptedException, IOException, InvalidMidiDataException, ParserConfigurationException, SAXException {
		List<File> anacrusisFiles = new ArrayList<File>();
		boolean useChannel = true;
		File groundTruth = null;
		boolean joint = false;
		boolean mlm = false;
		List<File> grammarFiles = new ArrayList<File>();
		MetricalLpcfg grammar = new MetricalLpcfg();
		File xmlFile = null;
		File beatFile = null;
		
		// No args given
		if (args.length == 0) {
			argumentError("No arguments given");
		}
		
		for (int i = 0; i < args.length; i++) {
			switch (args[i].charAt(0)) {
				// ARGS
				case '-':
					if (args[i].length() == 1) {
						argumentError("Unrecognized option: " + args[i]);
					}
					
					switch (args[i].charAt(1)) {
						// Use track
						case 'T':
							useChannel = false;
							break;
							
						case 'w':
							i++;
							if (args.length == i) {
								argumentError("No window length given for -w option.");
							}
							try {
								BEAT_EPSILON = Long.parseLong(args[i]);
							} catch (NumberFormatException e) {
								argumentError("Exception reading window length. Must be an long: " + args[i]);
							}
							break;
							
						// Check Full
						case 'F':
							checkFull();
							break;
							
						case 's':
							i++;
							if (args.length == i) {
								argumentError("No sub beat length given for -s option.");
							}
							try {
								Main.SUB_BEAT_LENGTH = Integer.parseInt(args[i]);
							} catch (NumberFormatException e) {
								argumentError("Exception reading sub beat length. Must be an integer: " + args[i]);
							}
							break;
						
						// Evaluate!
						case 'E':
							i++;
							if (args.length <= i) {
								argumentError("No ground truth file given for -E option.");
							}
							if (groundTruth != null) {
								argumentError("-E or -J option can only be given once.");
							}
							groundTruth = new File(args[i]);
							if (!groundTruth.exists()) {
								argumentError("Ground truth file " + groundTruth + " does not exist.");
							}
							break;
							
							// Generate Temperley
						case 'G':
							i++;
							if (args.length <= i) {
								argumentError("No file given for -t option.");
							}
							
							File file = new File(args[i]);
							if (!file.exists()) {
								argumentError("File " + args[i] + " not found");
							}
							
							generateFromTemperley(file);
							break;
							
						// Get Joint Ground Truth probability
						case 'J':
							joint = true;
							i++;
							if (args.length <= i) {
								argumentError("No ground truth file given for -J option.");
							}
							if (groundTruth != null) {
								argumentError("-E or -J option can only be given once.");
							}
							groundTruth = new File(args[i]);
							if (!groundTruth.exists()) {
								argumentError("Ground truth file " + groundTruth + " does not exist.");
							}
							Main.SUB_BEAT_LENGTH = 4;
							Main.EXTEND_NOTES = true;
							Main.MIN_NOTE_LENGTH = 100000;
							break;
							
						// XML midi file for use with -J
						case 'X':
							i++;
							if (args.length <= i) {
								argumentError("No MIDI file given for -X option.");
							}
							xmlFile = new File(args[i]);
							if (!xmlFile.exists()) {
								argumentError("XML MIDI file " + xmlFile + " does not exist.");
							}
							break;
							
						// MIDI file to derive barline and beats from, for use with -J
						case 'B':
							i++;
							if (args.length <= i) {
								argumentError("No MIDI file given for -B option.");
							}
							beatFile = new File(args[i]);
							if (!beatFile.exists()) {
								argumentError("Beat MIDI file " + beatFile + " does not exist.");
							}
							break;
							
						// Load grammar (for -J)
						case 'g':
							i++;
							if (args.length == i) {
								argumentError("No grammar file given with -g option.");
							}
							grammarFiles.add(new File(args[i]));
							try {
								grammar.mergeGrammar(MetricalLpcfg.deserialize(grammarFiles.get(grammarFiles.size() - 1)));
							} catch (Exception e) {
								argumentError("Exception loading grammar file " + grammarFiles.get(grammarFiles.size() - 1) + ": " + e.getLocalizedMessage());
							}
							break;
							
						case 'v':
							VERBOSE = true;
							break;
							
						// Anacrusis files
						case 'a':
							if (args.length <= ++i) {
								argumentError("No Anacrusis Files given after -a");
							}
							file = new File(args[i]);
							if (!file.exists()) {
								argumentError("Anacrusis File " + args[i] + " not found");
							}
							anacrusisFiles.addAll(Main.getAllFilesRecursive(file));
							break;
							
						// Error
						default:
							argumentError("Unrecognized option: " + args[i]);
					}
					break;
					
				// Error
				default:
					argumentError("Unrecognized option: " + args[i]);
			}
		}
		
		if (groundTruth != null) {
			if (joint) {
				if (grammarFiles.isEmpty()) {
					argumentError("No grammar (-g) given with -J option.");
				}
				calculateJointGroundTruth(groundTruth, anacrusisFiles, useChannel, grammar, xmlFile, beatFile);
				
			} else {
				evaluateGroundTruth(groundTruth, anacrusisFiles, useChannel);
			}
		}
	}
	
	/**
	 * Calculate and print the probability of the given ground truth beats and hierarchy
	 * under a joint model.
	 * 
	 * @param groundTruth The ground truth file.
	 * @param anacrusisFiles The anacrusis files.
	 * @param useChannel Whether to use channel for MIDI.
	 * @throws InterruptedException 
	 * @throws InvalidMidiDataException 
	 * @throws SAXException 
	 * @throws ParserConfigurationException 
	 * @throws IOException 
	 */
	private static void calculateJointGroundTruth(File groundTruth, List<File> anacrusisFiles, boolean useChannel,
			MetricalLpcfg grammar, File xmlFile, File beatFile) throws IOException, ParserConfigurationException, SAXException, InvalidMidiDataException, InterruptedException {
		Evaluator evaluator = new Evaluator(groundTruth, anacrusisFiles, useChannel);
		
		Evaluator beatEval = null;
		if (beatFile != null) {
			beatEval = new Evaluator(beatFile, anacrusisFiles, useChannel);
		}
		
		MetricalLpcfg groundTruthGrammar = null;
		List<Beat> groundTruthTatums = beatEval == null ? evaluator.getTatums() : beatEval.getTatums();
		Measure groundTruthMeasure = beatEval == null ? evaluator.getHierarchy() : beatEval.getHierarchy();
		
		TimeTracker tt = new TimeTracker(Main.SUB_BEAT_LENGTH);
		tt.setAnacrusis(MetricalLpcfgGeneratorRunner.getAnacrusisLength(beatFile == null ? groundTruth : beatFile, anacrusisFiles));
		NoteListGenerator nlg = new NoteListGenerator(tt);
		
		// Perform voice separation, if needed (with -B beatFile)
		if (beatFile != null) {
			TimeTracker beatTt = new TimeTracker(Main.SUB_BEAT_LENGTH);
			beatTt.setAnacrusis(MetricalLpcfgGeneratorRunner.getAnacrusisLength(beatFile == null ? groundTruth : beatFile, anacrusisFiles));
			NoteListGenerator beatNlg = new NoteListGenerator(tt);
			Runner.parseFile(beatFile, beatNlg, beatTt, useChannel);
			
			
			TimeTracker gtTt = new TimeTracker(Main.SUB_BEAT_LENGTH);
			gtTt.setAnacrusis(MetricalLpcfgGeneratorRunner.getAnacrusisLength(beatFile == null ? groundTruth : beatFile, anacrusisFiles));
			NoteListGenerator gtNlg = new NoteListGenerator(tt);
			Runner.parseFile(groundTruth, gtNlg, gtTt, useChannel);
			
			HmmVoiceSplittingModel model = new HmmVoiceSplittingModel(new HmmVoiceSplittingModelParameters(true));
			Runner.performInference(model, gtNlg);
			
			int i = 0;
			groundTruth = new File("TMP_" + System.currentTimeMillis());
			MidiWriter writer = new MidiWriter(groundTruth, beatTt);
			for (Voice voice : model.getHypotheses().first().getVoices()) {
				for (MidiNote note : voice.getNotes()) {
					note.setCorrectVoice(i);
					writer.addMidiNote(note);
				}
				i++;
			}
			
			writer.write();
		}
		
		// PARSE!
		EventParser ep = null;
		if (groundTruth.toString().endsWith(".xml")) {
			if (xmlFile == null) {
				argumentError("XML MIDI file must be given with -X when using -J with an xml file.");
			}
			ep = Runner.parseFile(xmlFile, nlg, tt, useChannel);
			
			groundTruthTatums = evaluator.getTatums();
			
			MetricalLpcfgHierarchyModelState tmpHierarchy = new MetricalLpcfgHierarchyModelState(grammar);
			tmpHierarchy.addMatch(MetricalLpcfgMatch.SUB_BEAT);
			tmpHierarchy.addMatch(MetricalLpcfgMatch.BEAT);
			FromBeatsBeatTrackingModelState tmpBeats = new FromBeatsBeatTrackingModelState(groundTruthTatums);
			tmpHierarchy.setBeatState(tmpBeats);
			tmpBeats.setHierarchyState(tmpHierarchy);
			
			JointModel jm = new JointModel(
					new FromFileVoiceSplittingModelState(ep),
					tmpBeats,
					new MetricalLpcfgHierarchyModelState(tmpHierarchy,
							grammar, groundTruthMeasure, Main.SUB_BEAT_LENGTH, groundTruthMeasure.getAnacrusis()));
			
			MetricalLpcfgGeneratorRunner.VERBOSE = true;
			Runner.performInference(jm, nlg);
			groundTruthGrammar = ((MetricalLpcfgHierarchyModelState) jm.getHypotheses().first().getHierarchyState()).getLocalGrammar();
			System.out.println("Tatums = " + jm.getHypotheses().first().getBeatState().getBeats());
			
		} else if (groundTruth.toString().endsWith(".match")) {
			System.err.println("Tree printing unsupoorted from match file.");
			
			
			System.out.println("Measure = " + evaluator.getHierarchy());
			System.out.println("Tatums = " + evaluator.getTatums());
			return;
			
		} else {
			ep = Runner.parseFile(groundTruth, nlg, tt, useChannel);
			
			// RUN!
			JointModel jm = new JointModel(
					new FromFileVoiceSplittingModelState(ep),
					new FromFileBeatTrackingModelState(tt),
					new FromFileHierarchyModelState(tt));
			
			Runner.performInference(jm, nlg);
			
			if (beatFile != null && groundTruth.getName().startsWith("TMP_")) {
				groundTruth.delete();
			}
			
			// GRAMMARIZE
			MetricalLpcfgGenerator generator = new MetricalLpcfgGenerator();
			generator.parseSong(jm, tt);
			
			groundTruthGrammar = generator.getGrammar();
			/*
			for (MetricalLpcfgTree tree : groundTruthGrammar.getTrees()) {
				try {
					grammar.extractTree(tree);
				} catch (MetricalLpcfgElementNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}*/
		}
		
		double grammarLogProb = 0.0;
		double localLogProb = 0.0;
		
		MetricalLpcfg localGrammar = new MetricalLpcfg();
		
		for (MetricalLpcfgTree tree : groundTruthGrammar.getTrees()) {
			if (VERBOSE) {
				System.out.println(tree.toStringPretty(" "));
			}
			double logProb = grammar.getTreeLogProbability(tree);
			grammarLogProb += logProb;
			if (VERBOSE) {
				System.out.println("Global log probability = " + logProb);
			}
			if (!localGrammar.getTrees().isEmpty()) {
				logProb = localGrammar.getTreeLogProbability(tree);
				localLogProb += logProb;
				if (VERBOSE) {
					System.out.println("Local log probability = " + logProb);
				}
			}
			localGrammar.addTree(tree);
		}
		System.out.println("Hierarchy log probability: global = " + grammarLogProb + " local = " + localLogProb);
		System.out.println("Averages: global = " + grammarLogProb / groundTruthGrammar.getTrees().size() + " local = " + localLogProb / localGrammar.getTrees().size());
		
		// TODO: For beats, go through each bar, create a static method in HmmBeatTracker to calculate prob of tatums in beat (given previous tempo)
		//HmmBeatTrackingModelState.printProbabilities(groundTruthTatums, groundTruthMeasure, nlg);
	}
	
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
	private static void generateFromTemperley(File inputFile) throws IOException, InvalidMidiDataException, InterruptedException {
		long firstNoteTime = -1L;
		
		TimeTracker tt = new TimeTracker();
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
				// Tactus found
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
		
		FromOutputTimeTracker tt = new FromOutputTimeTracker(Main.SUB_BEAT_LENGTH);
		int numerator = beatsPerBar;
		int denominator = 4;
		
		if (subBeatsPerBeat == 3) {
			numerator *= 3;
			denominator = 8; 
		}
		tt.setTimeSignature(new TimeSignature(numerator, denominator));
		tt.setAnacrusisSubBeats(anacrusisLengthSubBeats);
		
		for (int i = 0; i < times.size(); i++) {
			if (i % tatumsPerSubBeat != 0) {
				continue;
			}
			
			tt.addBeat(times.get(i));
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
	
	/**
	 * Calculate mean and standard deviation of Voice, Beat, Downbeat, and Meter scores as produced by
	 * Evaluation -E, read from std in.
	 */
	private static void checkFull() {
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

	/**
	 * Evaluate the program output (from std in) with the given ground truth file.
	 * Prints the result to std out.
	 * 
	 * @param groundTruth The ground truth file (MIDI, **krn).
	 * @param useChannel 
	 * @param anacrusisFiles 
	 * @throws InterruptedException 
	 * @throws InvalidMidiDataException 
	 * @throws IOException 
	 * @throws SAXException 
	 * @throws ParserConfigurationException 
	 */
	private static void evaluateGroundTruth(File groundTruth, List<File> anacrusisFiles, boolean useChannel) throws IOException, InvalidMidiDataException, InterruptedException, ParserConfigurationException, SAXException {
		Evaluator evaluator = new Evaluator(groundTruth, anacrusisFiles, useChannel);
		
		if (evaluator.getHasTimeChange()) {
			System.err.println("Meter change detected. Skipping song " + groundTruth);
			return;
		}
		
		if (evaluator.getHierarchy().getBeatsPerMeasure() < 2 || evaluator.getHierarchy().getBeatsPerMeasure() > 4 ||
				evaluator.getHierarchy().getSubBeatsPerBeat() < 2 || evaluator.getHierarchy().getSubBeatsPerBeat() > 3) {
			System.err.println("Irregular meter detected (" + evaluator.getHierarchy().getBeatsPerMeasure() + "," +
				evaluator.getHierarchy().getSubBeatsPerBeat() + "). Skipping song " + groundTruth);
			return;
		}
		
		// Parse input
		String voices = null;
		String beats = null;
		String hierarchy = null;
		int found = 0;
		
		Scanner input = new Scanner(System.in);
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
		List<Voice> voiceList = parseVoices(voices);
		List<Beat> beatList = parseBeats(beats);
		Measure measure = parseHierarchy(hierarchy);
		
		// Get scores
		System.out.println(evaluator.evaluate(voiceList, beatList, measure));
		
		if (VERBOSE) {
			System.out.println("Average tatum length transcribed: " +
					((beatList.get(beatList.size() - 1).getTime() - beatList.get(0).getTime()) / (beatList.size() / 1)));
			System.out.println("Average beat length gt: " +
					((evaluator.getTatums().get(evaluator.getTatums().size() - 1).getTime() -
							evaluator.getTatums().get(0).getTime()) / (evaluator.getTatums().size() / 1)));
			System.out.println("First downbeat time transcribed: " + getFirstDownbeatTime(beatList));
			System.out.println("First downbeat time gt: " + getFirstDownbeatTime(evaluator.getTatums()));
			System.out.println("Hierarchy transcribed: " + measure);
			System.out.println("Hierarchy gt: " + evaluator.getHierarchy());
		}
	}

	private static long getFirstDownbeatTime(List<Beat> beatList) {
		for (Beat beat : beatList) {
			if (beat.getTatum() == 0) {
				return beat.getTime();
			}
		}
		
		return -1L;
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
		Pattern beatPattern = Pattern.compile("(-?[0-9]+)\\.([0-9]+),(-?[0-9]+)");
		String[] splitBeats = beats.split("\\),\\(");
		splitBeats[0] = splitBeats[0].replace("[(", "");
		splitBeats[splitBeats.length - 1] = splitBeats[splitBeats.length - 1].substring(0, splitBeats[splitBeats.length - 1].indexOf(')'));
		
		List<Beat> beatsList = new ArrayList<Beat>(splitBeats.length);
		
		for (String beatString : splitBeats) {
			Matcher beatMatcher = beatPattern.matcher(beatString);
			
			if (beatMatcher.matches()) {
				int bar = Integer.parseInt(beatMatcher.group(1));
				int tatum = Integer.parseInt(beatMatcher.group(2));
				int time = Integer.parseInt(beatMatcher.group(3));
				
				Beat beat = new Beat(bar, tatum, time, time);
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
		Pattern hierarchyPattern = Pattern.compile("M_([0-9]+),([0-9]+) length=([0-9]+) anacrusis=([0-9]+).*");

		Matcher hierarchyMatcher = hierarchyPattern.matcher(hierarchy);
		Measure measure = null;
		if (hierarchyMatcher.matches()) {
			int beatsPerBar = Integer.parseInt(hierarchyMatcher.group(1));
			int subBeatsPerBeat = Integer.parseInt(hierarchyMatcher.group(2));
			int length = Integer.parseInt(hierarchyMatcher.group(3));
			int anacrusis = Integer.parseInt(hierarchyMatcher.group(4));
			
			measure = new Measure(beatsPerBar, subBeatsPerBeat, length, anacrusis);
			
		} else {
			throw new IOException("Hierarchy results malformed");
		}
		
		return measure;
	}
	
	/**
	 * Get the accuracy String for a metrical hypothesis.
	 *
	 * @param correctMeasure The correct measure.
	 * @param correctSubBeatLength The correct sub beat length, in ticks.
	 * @param correctAnacrusisLength The correct anacrusis length, in ticks.
	 * @param hypothesisMeasure The hypothesis measure.
	 * @param hypothesisSubBeatLength The hypothesis sub beat length, in ticks.
	 * @param hypothesisAnacrusisLength The hypothesis anacrusis length, in ticks.
	 * 
	 * @return The accuracy string for the given hypothesis.
	 */
	public static String getAccuracyString(Measure correctMeasure, int correctSubBeatLength, int correctAnacrusisLength,
			Measure hypothesisMeasure, int hypothesisSubBeatLength, int hypothesisAnacrusisLength) {
		
		if (hypothesisMeasure.equals(correctMeasure) &&
				hypothesisAnacrusisLength == correctAnacrusisLength &&
				hypothesisSubBeatLength == correctSubBeatLength) {
			return "TP = 3\nFP = 0\nFN = 0\nP = 1.0\nR = 1.0\nF1 = 1.0";
		}
		
		int truePositives = 0;
		int falsePositives = 0;
		
		// Sub beat
		int length = hypothesisSubBeatLength;
		int offset = 0;
		
		int match = getMatch(length, offset, correctMeasure, correctSubBeatLength, correctAnacrusisLength);
		if (match > 0) {
			truePositives++;
			
		} else if (match < 0) {
			falsePositives++;
		}
		
		// Beat
		length *= hypothesisMeasure.getSubBeatsPerBeat();
		offset = hypothesisAnacrusisLength % length;
		
		match = getMatch(length, offset, correctMeasure, correctSubBeatLength, correctAnacrusisLength);
		if (match > 0) {
			truePositives++;
			
		} else if (match < 0) {
			falsePositives++;
		}
		
		// Measure
		length *= hypothesisMeasure.getBeatsPerMeasure();
		offset = hypothesisAnacrusisLength;
		
		match = getMatch(length, offset, correctMeasure, correctSubBeatLength, correctAnacrusisLength);
		if (match > 0) {
			truePositives++;
			
		} else if (match < 0) {
			falsePositives++;
		}
		
		int falseNegatives = 3 - truePositives;
		
		double precision = ((double) truePositives) / (truePositives + falsePositives);
		double recall = ((double) truePositives) / (truePositives + falseNegatives);
		
		double fMeasure = 2 * precision * recall / (precision + recall);
		
		if (Double.isNaN(fMeasure)) {
			fMeasure = 0.0;
		}
		
		StringBuilder sb = new StringBuilder();
		
		sb.append("TP = ").append(truePositives).append('\n');
		sb.append("FP = ").append(falsePositives).append('\n');
		sb.append("FN = ").append(falseNegatives).append('\n');
		sb.append("P = ").append(precision).append('\n');
		sb.append("R = ").append(recall).append('\n');
		sb.append("F1 = ").append(fMeasure);
		
		return sb.toString();
	}
	
	/**
	 * Get the match type of a grouping of the given length and offset given the correct measure,
	 * anacrusis length, and sub beat length.
	 * 
	 * @param length The length of the grouping we want to check.
	 * @param offset The offset of the grouping we want to check.
	 * @param correctMeasure The correct measure of this song.
	 * @param correctSubBeatLength The correct sub beat length.
	 * @param correctAnacrusisLength The correct anacrusis length, measured in tacti.
	 * 
	 * @return A value less than 0 if this grouping overlaps some correct tree boundary. A value
	 * greater than 0 if this grouping matches a correct tree boundary exactly. A value of 0
	 * otherwise, for example if the grouping lies under the lowest grouping, but could be grouped
	 * up into a correct grouping.
	 */
	private static int getMatch(int length, int offset, Measure correctMeasure, int correctSubBeatLength,
			int correctAnacrusisLength) {
		// Sub beat
		int correctLength = correctSubBeatLength;
		int correctOffset = correctAnacrusisLength % correctLength;
		
		if (correctLength == length) {
			return correctOffset == offset ? 1 : -1;
			
		} else if (correctLength < length) {
			if ((offset - correctOffset) % correctLength != 0 || (offset + length - correctOffset) % correctLength != 0) {
				// We don't match up with both the beginning and the end
				return -1;
			}
			
		} else {
			// correctLength > length
			if ((correctOffset - offset) % length != 0 || (correctOffset + correctLength - offset) % length != 0) {
				// We don't match up with both the beginning and the end
				return -1;
			}
		}

		// Beat
		correctLength *= correctMeasure.getSubBeatsPerBeat();
		correctOffset = correctAnacrusisLength % correctLength;
		
		if (correctLength == length) {
			return correctOffset == offset ? 1 : -1;
			
		} else if (correctLength < length) {
			if ((offset - correctOffset) % correctLength != 0 || (offset + length - correctOffset) % correctLength != 0) {
				// We don't match up with both the beginning and the end
				return -1;
			}
			
		} else {
			// correctLength > length
			if ((correctOffset - offset) % length != 0 || (correctOffset + correctLength - offset) % length != 0) {
				// We don't match up with both the beginning and the end
				return -1;
			}
		}
		
		// Measure
		correctLength *= correctMeasure.getBeatsPerMeasure();
		correctOffset = correctAnacrusisLength;
		
		if (correctLength == length) {
			return correctOffset == offset ? 1 : -1;
			
		} else if (correctLength < length) {
			if ((offset - correctOffset) % correctLength != 0 || (offset + length - correctOffset) % correctLength != 0) {
				// We don't match up with both the beginning and the end
				return -1;
			}
			
		} else {
			// correctLength > length
			if ((correctOffset - offset) % length != 0 || (correctOffset + correctLength - offset) % length != 0) {
				// We don't match up with both the beginning and the end
				return -1;
			}
		}
		
		return 0;
	}
	
	/**
	 * Some argument error occurred. Print the message to std err and exit.
	 * 
	 * @param message The message to print to std err.
	 */
	private static void argumentError(String message) {
		StringBuilder sb = new StringBuilder("Evaluation: Argument error: ");
		
		sb.append(message).append('\n');
		
		sb.append("Usage: java -cp bin metalign.utils.Evaluation ARGS\n");

		sb.append("-E FILE = Evaluate the Main output (from std in) given the ground truth FILE.\n");
		sb.append("-F = Calculate means and standard deviations of the -E FILE results (read from std in).\n");
		sb.append("-w INT = Use the given INT as the window length for accepted grouping matches, in microseconds.\n");
		sb.append("-T = Use tracks as correct voice (instead of channels) *Only used for MIDI files.\n");
		sb.append("-s INT = Use INT as the sub beat length.\n");
		sb.append("-a FILE = Search recursively under the given FILE for anacrusis files.\n");
		
		System.err.println(sb.toString());
		System.exit(1);
	}
}
