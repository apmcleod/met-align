package metalign.hierarchy.lpcfg;

import java.util.List;

import metalign.beat.Beat;
import metalign.hierarchy.Measure;
import metalign.utils.MidiNote;

/**
 * A <code>MetricalLpcfgTreeFactory</code> is a class whose static methods aid in the
 * creation of {@link MetricalLpcfgTree}s. It cannot be instantiated.
 * 
 * @author Andrew McLeod - 25 April, 2016
 */
public class MetricalLpcfgTreeFactory {
	/**
	 * Private constructor to ensure that no factory is instantiated.
	 */
	private MetricalLpcfgTreeFactory() {}
	
	public static MetricalLpcfgTree makeTree(Measure measure, double prevTime, List<Beat> barBeats, double nextTime,
			List<MidiNote> notes) {
		// TODO Auto-generated method stub
		return null;
	}

	public static int getNumTatumsAfter(MetricalLpcfgTree tree, Beat anchor) {
		// TODO Auto-generated method stub
		return 0;
	}
}
