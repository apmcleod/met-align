package metalign.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import metalign.Main;
import metalign.beat.Beat;
import metalign.hierarchy.Measure;
import metalign.parsing.OutputParser;
import metalign.voice.Voice;

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
	 * The main method for evaluating results. Run with no arguments to print help.
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
			evaluateGroundTruth(groundTruth, anacrusisFiles, useChannel);
		} else {
			argumentError("No options given:");
		}
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
			if (beat.getTatum() == 0) {
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
		sb.append("-s INT = Use INT as the sub beat length. Defaults to 4.\n");
		sb.append("-a FILE = Search recursively under the given FILE for anacrusis files.\n");
		sb.append("-G FILE = Generate our output format from Temperley's output format (from Standard in), given the ground truth file FILE.\n");
		sb.append("-n FILE = Generate a notefile (for input to Temperley) from the given FILE.\n");
		
		System.err.println(sb.toString());
		System.exit(1);
	}
}
