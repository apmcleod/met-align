package metalign.hierarchy.lpcfg;

/**
 * A <code>MalformedTreeException</code> is thrown any time there is some error
 * with the structure of a {@link MetricalLpcfgTree}. 
 * 
 * @author Andrew McLeod
 */
public class MalformedTreeException extends Exception {
	/**
	 * The serial ID.
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * The error message to print.
	 */
	private final String message;
	
	/**
	 * Create a new Exception with the given message.
	 * 
	 * @param msg
	 */
	public MalformedTreeException(String msg) {
		message = msg;
	}
	
	@Override
	public String getLocalizedMessage() {
		return "Malformed Tree Exception: " + message;
	}
}