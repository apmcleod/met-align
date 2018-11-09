package metalign.beat.hmm;

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
import metalign.parsing.NoteBParser;
import metalign.parsing.NoteListGenerator;
import metalign.parsing.XMLParser;
import metalign.time.NoteBTimeTracker;
import metalign.time.TimeTracker;
import metalign.utils.MidiNote;

/**
 * The <code>HmmBeatTrackingModelTrainer</code> class is used to train the parameters for
 * HmmBeatTrackingModelParameters. It just reads in notes and beat/subBeat locations,
 * and then measures the different parameters.
 * 
 * @author Andrew McLeod - 28 July, 2017
 */
public class HmmBeatTrackingModelTrainer {
	
	/**
	 * The main method for running training the beat-tracker parameters.
	 * <p>
	 * Usage: <code>java -cp bin metalign.beat.hmm.HmmBeatTrackingModelTrainer [ARGS] input1 [input2...]</code>
	 * <p>
	 * Where each input is either a file or a directory. Each file listed as input, and each file
	 * beneath every directory listed as input (recursively) is read as input.
	 * <p>
	 * <blockquote>
	 * ARGS:
	 * <ul>
	 *  <li><code>-T</code> = Use tracks as correct voice (instead of channels) *Only used for MIDI files.</li>
	 *  <li><code>-s INT</code> = Use INT as the sub beat length.</li>
	 *  <li><code>-X</code> = Input files are xml directories from CrestMusePEDB.</li>
	 *  <li><code>-a FILE</code> = Search recursively under the given FILE for anacrusis files.</li>
	 * </ul>
	 * </blockquote>
	 * 
	 * @param args Arguments described above.
	 */
	public static void main(String[] args) {
		boolean useChannel = true;
		boolean xml = false;
		List<File> files = new ArrayList<File>();
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
						// Use track
						case 'T':
							useChannel = false;
							break;
							
						// XML files as input
						case 'X':
							xml = true;
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
					files.addAll(Main.getAllFilesRecursive(file));
			}
		}
		
		if (files.isEmpty()) {
			argumentError("No files found");
		}
		
		// Remove files until left with only nodoctype ones
		if (xml) {
			for (int i = files.size() - 1; i >= 0; i--) {
				if (files.get(i).getName().equals("deviation_nodoctype.xml")
						|| !files.get(i).getName().endsWith("_nodoctype.xml")
						|| files.get(i).getName().equals("structure_nodoctype.xml")) {
					files.remove(i);
				}
			}
		}
		
		parseFiles(files, useChannel);
	}
	
	private static void parseFiles(List<File> files, boolean useChannel) {
		int barChangeCount = 0;
		//double tempoDiffSum = 0.0;
		//double tempoDiffSumSquared = 0.0;
		
		double tempoPercentDiffSum = 0.0;
		double tempoPercentDiffSumSquared = 0.0;
		
		double initialTempoSum = 0.0;
		double initialTempoSumSquared = 0.0;
		int initialBarCount = 0;
		
		int barCount = 0;
		//double barStdSum = 0.0;
		//double barStdSumSquared = 0.0;
		
		double barPercentStdSum = 0.0;
		double barPercentStdSumSquared = 0.0;
		
		int deviationCount = 0;
		double deviationSum = 0.0;
		double deviationSumSquared = 0.0;
		
		double minTempo = Double.MAX_VALUE;
		double maxTempo = Double.MIN_VALUE;
		
		for (File file : files) {
			System.out.println("File: " + file);
			
			List<Beat> beats = new ArrayList<Beat>();
			List<Beat> tatums = null;
			XMLParser parser = null;
			NoteListGenerator nlg = null;
			
			if (file.toString().endsWith(".xml")) {
				File deviationFile = new File(file.getParentFile() + File.separator + "deviation_nodoctype.xml");
			
				try {
					parser = new XMLParser(deviationFile, file);
				} catch (ParserConfigurationException | SAXException | IOException e) {
					System.err.println("Error parsing " + file + "\n" + e.getLocalizedMessage());
					continue;
				}
				
				parser.run();
				
				beats = parser.getBeats();
				
			} else {
				TimeTracker tt = new TimeTracker();
				nlg = new NoteListGenerator(tt);
				
				try {
					if (file.toString().endsWith(".nb")) {
						// NoteB
						tt = new NoteBTimeTracker();
						nlg = new NoteListGenerator(tt);
						new NoteBParser(file, nlg, (NoteBTimeTracker) tt).run();
						
					} else {
						// Midi or krn
						Runner.parseFile(file, nlg, tt, useChannel);
					}
					
				} catch (IOException | InvalidMidiDataException | InterruptedException e) {
					System.err.println("Error parsing file " + file + ":\n" + e.getLocalizedMessage());
					continue;
				}
				
				beats = tt.getBeatsOnly();
				tatums = tt.getTatums();
			}
			
			if (!beats.isEmpty()) {
				barCount++;
			}
			
			double previousBarTempo = -1.0;
			Beat previousBeat = beats.isEmpty() ? null : beats.get(0);
			Beat currentBeat;
			
			double beatLengthSum = 0;
			double beatLengthSumSquared = 0;
			int numBeats = 0;
			
			for (int i = 1; i < beats.size(); i++) {
				currentBeat = beats.get(i);
				previousBeat = beats.get(i - 1);
				
				beatLengthSum += currentBeat.getTime() - previousBeat.getTime();
				beatLengthSumSquared += (currentBeat.getTime() - previousBeat.getTime()) * (currentBeat.getTime() - previousBeat.getTime());
				numBeats++;
				
				if (previousBeat.getBar() != currentBeat.getBar()) {
					double tempo = beatLengthSum / numBeats;
					minTempo = Double.min(minTempo, tempo);
					maxTempo = Double.max(maxTempo, tempo);
					
					double beatLengthStd = Math.sqrt(beatLengthSumSquared / numBeats - tempo * tempo);
					//barStdSum += beatLengthStd;
					//barStdSumSquared += beatLengthStd * beatLengthStd;
					
					barPercentStdSum += beatLengthStd / tempo;
					barPercentStdSumSquared += (beatLengthStd / tempo) * (beatLengthStd / tempo);
					
					if (previousBarTempo > 0.0) {
						barChangeCount++;
						barCount++;
						
						double diff = tempo - previousBarTempo;
						double percentDiff = diff / previousBarTempo;
						
						//tempoDiffSum += diff;
						//tempoDiffSumSquared += diff * diff;
						
						tempoPercentDiffSum += percentDiff;
						tempoPercentDiffSumSquared += percentDiff * percentDiff;
						
					} else {
						initialBarCount++;
						initialTempoSum += tempo;
						initialTempoSumSquared += tempo * tempo;
					}
					
					previousBarTempo = tempo;
					beatLengthSum = 0.0;
					beatLengthSumSquared = 0.0;
					numBeats = 0;
				}
			}
			
			if (numBeats > 0) {
				double tempo = beatLengthSum / numBeats;
				
				double beatLengthStd = Math.sqrt(beatLengthSumSquared / numBeats - tempo * tempo);
				//barStdSum += beatLengthStd;
				//barStdSumSquared += beatLengthStd * beatLengthStd;
				
				barPercentStdSum += beatLengthStd / tempo;
				barPercentStdSumSquared += (beatLengthStd / tempo) * (beatLengthStd / tempo);
				
				if (previousBarTempo > 0.0) {
					barChangeCount++;
					barCount++;
					
					double diff = tempo - previousBarTempo;
					double percentDiff = diff / previousBarTempo;
					
					//tempoDiffSum += diff;
					//tempoDiffSumSquared += diff * diff;
					
					tempoPercentDiffSum += percentDiff;
					tempoPercentDiffSumSquared += percentDiff * percentDiff;
				}
			}
			
			
			// Calculate note deviations from tatums
			if (parser != null) {
				for (double deviation : parser.getNoteDeviations()) {
					deviationCount++;
					deviationSum += deviation;
					deviationSumSquared += deviation * deviation;
				}
				
			} else if (tatums != null && nlg != null) {
				for (List<MidiNote> noteList : nlg.getIncomingLists()) {
					for (MidiNote note : noteList) {
						Beat closestTatum = note.getOnsetSubBeat(tatums);
						
						double diff = Math.abs(closestTatum.getTime() - note.getOnsetTime());
						deviationCount++;
						deviationSum += diff;
						deviationSumSquared += diff * diff;
					}
				}
			}
		}
		
		//double tempoDiffMean = tempoDiffSum / barChangeCount;
		//double tempoDiffVariance = tempoDiffSumSquared / barChangeCount - tempoDiffMean * tempoDiffMean;
		
		double tempoPercentDiffMean = tempoPercentDiffSum / barChangeCount;
		double tempoPercentDiffVariance = tempoPercentDiffSumSquared / barChangeCount - tempoPercentDiffMean * tempoPercentDiffMean;
		
		double initialTempoMean = initialTempoSum / initialBarCount;
		double initialTempoVariance = initialTempoSumSquared / initialBarCount - initialTempoMean * initialTempoMean;
		
		//System.out.println("Bar Change Count = " + barChangeCount);
		//System.out.println("Tempo Diff Mean = " + tempoDiffMean);
		//System.out.println("Tempo Diff St Dev = " + Math.sqrt(tempoDiffVariance));
		System.out.println("Tempo Percent Diff Mean = " + tempoPercentDiffMean);
		System.out.println("Tempo Percent Diff St Dev = " + Math.sqrt(tempoPercentDiffVariance));
		System.out.println("Initial Tempo Mean = " + initialTempoMean);
		System.out.println("Initial Tempo St Dev = " + Math.sqrt(initialTempoVariance));
		System.out.println("Min Tempo = " + minTempo);
		System.out.println("Max Tempo = " + maxTempo);
		
		//double barStdMean = barStdSum / barCount;
		//double barStdVariance = barStdSumSquared / barCount - barStdMean * barStdMean;
		
		double barPercentStdMean = barPercentStdSum / barCount;
		double barPercentStdVariance = barPercentStdSumSquared / barCount - barPercentStdMean * barPercentStdMean;
		
		//System.out.println("Bar Count = " + barCount);
		//System.out.println("Bar Std Mean = " + barStdMean);
		//System.out.println("Bar Std Std Dev = " + Math.sqrt(barStdVariance));
		System.out.println("Evenness Mean = " + barPercentStdMean);
		System.out.println("Evenness Std Dev = " + Math.sqrt(barPercentStdVariance));
		
		double deviationMean = deviationSum / deviationCount;
		double deviationVariance = deviationSumSquared / deviationCount - deviationMean * deviationMean;
		
		//System.out.println("Deviation Count = " + deviationCount);
		//System.out.println("Deviation Mean = " + deviationMean);
		System.out.println("Note Deviation Std Dev = " + Math.sqrt(deviationVariance));
	}

	/**
	 * An argument error occurred. Print the usage help info to standard error, and then exit.
	 * <p>
	 * NOTE: This method calls <code>System.exit(1)</code> and WILL NOT return.
	 * 
	 * @param message The error message to print at the beginning of the exception.
	 */
	public static void argumentError(String message) {
		StringBuilder sb = new StringBuilder("HmmBeatTrackingModelTrainer: Argument error: ");
		
		sb.append(message).append('\n');
		
		sb.append("Usage: java -cp bin metalign.beat.hmm.HmmBeatTrackingModelTrainer ARGS file [directory...]\n");
		
		sb.append("-T = Use tracks as correct voice (instead of channels) *Only used for MIDI files.\n");
		sb.append("-X = Input files are xml directories from CrestMusePEDB.\n");
		sb.append("-s INT = Use INT as the sub beat length.\n");
		sb.append("-a FILE = Search recursively under the given FILE for anacrusis files.\n");
		
		System.err.println(sb.toString());
		System.exit(1);
	}
}
