package metalign;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import metalign.beat.BeatTrackingModelState;
import metalign.beat.fromfile.FromFileBeatTrackingModelState;
import metalign.beat.hmm.HmmBeatTrackingModelParameters;
import metalign.beat.hmm.HmmBeatTrackingModelState;
import metalign.hierarchy.HierarchyModelState;
import metalign.hierarchy.fromfile.FromFileHierarchyModelState;
import metalign.hierarchy.lpcfg.MetricalLpcfg;
import metalign.hierarchy.lpcfg.MetricalLpcfgElementNotFoundException;
import metalign.hierarchy.lpcfg.MetricalLpcfgHierarchyModelState;
import metalign.joint.JointModel;
import metalign.joint.JointModelState;
import metalign.parsing.EventParser;
import metalign.parsing.NoteListGenerator;
import metalign.time.TimeTracker;
import metalign.utils.Evaluator;
import metalign.voice.VoiceSplittingModelState;
import metalign.voice.fromfile.FromFileVoiceSplittingModelState;
import metalign.voice.hmm.HmmVoiceSplittingModelParameters;
import metalign.voice.hmm.HmmVoiceSplittingModelState;

/**
 * The <code>Main</code> class is used to perform any function of this project.
 * This is the main interface which should be used to test any of the functionality
 * of the beat tracking project.
 * <p>
 * Usage can be found in the javadoc comments for the {@link #main(String[])} method.
 *
 * @author Andrew McLeod - 11 Feb, 2015
 */
public class Main {

	public static boolean EXTEND_NOTES = true;

	public static boolean VERBOSE = false;

	public static boolean SUPER_VERBOSE = false;

	public static boolean TESTING = false;

	public static boolean LOG_STATUS = false;

	public static Evaluator EVALUATOR = null;

	public static int BEAM_SIZE = 200;

	public static int SUB_BEAT_LENGTH = 4;

	public static int MIN_NOTE_LENGTH = 100000;

	public static int NUM_FROM_FILE = 0;

	public static boolean USE_CONGRUENCE = true;

	public static boolean TS_CHECK = true;

