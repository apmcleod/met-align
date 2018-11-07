package metalign.hierarchy.lpcfg;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sound.midi.InvalidMidiDataException;

import metalign.Main;
import metalign.Runner;
import metalign.beat.Beat;
import metalign.beat.fromfile.FromFileBeatTrackingModelState;
import metalign.hierarchy.fromfile.FromFileHierarchyModelState;
import metalign.joint.JointModel;
import metalign.parsing.EventParser;
import metalign.parsing.NoteBParser;
import metalign.parsing.NoteListGenerator;
import metalign.time.NoteBTimeTracker;
import metalign.time.TimeSignature;
import metalign.time.TimeTracker;
import metalign.utils.MidiNote;
import metalign.voice.fromfile.FromFileVoiceSplittingModelState;

/**
 * The <code>MetricalGrammarGeneratorRunner</code> class is used to interface with and run
 * the {@link MetricalLpcfgGenerator} class.
 * 
 * @author Andrew McLeod - 29 February, 2016
 */
public class MetricalLpcfgGeneratorRunner implements Callable<MetricalLpcfgGenerator> {
	public static boolean VERBOSE = false;
	public static boolean TESTING = false;
	public static boolean LEXICALIZATION = true;
	public static int NUM_PROCS = 1;
	public static double QUANTIZATION_THRESHOLD = 0.9;
	public static boolean SAVE_TREES = true;

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
	 * @throws ExecutionException 
	 */
	public static void main(String[] args) throws InterruptedException, IOException, ExecutionException {
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
							
						case 'x':
							SAVE_TREES = false;
							break;
							
						case 'p':
							i++;
							if (args.length == i) {
								argumentError("No process count given for -p option.");
							}
							try {
								NUM_PROCS = Integer.parseInt(args[i]);
							} catch (NumberFormatException e) {
								argumentError("Exception reading process count. Must be an integer: " + args[i]);
							}
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
							
						case 'q':
							i++;
							if (args.length == i) {
								argumentError("No quantization threshold given for -q option.");
							}
							try {
								QUANTIZATION_THRESHOLD = Double.parseDouble(args[i]);
							} catch (NumberFormatException e) {
								argumentError("Exception reading quantization threshold. Must be a double: " + args[i]);
							}
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
		
		MetricalLpcfg grammar;
		
		// Generate grammar
		if (generate) {
			if (VERBOSE) {
				System.out.println("Generating grammar into " + exportModelFile);
				System.out.println("Using " + NUM_PROCS + " process(es).");
			}
			
			if (NUM_PROCS > 1) {
				grammar = new MetricalLpcfg();
				double filesPerProc = ((double) testFiles.size()) / ((double) NUM_PROCS);
				
				// Create callables
				List<Callable<MetricalLpcfgGenerator>> callables = new ArrayList<Callable<MetricalLpcfgGenerator>>(NUM_PROCS);
			    for (int i = 0; i < NUM_PROCS; i++) {
			    	callables.add(new MetricalLpcfgGeneratorRunner(
			    			testFiles.subList((int) Math.round(i * filesPerProc), (int) Math.round((i + 1) * filesPerProc)),
			    			anacrusisFiles, useChannel)
			    	);
			    }
	
			    // Execute the callables
			    ExecutorService executor = Executors.newFixedThreadPool(NUM_PROCS);
			    List<Future<MetricalLpcfgGenerator>> results = executor.invokeAll(callables);
			    
			    // Grab the results and save the best
			    for (Future<MetricalLpcfgGenerator> result : results) {
			    	MetricalLpcfgGenerator generator = result.get();
			    	
			    	grammar.mergeGrammar(generator.getGrammar());
			    }
			    
			    executor.shutdown();
			    
			} else {
				grammar = generateGrammar(testFiles, anacrusisFiles, useChannel).getGrammar();
			}
			
			MetricalLpcfg.serialize(grammar, exportModelFile);	
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
		int fileNum = 0;
		
		for (File file : midiFiles) {
			fileNum++;
			if (VERBOSE) {
				if (NUM_PROCS != 1) {
					System.out.print(Thread.currentThread().getId() + ": ");
				}
				
				System.out.println("Parsing " + fileNum + "/" + midiFiles.size() + ": " + file);
			}
			
			TimeTracker tt = new TimeTracker();
			tt.setAnacrusis(getAnacrusisLength(file, anacrusisFiles));
			NoteListGenerator nlg = new NoteListGenerator(tt);
			
			// PARSE!
			EventParser ep = null;
			try {
				if (file.toString().endsWith(".nb")) {
					// NoteB
					tt = new NoteBTimeTracker();
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
			
			tt.setFirstNoteTime(nlg.getNoteList().get(0).getOnsetTime());
			
			if (tt.getFirstTimeSignature().getNumerator() == TimeSignature.IRREGULAR_NUMERATOR ||
					(tt.getFirstTimeSignature().getNumerator() != 2 &&
					tt.getFirstTimeSignature().getNumerator() != 3 &&
					tt.getFirstTimeSignature().getNumerator() != 4 &&
					tt.getFirstTimeSignature().getNumerator() != 6 &&
					tt.getFirstTimeSignature().getNumerator() != 9 &&
					tt.getFirstTimeSignature().getNumerator() != 12)) {
				System.err.println("Irregular meter detected (" + tt.getFirstTimeSignature().getNumerator() +
						"). Skipping song " + file);
				continue;
			}
			
			if (tt.getAllTimeSignatures().size() != 1) {
				System.err.println("Meter change detected. Skipping song " + file);
				continue;
			}
			
			double alignmentScore = getAlignmentScore(nlg, tt);
			if (alignmentScore < QUANTIZATION_THRESHOLD) {
				System.err.println("Poor alignment with beats detected (" + alignmentScore + "). Skipping song " + file);
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
			try {
				generator.parseSong(jm, tt);
			} catch (Exception e) {
				System.err.println("Error parsing file " + file + ":\n" + e.getLocalizedMessage());
				
				if (VERBOSE) {
					e.printStackTrace();
				}
			}
		}
		
		return generator;
	}
	
	/**
	 * Get the alignment score of a song given a NoteListGenerator and a TimeTracker. A song's
	 * alignment score is 1 minus the average distance each note's onset away from the nearest
	 * tatum location, measured as a percentage of the tatum length at that point.
	 * 
	 * @param nlg The NoteListGenerator.
	 * @param tt The TimeTracker.
	 * @return The alignment score.
	 */
	private static double getAlignmentScore(NoteListGenerator nlg, TimeTracker tt) {
		List<Beat> tatums = tt.getTatums();
		if (tatums.size() <= 1) {
			return 0.0;
		}
		
		Iterator<Beat> tatumIterator = tatums.iterator();
		Beat prevTatum = tatumIterator.next();
		Beat currentTatum = tatumIterator.next();
		
		double totalError = 0.0;
		for (MidiNote note : nlg.getNoteList()) {
			long time = note.getOnsetTime();
			
			while (time > currentTatum.getTime() && tatumIterator.hasNext()) {
				prevTatum = currentTatum;
				currentTatum = tatumIterator.next();
			}
			
			long diff = Math.min(Math.abs(time - prevTatum.getTime()), Math.abs(currentTatum.getTime() - time));
			totalError += ((double) diff) / ((double) (currentTatum.getTime() - prevTatum.getTime()));
		}
		
		return 1.0 - totalError / nlg.getNoteList().size();
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
		sb.append("-p INT = Run multi-threaded with the given number of processes.\n");
		sb.append("-q DOUBLE = Skip songs with a quantization score less than the given threshold (default=0.9).\n");
		sb.append("-x = Do not save trees in the grammar file (saves memory, cannot extract when testing).");
		
		System.err.println(sb.toString());
		System.exit(1);
	}
	
	/*******
	 * Class objects for multi-threading.
	 */
	
	private List<File> testFiles;
	private List<File> anacrusisFiles;
	private boolean useChannel;
	
	public MetricalLpcfgGeneratorRunner(List<File> testFiles, List<File> anacrusisFiles, boolean useChannel) {
		this.testFiles = testFiles;
		this.anacrusisFiles = anacrusisFiles;
		this.useChannel = useChannel;
	}
	
	@Override
	public MetricalLpcfgGenerator call() throws InterruptedException {
		return MetricalLpcfgGeneratorRunner.generateGrammar(testFiles, anacrusisFiles, useChannel);
	}
}
