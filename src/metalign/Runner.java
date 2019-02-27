package metalign;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;

import metalign.generic.MidiModel;
import metalign.parsing.MidiEventParser;
import metalign.parsing.NoteEventParser;
import metalign.parsing.NoteListGenerator;
import metalign.time.MidiTimeTracker;
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
	public static final String DEFAULT_BEAT_TRACKER = "FromFile";
	
	/**
	 * The default hierarchy model to use.
	 */
	public static final String DEFAULT_HIERARCHY_MODEL = "FromFile";
	
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
	public static MidiEventParser parseMidiFile(File file, NoteEventParser parser, MidiTimeTracker tt, boolean useChannel)
			throws InvalidMidiDataException, IOException, InterruptedException {
		MidiEventParser ep = new MidiEventParser(file, parser, tt, useChannel);
		
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
