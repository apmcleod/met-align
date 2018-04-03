package metalign;

/**
 * An <code>ArgumentException</code> is created when improper arguments have been given
 * to {@link Main}. This could be that a given file is not found or that some unrecognized
 * argument has been given. The method {@link #getLocalizedMessage()} has been overwritten
 * to print the usage info for {@link Main}.
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
 *  <li><code>-g FILE</code> = Load a grammar in from the given file. Used only with -Hlpcfg.</li>
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
 * @author Andrew McLeod - 16 June, 2015
 */
public class ArgumentException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2085179626730582890L;
	
	/**
	 * The first line of the Exception to print.
	 */
	private String messageHeader;
	
	/**
	 * Create a new ArgumentException with the given header message.
	 * 
	 * @param header {@link #messageHeader}
	 */
	public ArgumentException(String header) {
		messageHeader = header;
	}
	
	/**
	 * Get the message header this exception was created with.
	 * 
	 * @return {@link #messageHeader}
	 */
	public String getHeader() {
		return messageHeader;
	}

	@Override
	public String getLocalizedMessage() {
		StringBuilder sb = new StringBuilder("BeatTracking: Argument error: ");
		
		sb.append(messageHeader).append('\n');
		
		sb.append("Usage: java -cp bin metalign.Main ARGS file [directory...]\n");
		
		sb.append("-T = Use tracks as correct voice (instead of channels) *Only used for MIDI files.\n");
		sb.append("-p = Use verbose printing.\n");
		sb.append("-P = Use super verbose printing.\n");
		sb.append("-l = Print logging (time, hypothesis count, and notes at each step).\n");
		sb.append("-J = Run with incremental joint processing.\n");
		sb.append("-VClass = Use the given class for voice separation. (FromFile (default) or Hmm)\n");
		sb.append("-BClass = Use the given class for beat tracking. (FromFile (default) or Hmm).\n");
		sb.append("-HClass = Use the given class for hierarchy detection. (FromFile (default) or lpcfg).\n");
		sb.append("-g FILE = Load a grammar in from the given file. Used only with -Hlpcfg.\n");
		sb.append("-x = Extract the trees of the song for testing from the loaded grammar when testing. Used only with -Hlpcfg.\n");
		sb.append("-e = Extend each note within each voice to the next note's onset.\n");
		sb.append("-m INT = For beat tracking and hierarchy detection, throw out notes whose length is shorter than INT microseconds, once extended.\n");
		sb.append("-s INT = Use INT as the sub beat length.\n");
		sb.append("-b INT = Use INT as the beam size.\n");
		sb.append("-v INT = Use INT as the voice beam size.\n");
		sb.append("-E FILE = Print out the evaluation for each hypothesis as well with the given FILE as ground truth.\n");
		sb.append("-a FILE = Search recursively under the given FILE for anacrusis files.\n");
		
		return sb.toString();
	}
}
