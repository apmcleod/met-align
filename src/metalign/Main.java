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
import metalign.hierarchy.lpcfg.MetricalLpcfgGeneratorRunner;
import metalign.hierarchy.lpcfg.MetricalLpcfgHierarchyModelState;
import metalign.joint.JointModel;
import metalign.joint.JointModelState;
import metalign.parsing.EventParser;
import metalign.parsing.MidiWriter;
import metalign.parsing.NoteListGenerator;
import metalign.time.MidiTimeTracker;
import metalign.time.TimeTracker;
import metalign.utils.Evaluator;
import metalign.utils.MidiNote;
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
	
	public static boolean EXTEND_NOTES = false;

	public static boolean VERBOSE = false;
	
	public static boolean SUPER_VERBOSE = false;
	
	public static boolean TESTING = false;
	
	public static boolean LOG_STATUS = false;
	
	public static Evaluator EVALUATOR = null;
	
	public static int BEAM_SIZE = 200;
	
	public static int VOICE_BEAM_SIZE = -1;
	
	public static int MIN_NOTE_LENGTH = -1;
	
	public static int NUM_FROM_FILE = 0;
	
	public static int SUB_BEAT_LENGTH = 4;
	
	/**
	 * The main method for running this program.
	 * <p>
	 * Usage: <code>java -cp bin metalign.Main [ARGS] input1 [input2...]</code>
	 * <p>
	 * Where each input is either a file or a directory. Each file listed as input, and each file
	 * beneath every directory listed as input (recursively) is read as input.
	 * <p>
	 * <blockquote>
	 * ARGS:
	 * <ul>
	 *  <li><code>-T</code> = Use tracks as correct voice (instead of channels) *Only used for MIDI files.</li>
	 *  <li><code>-p</code> = Use verbose printing.</li>
	 *  <li><code>-P</code> = Use super verbose printing.</li>
	 *  <li><code>-l</code> = Print logging (time, hypothesis count, and notes at each step).</li>
	 *  <li><code>-J</code> = Run with incremental joint processing.</li>
	 *  <li><code>-VClass</code> = Use the given class for voice separation. (FromFile (default) or Hmm)</li>
	 *  <li><code>-BClass</code> = Use the given class for beat tracking. (FromFile (default) or Hmm).</li>
	 *  <li><code>-HClass</code> = Use the given class for hierarchy detection. (FromFile (default) or lpcfg).</li>
	 *  <li><code>-g FILE</code> = Load a grammar in from the given file. Used only with -Hlpcfg. Can merge multiple grammars with multiple -g.</li>
	 *  <li><code>-x</code> = Extract the trees of the song for testing from the loaded grammar when testing. Used only with -Hlpcfg.</li>
	 *  <li><code>-e</code> = Extend each note within each voice to the next note's onset.</li>
	 *  <li><code>-m INT</code> = For beat tracking and hierarchy detection, throw out notes whose length is shorter than INT microseconds, once extended.</li>
	 *  <li><code>-s INT</code> = Use INT as the sub beat length.</li>
	 *  <li><code>-b INT</code> = Use INT as the beam size.</li>
	 *  <li><code>-v INT</code> = Use INT as the voice beam size.</li>
	 *  <li><code>-E FILE</code> = Print out the evaluation for each hypothesis as well with the given FILE as ground truth.</li>
	 *  <li><code>-a FILE</code> = Search recursively under the given FILE for anacrusis files.</li>
	 * </ul>
	 * </blockquote>
	 * 
	 * @param args String array with the args described above.
	 * @throws InterruptedException An interrupt occurred in GUI mode.
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
		boolean incrementalJoint = false;
		
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
							
						case 'J':
							incrementalJoint = true;
							break;
							
						case 'l':
							LOG_STATUS = true;
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
							
						case 'G':
							i++;
							if (args.length == i) {
								argumentError("No global weight given with -G option.");
							}
							try {
								MetricalLpcfgHierarchyModelState.GLOBAL_WEIGHT = Double.parseDouble(args[i]);
							} catch (NumberFormatException e) {
								argumentError("Exception reading global weight. Must be a double: " + args[i]);
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
							
						// Voice beam
						case 'v':
							i++;
							if (args.length == i) {
								argumentError("No voice beam size given with -v option.");
							}
							try {
								VOICE_BEAM_SIZE = Integer.parseInt(args[i]);
							} catch (NumberFormatException e) {
								argumentError("Exception reading voice beam size. Must be an integer: " + args[i]);
							}
							break;
							
						// Voice separation class
						case 'V':
							if (args[i].length() == 2) {
								argumentError("No Class given for -V. (There should be no space between -V and the Class name).");
							}
							voiceClass = args[i].substring(2);
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
			
			if (incrementalJoint) {
				System.out.println("Incremental Joint processing");
			}
			
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
			
			System.out.println("Using beam size " + BEAM_SIZE);
			System.out.println("Using voice beam size " + VOICE_BEAM_SIZE);
			
			System.out.println((EXTEND_NOTES ? "Extending" : "Not extending") + " notes through rests");
			
			System.out.println("Testing on files: " + files);
			System.out.println("Using anacrusis files: " + anacrusisFiles);
		}
		
		if (incrementalJoint) {
			for (File file : files) {
				System.out.println("File: " + file);
				
				TimeTracker tt = new MidiTimeTracker();
				NoteListGenerator nlg = new NoteListGenerator();
				EventParser ep;
				
				try {
					ep = Runner.parseMidiFile(file, nlg, (MidiTimeTracker) tt, useChannel);
					
				} catch (IOException | InvalidMidiDataException e) {
					System.err.println("Error parsing file " + file + ":\n" + e.getLocalizedMessage());
					
					if (VERBOSE) {
						e.printStackTrace();
					}
					
					continue;
				}
				
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
				
				JointModel jm = getJointModel(voiceClass, "FromFile", "FromFile", ep, tt, grammar);
				
				// Run with VOICE_BEAM as BEAM
				TESTING = true;
				int prevBeam = BEAM_SIZE;
				BEAM_SIZE = VOICE_BEAM_SIZE;
				Runner.performInference(jm, nlg);
				BEAM_SIZE = prevBeam;
				TESTING = false;
				
				JointModelState best = null;
				double bestScore = Double.NEGATIVE_INFINITY;
						
				for (JointModelState jms : jm.getHypotheses()) {
					
					VoiceSplittingModelState voiceState = jms.getVoiceState();
					
					// Write file
					File newFile = new File("tmp." + voiceState.hashCode() + ".mid");
					MidiWriter voiceWriter = new MidiWriter(newFile, (MidiTimeTracker) tt);
					
					for (int i = 0; i < voiceState.getVoices().size(); i++) {
						for (MidiNote note : voiceState.getVoices().get(i).getNotes()) {
							MidiNote newNote = new MidiNote(note.getPitch(), note.getVelocity(), note.getOnsetTime(), i, 0);
							newNote.close(note.getOffsetTime());
							voiceWriter.addMidiNote(newNote);
						}
					}
						
					voiceWriter.write();
					
					TimeTracker newTt = new MidiTimeTracker();
					NoteListGenerator newNlg = new NoteListGenerator();
					EventParser newEp;
					
					try {
						newEp = Runner.parseMidiFile(newFile, newNlg, (MidiTimeTracker) newTt, useChannel);
						
					} catch (IOException | InvalidMidiDataException e) {
						System.err.println("Error parsing file " + newFile + " during incremental of " + file + ":\n" + e.getLocalizedMessage());
						
						if (VERBOSE) {
							e.printStackTrace();
						}
						
						return;
					}
					
					newFile.delete();
					
					JointModel newJm = getJointModel("FromFile", beatClass, hierarchyClass, newEp, newTt, grammar);
					
					// Run
					TESTING = true;
					Runner.performInference(newJm, newNlg);
					TESTING = false;
					
					if (!newJm.getHypotheses().isEmpty()) {
						JointModelState bestJmsGuess = newJm.getHypotheses().first();
						
						if (bestJmsGuess.getScore() + voiceState.getScore() > bestScore) {
							best = bestJmsGuess;
							bestScore = bestJmsGuess.getScore() + voiceState.getScore();
						}
					}
				}
				
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
				
				if (best == null) {
					System.out.println("NONE");
				} else {
					System.out.println("Voices: " + best.getVoiceState());
					System.out.println("Beats: " + best.getBeatState());
					System.out.println("Hierarchy: " + best.getHierarchyState());
				}
			}
			
		} else {
		
			// Test
			for (File file : files) {
				System.out.println("File: " + file);
				
				TimeTracker tt = new MidiTimeTracker();
				tt.setAnacrusis(MetricalLpcfgGeneratorRunner.getAnacrusisLength(file, anacrusisFiles));
				NoteListGenerator nlg = new NoteListGenerator();
				EventParser ep;
				
				try {
					ep = Runner.parseMidiFile(file, nlg, (MidiTimeTracker) tt, useChannel);
					
				} catch (IOException | InvalidMidiDataException e) {
					System.err.println("Error parsing file " + file + ":\n" + e.getLocalizedMessage());
					
					if (VERBOSE) {
						e.printStackTrace();
					}
					
					continue;
				}
				
				tt.setFirstNoteTime(nlg.getNoteList().get(0).getOnsetTime());
				if (tt.getAllTimeSignatures().size() != 1) {
					System.err.println("Time change detected. Skipping song " + file);
					continue;
				}
				
				if (tt.getFirstTimeSignature().getMeasure().getBeatsPerBar() < 2 || tt.getFirstTimeSignature().getMeasure().getBeatsPerBar() > 4 ||
						tt.getFirstTimeSignature().getMeasure().getSubBeatsPerBeat() < 2 || tt.getFirstTimeSignature().getMeasure().getSubBeatsPerBeat() > 3) {
					System.err.println("Irregular meter detected (" + tt.getFirstTimeSignature().getMeasure().getBeatsPerBar() + "," +
						tt.getFirstTimeSignature().getMeasure().getSubBeatsPerBeat() + "). Skipping song " + file);
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
				
				if (VERBOSE || EVALUATOR != null) {
					// Print all choices
					for (JointModelState jms : jm.getHypotheses()) {
						if (VERBOSE) {
							System.out.println(jms.getVoiceState());
							System.out.println(jms.getBeatState());
							System.out.println(jms.getHierarchyState());
						}
						
						// Also print score for each
						if (EVALUATOR != null) {
							System.out.println("Voice prob: " + jms.getVoiceState().getScore());
							System.out.println("Beats prob: " + jms.getBeatState().getScore());
							System.out.println("Hierarchy: " + jms.getHierarchyState());
							System.out.println(EVALUATOR.evaluate(jms));
						}
					}
					
				} else {
					// Print only top choices
					if (jm.getHypotheses().isEmpty()) {
						System.out.println("NONE");
					} else {
						System.out.println("Voices: " + jm.getHypotheses().first().getVoiceState());
						System.out.println("Beats: " + jm.getHypotheses().first().getBeatState());
						System.out.println("Hierarchy: " + jm.getHypotheses().first().getHierarchyState());
					}
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