	/**
	 * The main method for running this program. Run with no args to print help.
	 *
	 * @param args String array with the command line args. Run with no args to print help.
	 *
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 * @throws InvalidMidiDataException
	 */
	public static void main(String[] args) throws InterruptedException, IOException, ParserConfigurationException, SAXException, InvalidMidiDataException {
		boolean useChannel = true;
		List<File> files = new ArrayList<File>();
		List<File> anacrusisFiles = new ArrayList<File>();
		String voiceClass = Runner.DEFAULT_VOICE_SPLITTER;
		String beatClass = Runner.DEFAULT_BEAT_TRACKER;
		String hierarchyClass = Runner.DEFAULT_HIERARCHY_MODEL;
		List<File> grammarFiles = new ArrayList<File>();
		File groundTruth = null;
		MetricalLpcfg grammar = new MetricalLpcfg();
		boolean extract = false;

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

						case 'X':
							TS_CHECK = false;
							break;

						case 'c':
							USE_CONGRUENCE = false;
							break;

						case 'l':
							LOG_STATUS = true;
							break;

						case 's':
							i++;
							if (args.length == i) {
								argumentError("No sub beat length given with -s option.");
							}
							try {
								SUB_BEAT_LENGTH = Integer.parseInt(args[i]);
							} catch (NumberFormatException e) {
								argumentError("Exception reading sub beat length. Must be an integer: " + args[i]);
							}
							break;

						case 'm':
							i++;
							if (args.length == i) {
								argumentError("No minimum note length given with -m option.");
							}
							try {
								MIN_NOTE_LENGTH = Integer.parseInt(args[i]);
							} catch (NumberFormatException e) {
								argumentError("Exception reading minimum note length. Must be an integer: " + args[i]);
							}
							break;

						// Extract from grammar
						case 'x':
							extract = true;
							break;

						// Extend notes
						case 'e':
							EXTEND_NOTES = true;
							break;

						case 'f':
							EXTEND_NOTES = false;
							break;

						case 'L':
							i++;
							if (args.length == i) {
								argumentError("No local weight given with -L option.");
							}
							try {
								MetricalLpcfgHierarchyModelState.LOCAL_WEIGHT = Double.parseDouble(args[i]);
							} catch (NumberFormatException e) {
								argumentError("Exception reading local weight. Must be a double: " + args[i]);
							}
							break;

						// Anacrusis files
						case 'a':
							if (args.length <= ++i) {
								argumentError("No Anacrusis Files given after -a");
							}
							File file = new File(args[i]);
							if (!file.exists()) {
								argumentError("Anacrusis File " + args[i] + " not found");
							}
							anacrusisFiles.addAll(getAllFilesRecursive(file));
							break;

						// Verbose printing
						case 'p':
							VERBOSE = true;
							break;

						// Super verbose
						case 'P':
							SUPER_VERBOSE = true;
							VERBOSE = true;
							break;

						// Beat tracking class
						case 'B':
							if (args[i].length() == 2) {
								argumentError("No Class given for -B. (There should be no space between -B and the Class name).");
							}
							beatClass = args[i].substring(2);
							break;

						// Hierarchy detection class
						case 'H':
							if (args[i].length() == 2) {
								argumentError("No Class given for -H. (There should be no space between -H and the Class name).");
							}
							hierarchyClass = args[i].substring(2);
							break;

						// Grammar file
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

						// Also print evaluation (give ground truth file)
						case 'E':
							i++;
							if (args.length == i) {
								argumentError("No ground truth file given with -E option.");
							}
							groundTruth = new File(args[i]);
							if (!groundTruth.exists()) {
								argumentError("Ground truth file " + groundTruth + " not found.");
							}
							break;

						// Beam size
						case 'b':
							i++;
							if (args.length == i) {
								argumentError("No beam size given with -b option.");
							}
							try {
								BEAM_SIZE = Integer.parseInt(args[i]);
							} catch (NumberFormatException e) {
								argumentError("Exception reading beam size. Must be an integer: " + args[i]);
							}
							break;

						// Error
						default:
							argumentError("Unrecognized option: " + args[i]);
					}
					break;

