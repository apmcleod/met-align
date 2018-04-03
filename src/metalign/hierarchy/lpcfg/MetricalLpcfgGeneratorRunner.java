package metalign.hierarchy.lpcfg;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;

import metalign.Main;
import metalign.Runner;
import metalign.beat.fromfile.FromFileBeatTrackingModelState;
import metalign.hierarchy.fromfile.FromFileHierarchyModelState;
import metalign.joint.JointModel;
import metalign.parsing.EventParser;
import metalign.parsing.NoteBParser;
import metalign.parsing.NoteListGenerator;
import metalign.time.NoteBTimeTracker;
import metalign.time.TimeSignature;
import metalign.time.TimeTracker;
import metalign.voice.fromfile.FromFileVoiceSplittingModelState;

/**
 * The <code>MetricalGrammarGeneratorRunner</code> class is used to interface with and run
 * the {@link MetricalLpcfgGenerator} class.
 * 
 * @author Andrew McLeod - 29 February, 2016
 */
public class MetricalLpcfgGeneratorRunner {
	public static boolean VERBOSE = false;
	public static boolean TESTING = false;
	public static boolean LEXICALIZATION = true;
	
	/**
	 * The main method for generating an LPCFG grammar file.
	 * <p>
	 * Usage: <code>java -cp bin metalign.hierarchy.lpcfg.MetricalLpcfgGeneratorRunner [ARGS] input1 [input2...]</code>
	 * <p>
	 * Where each input is either a file or a directory. Each file listed as input, and each file
	 * beneath every directory listed as input (recursively) is read as input.
	 * <p>
	 * <blockquote>
	 * ARGS:
	 * <ul>
	 *  <li><code>-g FILE</code> = Write the grammar out to the given FILE.</li>
	 *  <li><code>-v</code> = Use verbose printing.</li>
	 *  <li><code>-T</code> = Use tracks as correct voice (instead of channels) *Only used for MIDI files.</li>
	 *  <li><code>-l</code> = Do NOT use lexicalisation.</li>
	 *  <li><code>-e</code> = Extend each note within each voice to the next note's onset.</li>
	 *  <li><code>-m INT</code> = Throw out notes whose length is shorter than INT microseconds, once extended.</li>
	 *  <li><code>-s INT</code> = Use INT as the sub beat length.</li>
	 *  <li><code>-a FILE</code> = Search recursively under the given FILE for anacrusis files.</li>
	 * </ul>
	 * </blockquote>
	 * 
	 * @param args The args as described above.
	 * @throws InterruptedException
	 * @throws IOException
	 */
	public static void main(String[] args) throws InterruptedException, IOException {
		boolean useChannel = true;
		boolean generate = false;
		File exportModelFile = null;
		List<File> testFiles = new ArrayList<File>();
		List<File> anacrusisFiles = new ArrayList<File>();

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
							
						case 'm':
							i++;
							if (args.length == i) {
								argumentError("No minimum note length given with -m option.");
							}
							try {
								Main.MIN_NOTE_LENGTH = Integer.parseInt(args[i]);
							} catch (NumberFormatException e) {
								argumentError("Exception reading minimum note length. Must be an integer: " + args[i]);
							}
							break;
							
						// Use track
						case 'T':
							useChannel = false;
							break;
							
						case 'v':
							VERBOSE = true;
							System.out.println("Verbose output");
							break;
							
						case 'l':
							LEXICALIZATION = false;
							break;
							
						case 'e':
							Main.EXTEND_NOTES = true;
							break;
							
						case 'g':
							generate = true;
							if (args.length <= ++i) {
								argumentError("No File used with -g");
							}
							exportModelFile = new File(args[i]);
							break;
							
						case 'a':
							if (args.length <= ++i) {
								argumentError("No Anacrusis Files given after -a");
							}
							File file = new File(args[i]);
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
					
				// File or directory name
				default:
					File file = new File(args[i]);
					if (!file.exists()) {
						argumentError("File " + args[i] + " not found");
					}
					testFiles.addAll(Main.getAllFilesRecursive(file));
			}
		}
		
		if (testFiles.isEmpty()) {
			argumentError("No music files given for testing");
		}
		
		// Generate grammar
		if (generate) {
			if (VERBOSE) {
				System.out.println("Generating grammar into " + exportModelFile);
			}
			
			MetricalLpcfgGenerator generator = generateGrammar(testFiles, anacrusisFiles, useChannel);
			
			MetricalLpcfg.serialize(generator.getGrammar(), exportModelFile);	
		}
	}

	/**
	 * Generate a grammar from the given files with the given Runner.
	 * 
	 * @param midiFiles The Files we want to generate a grammar from.
	 * @param anacrusisFiles The anacrusis files for the given midiFiles.
	 * @param useChannel True if to use channels as the gold standard voice in MIDI files.
	 * False for tracks.
	 * @throws InterruptedException An interrupt occurred in GUI mode.
	 */
	private static MetricalLpcfgGenerator generateGrammar(List<File> midiFiles, List<File> anacrusisFiles, boolean useChannel) throws InterruptedException {
		// We have files and are ready to run!
		MetricalLpcfgGenerator generator = new MetricalLpcfgGenerator();
		
		for (File file : midiFiles) {
			if (VERBOSE) {
				System.out.println("Parsing " + file);
			}
			
			TimeTracker tt = new TimeTracker(Main.SUB_BEAT_LENGTH);
			tt.setAnacrusis(getAnacrusisLength(file, anacrusisFiles));
			NoteListGenerator nlg = new NoteListGenerator(tt);
			
			// PARSE!
			EventParser ep = null;
			try {
				if (file.toString().endsWith(".nb")) {
					// NoteB
					tt = new NoteBTimeTracker(Main.SUB_BEAT_LENGTH);
					nlg = new NoteListGenerator(tt);
					ep = new NoteBParser(file, nlg, (NoteBTimeTracker) tt);
					ep.run();
					
				} else {
					// Midi or krn
					ep = Runner.parseFile(file, nlg, tt, useChannel);
				}
				
			} catch (InvalidMidiDataException | IOException e) {
				System.err.println("Error parsing file " + file + ":\n" + e.getLocalizedMessage());
				
				if (VERBOSE) {
					e.printStackTrace();
				}
				
				continue;
			}
			
			if (tt.getFirstTimeSignature().getNumerator() == TimeSignature.IRREGULAR_NUMERATOR) {
				System.err.println("Irregular meter detected. Skipping song " + file);
				continue;
			}
			
			if (tt.getAllTimeSignatures().size() != 1) {
				System.err.println("Meter change detected. Skipping song " + file);
				continue;
			}
			
			// RUN!
			JointModel jm;
			try {
				jm = new JointModel(
					new FromFileVoiceSplittingModelState(ep),
					new FromFileBeatTrackingModelState(tt),
					new FromFileHierarchyModelState(tt));
			
			} catch (InvalidMidiDataException e) {
				System.err.println("Error parsing file " + file + ":\n" + e.getLocalizedMessage());
				
				if (VERBOSE) {
					e.printStackTrace();
				}
				
				continue;
			}
			
			Runner.performInference(jm, nlg);
			
			// GRAMMARIZE
			generator.parseSong(jm, tt);
		}
		
		return generator;
	}
	
	/**
	 * Get the anacrusis length for the given test file given the anacrusis files.
	 * 
	 * @param testFile The file for which we want the anacrusis.
	 * @param anacrusisFiles The anacrusisFiles.
	 * @return The anacrusis length for the given test file.
	 */
	public static int getAnacrusisLength(File testFile, List<File> anacrusisFiles) {
		String anacrusisFileName = testFile.getName() + ".anacrusis";
		for (File file : anacrusisFiles) {
			if (file.getName().equals(anacrusisFileName)) {
				try {
					BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
					
					int anacrusisLength = Integer.parseInt(bufferedReader.readLine());
					bufferedReader.close();
					return anacrusisLength;
					
				} catch (FileNotFoundException e) {
				} catch (NumberFormatException e) {
				} catch (IOException e) {}
			}
		}
		
		return 0;
	}

	/**
	 * An argument error occurred. Print the usage help info to standard error, and then exit.
	 * <p>
	 * NOTE: This method calls <code>System.exit(1)</code> and WILL NOT return.
	 * 
	 * @param message The error message to print at the beginning of the exception.
	 */
	private static void argumentError(String message) {
		StringBuilder sb = new StringBuilder("MetricalLpcfgGeneratorRunner: Argument error: ");
		
		sb.append(message).append('\n');
		
		sb.append("Usage: java -cp bin metalign.hierarchy.lpcfg.MetricalLpcfgGeneratorRunner ARGS file [directory...]\n");

		sb.append("-g FILE = Write the grammar out to the given FILE.\n");
		sb.append("-v = Use verbose printing.\n");
		sb.append("-T = Use tracks as correct voice (instead of channels) *Only used for MIDI files.\n");
		sb.append("-l = Do NOT use lexicalisation.\n");
		sb.append("-e = Extend each note within each voice to the next note's onset.\n");
		sb.append("-m INT = Throw out notes whose length is shorter than INT microseconds, once extended.\n");
		sb.append("-s INT = Use INT as the sub beat length.\n");
		sb.append("-a FILE = Search recursively under the given FILE for anacrusis files.\n");
		
		System.err.println(sb.toString());
		System.exit(1);
	}
}
