package metalign.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import metalign.Main;
import metalign.Runner;
import metalign.beat.Beat;
import metalign.beat.fromfile.FromFileBeatTrackingModelState;
import metalign.hierarchy.Measure;
import metalign.hierarchy.fromfile.FromFileHierarchyModelState;
import metalign.hierarchy.lpcfg.MetricalLpcfg;
import metalign.hierarchy.lpcfg.MetricalLpcfgGenerator;
import metalign.hierarchy.lpcfg.MetricalLpcfgGeneratorRunner;
import metalign.hierarchy.lpcfg.MetricalLpcfgHierarchyModelState;
import metalign.hierarchy.lpcfg.MetricalLpcfgTree;
import metalign.joint.JointModel;
import metalign.parsing.EventParser;
import metalign.parsing.NoteListGenerator;
import metalign.parsing.OutputParser;
import metalign.time.MidiTimeTracker;
import metalign.time.TimeTracker;
import metalign.voice.Voice;
import metalign.voice.fromfile.FromFileVoiceSplittingModelState;

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
		boolean joint = false;
		List<File> grammarFiles = new ArrayList<File>();
		MetricalLpcfg grammar = new MetricalLpcfg();
		File groundTruth = null;
		File file = null;
		
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
							OutputParser.checkFull();
							return;
						
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
							
						// Generate Temperley
						case 'G':
							i++;
							if (args.length <= i) {
								argumentError("No file given for -G option.");
							}
							
							file = new File(args[i]);
							if (!file.exists()) {
								argumentError("File " + args[i] + " not found");
							}
							
							Temperley.generateFromTemperley(file);
							return;
							
						// Notefile generation
						case 'n':
							i++;
							if (args.length <= i) {
								argumentError("No file given for -n option.");
							}
							
							file = new File(args[i]);
							if (!file.exists()) {
								argumentError("File " + args[i] + " not found");
							}
							
							System.out.println(Temperley.getNoteFileString(file));
							return;
							
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
				calculateJointGroundTruth(groundTruth, anacrusisFiles, useChannel, grammar);
				
			} else {
				evaluateGroundTruth(groundTruth, anacrusisFiles, useChannel);
			}
		} else {
			argumentError("No options given:");
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
	private static void calculateJointGroundTruth(File groundTruth, List<File> anacrusisFiles, boolean useChannel, MetricalLpcfg grammar) throws IOException, ParserConfigurationException, SAXException, InvalidMidiDataException, InterruptedException {
		MetricalLpcfg groundTruthGrammar = null;
		
		TimeTracker tt = new MidiTimeTracker();
		tt.setAnacrusis(MetricalLpcfgGeneratorRunner.getAnacrusisLength(groundTruth, anacrusisFiles));
		NoteListGenerator nlg = new NoteListGenerator(tt);
		
		// PARSE!
		EventParser ep = Runner.parseFile(groundTruth, nlg, tt, useChannel);
			
		// RUN!
		JointModel jm = new JointModel(
				new FromFileVoiceSplittingModelState(ep),
				new FromFileBeatTrackingModelState(tt),
				new FromFileHierarchyModelState(tt));
		
		Runner.performInference(jm, nlg);
		
		// GRAMMARIZE
		MetricalLpcfgGenerator generator = new MetricalLpcfgGenerator();
		generator.parseSong(jm, tt);
		
		groundTruthGrammar = generator.getGrammar();
		
		double grammarLogProb = 0.0;
		double localGrammarLogProb = 0.0;
		MetricalLpcfg localGrammar = new MetricalLpcfg();
		
		for (MetricalLpcfgTree tree : groundTruthGrammar.getTrees()) {
			if (VERBOSE) {
				System.out.println(tree.toStringPretty(" "));
			}
			double logProb = grammar.getTreeLogProbability(tree);
			grammarLogProb += logProb;
			
			double localLogProb = 0.0;
			if (!localGrammar.getTrees().isEmpty()) {
				localLogProb = localGrammar.getTreeLogProbability(tree);
				localGrammarLogProb += localLogProb;
			}
			localGrammar.addTree(tree);
			
			if (VERBOSE) {
				System.out.println("Log probability = " + (logProb * MetricalLpcfgHierarchyModelState.GLOBAL_WEIGHT) + " + " + (localLogProb * (1.0 - MetricalLpcfgHierarchyModelState.GLOBAL_WEIGHT)));
			}
		}
		System.out.println("Hierarchy log probability = " + (grammarLogProb * MetricalLpcfgHierarchyModelState.GLOBAL_WEIGHT) + " + " + (localGrammarLogProb * (1.0 - MetricalLpcfgHierarchyModelState.GLOBAL_WEIGHT)) + " = " +
				(MetricalLpcfgHierarchyModelState.GLOBAL_WEIGHT * grammarLogProb + (1.0 - MetricalLpcfgHierarchyModelState.GLOBAL_WEIGHT) * localGrammarLogProb));
		
		// TODO: For beats, go through each bar, create a static method in HmmBeatTracker to calculate prob of tatums in beat (given previous tempo)
		
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
		
		if (evaluator.getHierarchy().getBeatsPerBar() < 2 || evaluator.getHierarchy().getBeatsPerBar() > 4 ||
				evaluator.getHierarchy().getSubBeatsPerBeat() < 2 || evaluator.getHierarchy().getSubBeatsPerBeat() > 3) {
			System.err.println("Irregular meter detected (" + evaluator.getHierarchy().getBeatsPerBar() + "," +
				evaluator.getHierarchy().getSubBeatsPerBeat() + "). Skipping song " + groundTruth);
			return;
		}
		
		OutputParser op = new OutputParser();
		
		List<Voice> voiceList = op.getVoices();
		List<Beat> beatList = op.getBeats();
		Measure measure = op.getMeasure();
		
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
			if (beat.isDownbeat()) {
				return beat.getTime();
			}
		}
		
		return -1L;
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
		sb.append("-G FILE = Generate our output format from Temperley's output format (from Standard in), given the ground truth file FILE.\n");
		sb.append("-n FILE = Generate a notefile (for input to Temperley) from the given FILE.\n");
		
		System.err.println(sb.toString());
		System.exit(1);
	}
}
