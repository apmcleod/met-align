package metalign.parsing;

import java.io.IOException;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;

import metalign.utils.MidiNote;

/**
 * An <code>EventParser</code> handles the interfacing between this program and music files.
 * It reads note-events from a file with {@link #run()}.
 * <p>
 * One EventParser is required per song you wish to parse.
 * 
 * @author Andrew McLeod - 23 October, 2014
 */
public interface EventParser {
	/**
     * Parses the events from the loaded music file.
     * 
     * @throws InvalidMidiDataException If a note off event doesn't match any previously seen note on.
     * @throws InterruptedException If this is running on a GUI and gets cancelled.
	 * @throws IOException If an I/O error occurred while reading the file.
     */
    public void run() throws InvalidMidiDataException, InterruptedException, IOException;
    
    /**
     * Get a List of the gold standard voices from this song.
     * 
     * @return A List of the gold standard voices from this song.
     */
    public List<List<MidiNote>> getGoldStandardVoices();
    
    /**
     * Get the time of the onset of the first note in the parsed file.
     * 
     * @return The time of the first onset in the file.
     */
    public long getFirstNoteTime();
}
