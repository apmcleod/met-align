package metalign;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;

import metalign.generic.MidiModel;
import metalign.parsing.EventParser;
import metalign.parsing.KernEventParser;
import metalign.parsing.MidiEventParser;
import metalign.parsing.NoteEventParser;
import metalign.parsing.NoteListGenerator;
import metalign.time.TimeTracker;
import metalign.utils.MidiNote;

/**
 * The <code>Runner</code> class is the interface which runs all of the different parts
 * of the beattracking project directly. This is used as an interface between {@link Main},
 * which interprets the command line arguments, and the actual functionality of this project.
 * 
 * @author Andrew McLeod - 3 Sept, 2015
 */
public class Runner {
	/**
	 * The default voice splitter to use.
	 */
	public static final String DEFAULT_VOICE_SPLITTER = "FromFile";
	
	/**
	 * The default beat tracker to use.
	 */
	public static final String DEFAULT_BEAT_TRACKER = "Hmm";
	
	/**
	 * The default hierarchy model to use.
	 */
	public static final String DEFAULT_HIERARCHY_MODEL = "Lpcfg";

	/**
	 * Parse the given file with the given objects. Use this method if you don't know specifically whether
	 * the file is a **kern file or a MIDI file, but if it is a MIDI file, you know whether to use channels
	 * or tracks as the gold standard voices.
	 * <br>
	 * This method first tries to detect the file's type by its extension (.mid, .midi, or .krn). If
	 * the extension is something else, or there is some error parsing the file, both MIDI and **kern
	 * are tried to see if either works.
	 * <br>
	 * If you know the file type already, you should use either {@link #parseKernFile(File, NoteEventParser, TimeTracker)}
	 * (for **kern files) or {@link #parseMidiFile(File, NoteEventParser, TimeTracker, boolean)}
	 * (for MIDI files).
	 * 
	 * @param file The file to parse.
	 * @param parser The NoteEventParser to pass note events to.
	 * @param tt The TimeTracker to pass time events to.
	 * 
	 * @return The EventParser used to parse the given file.
	 * 
	 * @throws IOException The File was unable to be parsed for some reason.
	 * @throws InvalidMidiDataException The File contained invalid MIDI data.
	 * @throws InterruptedException An interrupt occurred in GUI mode.
	 */
	public static EventParser parseFile(File file, NoteEventParser parser, TimeTracker tt, boolean useChannel)
			throws IOException, InvalidMidiDataException, InterruptedException {
		if (file.toString().endsWith(".mid") || file.toString().endsWith(".midi")) {
			try {
				return parseMidiFile(file, parser, tt, useChannel);
				
			} catch (InvalidMidiDataException | IOException e) {
				try {
					return parseKernFile(file, parser, tt);
					
				} catch (IOException e1) {
					// Throw the original MIDI Exception, since we really think this is a MIDI file.
					throw e;
				}
			}
			
		} else if (file.toString().endsWith(".krn")) {
			try {
				return parseKernFile(file, parser, tt);
				
			} catch (IOException e) {
				try {
					return parseMidiFile(file, parser, tt, useChannel);
					
				} catch (InvalidMidiDataException | IOException e2) {
					// Throw the original **kern Exception, since we really think this is a **kern file.
					throw e;
				}
			}
		}
		
		// Here, we have an unknown file
		try {
			return parseMidiFile(file, parser, tt, useChannel);
			
		} catch (IOException | InvalidMidiDataException e) {
			try {
				return parseKernFile(file, parser, tt);
				
			} catch (IOException e2) {
				// Throw both Exceptions, since we don't know which was intended.
				throw new IOException("ERROR: Unable to determine type of file " + file + "\n" +
						"Parsing as MIDI gave: " + e.getLocalizedMessage() + "\n" +
						"Parsing as **kern gave: " + e2.getLocalizedMessage());
			}
		}
	}
	
	/**
	 * Parse the given MIDI file using the given objects.
	 * 
	 * @param file The MIDI File we wish to parse.
	 * @param parser The NoteEventParser we want our EventParser to pass note events to. 
	 * @param tt The TimeTracker we want our EventParser to pass time events to.
	 * @param useChannel Whether we want to use channels as a gold standard voice (TRUE), or tracks (FALSE),
	 * when parsing midi.
	 * 
	 * @return The MidiEventParser to be used for this song.
	 * 
	 * @throws InvalidMidiDataException If the File contains invalid MIDI data.
	 * @throws IOException If there was an error reading the File.
	 * @throws InterruptedException An interrupt occurred in GUI mode.
	 */
	public static MidiEventParser parseMidiFile(File file, NoteEventParser parser, TimeTracker tt, boolean useChannel)
			throws InvalidMidiDataException, IOException, InterruptedException {
		MidiEventParser ep = new MidiEventParser(file, parser, tt, useChannel);
		
		ep.run();
		
		return ep;
	}
	
	/**
	 * Parse the given **kern file using the given objects.
	 * 
	 * @param file The **kern File we wish to parse.
	 * @param parser The NoteEventParser we want our EventParser to pass note events to. 
	 * @param tt The TimeTracker we want our EventParser to pass time events to.
	 * 
	 * @return The KernEventParser to be used for this song.
	 * 
	 * @throws IOException If there was some error reading or parsing the file.
	 * @throws InterruptedException An interrupt occurred in GUI mode.
	 */
	public static KernEventParser parseKernFile(File file, NoteEventParser parser, TimeTracker tt)
			throws IOException, InterruptedException {
		KernEventParser ep = new KernEventParser(file, parser, tt);
		
		ep.run();
		
		return ep;
	}
	
	/**
	 * Perform inference on the given model.
	 * 
	 * @param model The model on which we want to perform inference.
	 * @param nlg The NoteListGenerator which will give us the incoming note lists.
	 */
	public static void performInference(MidiModel model, NoteListGenerator nlg) {
		for (List<MidiNote> incoming : nlg.getIncomingLists()) {
			model.handleIncoming(incoming);
		}
		
		model.close();
	}
}