				// File or directory name
				default:
					File file = new File(args[i]);
					if (!file.exists()) {
						argumentError("File " + args[i] + " not found");
					}
					files.addAll(getAllFilesRecursive(file));
			}
		}

		// Set up global vars
		if (groundTruth != null) {
			EVALUATOR = new Evaluator(groundTruth, anacrusisFiles, useChannel);
		}

		if (files.isEmpty()) {
			argumentError("No files found");
		}

		// Set up testing models
		validateModelClasses(voiceClass, beatClass, hierarchyClass);

		if ("lpcfg".equalsIgnoreCase(hierarchyClass) && grammarFiles.size() == 0) {
			argumentError("No grammar given with -Hlpcfg option. Use -g FILE to specify a grammar.");
		}

		// Print out chosen options
		if (VERBOSE) {
			System.out.println((SUPER_VERBOSE ? "Super " : "") + "Verbose mode");

			System.out.println((EVALUATOR == null ? "Not evaluating" : "Evaluating") + " hypotheses from ground truth " + groundTruth);
			System.out.println("Using " + (useChannel ? "channels" : "tracks") + " for gold standard voices.");
			System.out.println("Using beats input from Temperley Std in.");

			System.out.println("Using voice separation class " + voiceClass);
			System.out.println("Using beat tracking class " + beatClass);
			System.out.println("Using hierarchy class " + hierarchyClass);

			if ("lpcfg".equalsIgnoreCase(hierarchyClass)) {
				System.out.println("Using grammar files " + grammarFiles);

				if (SUPER_VERBOSE) {
					System.out.println(grammar.getProbabilityTracker());
				}

				System.out.println((extract ? "Extracting" : "Not extracting") + " trees from grammar");
			}

			System.out.println("Using sub beat length " + SUB_BEAT_LENGTH);
			System.out.println("Using beam size " + BEAM_SIZE);

			System.out.println((EXTEND_NOTES ? "Extending" : "Not extending") + " notes through rests");

			System.out.println("Testing on files: " + files);
			System.out.println("Using anacrusis files: " + anacrusisFiles);
		}

		// Test
		for (File file : files) {
			System.out.println("File: " + file);

			TimeTracker tt = new TimeTracker(SUB_BEAT_LENGTH);
			NoteListGenerator nlg = new NoteListGenerator(tt);
			EventParser ep;

			try {
				ep = Runner.parseFile(file, nlg, tt, useChannel);

			} catch (IOException | InvalidMidiDataException e) {
				System.err.println("Error parsing file " + file + ":\n" + e.getLocalizedMessage());

				if (VERBOSE) {
					e.printStackTrace();
				}

				continue;
			}

			tt.setFirstNoteTime(nlg.getNoteList().get(0).getOnsetTime());
			if (TS_CHECK && tt.getAllTimeSignatures().size() != 1) {
				System.err.println("Time change detected. Skipping song " + file);
				System.err.println("It is recommended to split files on time changes, since the model " +
								   "cannot output them. This check can be overriden with " +
								   "the -X flag.");
				continue;
			}

			if (TS_CHECK && (tt.getFirstTimeSignature().getMetricalMeasure().getBeatsPerMeasure() < 2 || tt.getFirstTimeSignature().getMetricalMeasure().getBeatsPerMeasure() > 4 ||
					tt.getFirstTimeSignature().getMetricalMeasure().getSubBeatsPerBeat() < 2 || tt.getFirstTimeSignature().getMetricalMeasure().getSubBeatsPerBeat() > 3)) {
				System.err.println("Irregular meter detected (" + tt.getFirstTimeSignature().getMetricalMeasure().getBeatsPerMeasure() + "," +
					tt.getFirstTimeSignature().getMetricalMeasure().getSubBeatsPerBeat() + "). Skipping song " + file);
				System.err.println("It is recommended to skip files with irregular time signatures, since the model " +
								   "cannot output them. This check can be overriden with " +
								   "the -X flag.");
				continue;
			}

			JointModel jm = null;

			if (grammar != null && extract) {
				try {
					grammar.extract(file, anacrusisFiles, useChannel);
				} catch (IOException | InvalidMidiDataException
						| MetricalLpcfgElementNotFoundException e) {
					System.err.println("Error parsing file " + file +
							" for grammar extraction:\n" + e.getLocalizedMessage());

					if (VERBOSE) {
						e.printStackTrace();
					}

					continue;
				}
			}

			try {
				jm = getJointModel(voiceClass, beatClass, hierarchyClass, ep, tt, grammar);

			} catch (InvalidMidiDataException e) {
				System.err.println("Error parsing file " + file + ":\n" + e.getLocalizedMessage());

				if (VERBOSE) {
					e.printStackTrace();
				}

				System.exit(1);
			}

			TESTING = true;
			Runner.performInference(jm, nlg);
			TESTING = false;

			if (grammar != null && extract) {
				try {
					grammar.unextract(file, anacrusisFiles, useChannel);
				} catch (IOException | InvalidMidiDataException e) {
					System.err.println("Error parsing file " + file +
							" for grammar extraction:\n" + e.getLocalizedMessage());

					if (VERBOSE) {
						e.printStackTrace();
					}

					continue;
				}
			}

			if (VERBOSE) {
				// Print all choices
				for (JointModelState jms : jm.getHypotheses()) {
					System.out.println(jms.getVoiceState());
					System.out.println(jms.getBeatState());
					System.out.println(jms.getHierarchyState());

					// Also print score for each
					if (EVALUATOR != null) {
						System.out.println(EVALUATOR.evaluate(jms));
					}
				}

			} else {
				// Print only top choices
				if (jm.getHypotheses().isEmpty()) {
					System.out.println("No output generated. This is likely for one of 2 reasons:");
					System.out.println();
					System.out.println(" 1. The input piece's notes are not in monophonic voices " +
									   "(small overlaps between notes are fine).");
					System.out.println("   - To solve this, use my voice splitter as described in the README, " +
					                   "Project Overview section.");
					System.out.println(" 2. The input is heavily syncopated. The model by default applies the " +
					                   "Rule of Congruence (see the SMC publication), which aggresively " +
									   "eliminates hypotheses that look unlikely at the beginning.");
					System.out.println("   - If you are using -BFromFile to use the ground truth 32nd-note tatum, " +
									   "removing this option should solve the issue, although the model's output will " +
									   "no longer be locked to the 32nd-note pulse.");
					System.out.println("   - Use can also add the -c flag to disable this rule (NOT RECOMMENDED).");
					System.out.println("   - You should also widen the beam when using this option. (e.g., using \"-b 500\")");
				} else {
					System.out.println("Voices: " + jm.getHypotheses().first().getVoiceState());
					System.out.println("Beats: " + jm.getHypotheses().first().getBeatState());
					System.out.println("Hierarchy: " + jm.getHypotheses().first().getHierarchyState());
					System.out.println("Tatum times: " + jm.getHypotheses().first().getBeatState().getTatumTimesString());
					System.out.println("Sub-beat times: " + jm.getHypotheses().first().getBeatState().getSubBeatTimesString());
					System.out.println("Beat times: " + jm.getHypotheses().first().getBeatState().getBeatTimesString());
					System.out.println("Downbeat times: " + jm.getHypotheses().first().getBeatState().getDownbeatTimesString());
				}
			}
		}
	}

	/**
	 * Ensure that the given model class Strings are valid. This method will halt exectution of the code if
	 * the any of them are not valid.
	 *
	 * @param voiceClass The voice class String.
	 * @param beatClass The beat class String.
	 * @param hierarchyClass The hierarchy class String.
	 */
	private static void validateModelClasses(String voiceClass, String beatClass, String hierarchyClass) {
		validateVoiceClass(voiceClass);
		validateBeatClass(beatClass);
		validateHierarchyClass(hierarchyClass);
	}

	/**
	 * Ensure that the given voice class String is valid. This method will halt exectution of the code if
	 * it is not valid.
	 *
	 * @param voiceClass The voice class String.
	 */
	private static void validateVoiceClass(String voiceClass) {
		if (!"Hmm".equalsIgnoreCase(voiceClass) && !"FromFile".equalsIgnoreCase(voiceClass)) {
			argumentError("Unrecognized voice splitter " + voiceClass + ".\n" +
					"Possible values are FromFile (default) or Hmm");
		}
	}

	/**
	 * Ensure that the given beat class String is valid. This method will halt exectution of the code if
	 * it is not valid.
	 *
	 * @param beatClass The beat class String.
	 */
	private static void validateBeatClass(String beatClass) {
		if (!"Hmm".equalsIgnoreCase(beatClass) && !"FromFile".equalsIgnoreCase(beatClass)) {
			argumentError("Unrecognized beat tracker " + beatClass + ".\n" +
					"Possible values are FromFile (default) or Hmm");
		}
	}

	/**
	 * Ensure that the given hierarchy class String is valid. This method will halt exectution of the code if
	 * it is not valid.
	 *
	 * @param hierarchyClass The hierarchy class String.
	 */
	private static void validateHierarchyClass(String hierarchyClass) {
		if (!"lpcfg".equalsIgnoreCase(hierarchyClass) && !"FromFile".equalsIgnoreCase(hierarchyClass)) {
			argumentError("Unrecognized hierarchy model " + hierarchyClass + ".\n" +
					"Possible values are FromFile (default) or LPCFG");
		}
	}

	/**
	 * Get a joint model given the model class Strings (alread validated with {@link #validateModelClasses(String, String, String)}),
	 * an event parser, and a time tracker.
	 *
	 * @param voiceClass The voice class String.
	 * @param beatClass The beat class String.
	 * @param hierarchyClass The hierarchy class String.
	 * @param ep The event parser.
	 * @param tt The time tracker.
	 *
	 * @return A joint model which can be used to perform inference jointly.
	 * @throws InvalidMidiDataException
	 */
	private static JointModel getJointModel(String voiceClass, String beatClass, String hierarchyClass, EventParser ep, TimeTracker tt, MetricalLpcfg grammar)
			throws InvalidMidiDataException {
		VoiceSplittingModelState vs = getVoiceState(voiceClass, ep);
		BeatTrackingModelState bs = getBeatState(beatClass, tt);
		HierarchyModelState hs = getHierarchyState(hierarchyClass, tt, grammar);

		return new JointModel(vs, bs, hs);
	}

	/**
	 * Load and return a voice state given the voice class String.
	 *
	 * @param voiceClass The voice class String.
	 * @param ep An event parser, used in case the voice class String is FromFile (default).
	 *
	 * @return The voice state requested by the voice class String.
	 * @throws InvalidMidiDataException
	 */
	private static VoiceSplittingModelState getVoiceState(String voiceClass, EventParser ep) throws InvalidMidiDataException {
		if ("Hmm".equalsIgnoreCase(voiceClass)) {
			return new HmmVoiceSplittingModelState(new HmmVoiceSplittingModelParameters(true));
		}

		NUM_FROM_FILE++;
		return new FromFileVoiceSplittingModelState(ep);
	}

	/**
	 * Load and return a beat state given the beat class String.
	 *
	 * @param beatClass The beat class String.
	 * @param tt A time tracker, used in case the beat class String is FromFile (default).
	 *
	 * @return The beat state requested by the beat class String.
	 */
	private static BeatTrackingModelState getBeatState(String beatClass, TimeTracker tt) {
		if ("Hmm".equalsIgnoreCase(beatClass)) {
			return new HmmBeatTrackingModelState(new HmmBeatTrackingModelParameters());
		}

		NUM_FROM_FILE++;
		return new FromFileBeatTrackingModelState(tt);
	}

	/**
	 * Load and return a hierarchy state given the hierarchy class String.
	 *
	 * @param hierarchyClass The hierarchy class String.
	 * @param tt A time tracker, used in case the hierarchy class String is FromFile (default).
	 *
	 * @return The hierarchy state requested by the hierarchy class String.
	 */
	private static HierarchyModelState getHierarchyState(String hierarchyClass, TimeTracker tt, MetricalLpcfg grammar) {
		if ("lpcfg".equalsIgnoreCase(hierarchyClass)) {
			return new MetricalLpcfgHierarchyModelState(grammar);
		}

		NUM_FROM_FILE++;
		return new FromFileHierarchyModelState(tt);
	}

	/**
	 * Get and return a List of every file beneath the given one.
	 *
	 * @param file The head File.
	 * @return A List of every File under the given head.
	 */
	public static List<File> getAllFilesRecursive(File file) {
		List<File> files = new ArrayList<File>();

		if (file.isFile()) {
			files.add(file);

		} else if (file.isDirectory()) {
			File[] fileList = file.listFiles();
			if (fileList != null) {
				for (File subFile : fileList) {
					files.addAll(getAllFilesRecursive(subFile));
				}
			}
		}

		Collections.sort(files);
		return files;
	}

	/**
	 * An argument error occurred. Print the usage help info to standard error, and then exit.
	 * <p>
	 * NOTE: This method calls <code>System.exit(1)</code> and WILL NOT return.
	 *
	 * @param message The error message to print at the beginning of the exception.
	 */
	private static void argumentError(String message) {
		ArgumentException ae = new ArgumentException(message);
		System.err.println(ae.getLocalizedMessage());
		System.exit(1);
	}
}
