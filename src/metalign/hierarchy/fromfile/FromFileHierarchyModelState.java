package metalign.hierarchy.fromfile;

import java.util.List;
import java.util.TreeSet;

import metalign.generic.MidiModelState;
import metalign.hierarchy.HierarchyModelState;
import metalign.hierarchy.Measure;
import metalign.time.TimeTracker;
import metalign.utils.MidiNote;

/**
 * A <code>FromFileHierarchyModelState</code> grabs the correct metrical structure from the MIDI file and
 * uses that to construct the proper hierarchy.
 * 
 * @author Andrew McLeod - 2 Dec, 2015
 */
public class FromFileHierarchyModelState extends HierarchyModelState {
	/**
	 * The Measure of this metrical structure.
	 */
	private Measure measure;
	
	/**
	 * The time of the most recent note onset we've seen so far.
	 */
	private long mostRecentTime;
	
	/**
	 * The time tracker from this hierarchy state.
	 */
	private final TimeTracker tt;
	
	/**
	 * Create a new FromFileBeatHierarchyState based on the given TimeTracker.
	 * 
	 * @param tt The TimeTracker which we will grab the proper hierarchy from.
	 */
	public FromFileHierarchyModelState(TimeTracker tt) {
		mostRecentTime = 0;
		this.tt = tt;
		measure = tt.getFirstTimeSignature().getMeasure();
		measure = new Measure(measure.getBeatsPerBar(), measure.getSubBeatsPerBeat());
	}
	
	/**
	 * Create a new FromFileHierarchyModelState and initialize the fileds as given. This is private
	 * as it is only used to clone this object from within the {@link #handleIncoming(List)} method.
	 * 
	 * @param measure {@link #measure}
	 * @param mostRecentTime {@link #mostRecentTime}
	 * @param tt {@link #tt}
	 */
	private FromFileHierarchyModelState(Measure measure, long mostRecentTime, TimeTracker tt) {
		this.measure = measure;
		this.mostRecentTime = mostRecentTime;
		this.tt = tt;
	}

	@Override
	public Measure getMeasure() {
		return measure;
	}
	
	@Override
	public int getAnacrusis() {
		return tt.getAnacrusisSubBeats();
	}
	
	@Override
	public int getBarCount() {
		return Integer.MAX_VALUE;
	}
	
	@Override
	public boolean isDuplicateOf(HierarchyModelState state) {
		return state instanceof FromFileHierarchyModelState;
	}

	@Override
	public TreeSet<FromFileHierarchyModelState> handleIncoming(List<MidiNote> notes) {
		TreeSet<FromFileHierarchyModelState> newState = new TreeSet<FromFileHierarchyModelState>();
		
		if (!notes.isEmpty()) {
			mostRecentTime = notes.get(0).getOnsetTime();
		}
		newState.add(this);
		
		return newState;
	}
	
	@Override
	public TreeSet<FromFileHierarchyModelState> close() {
		TreeSet<FromFileHierarchyModelState> newState = new TreeSet<FromFileHierarchyModelState>();
		
		newState.add(this);
		
		return newState;
	}
	
	@Override
	public HierarchyModelState deepCopy() {
		return new FromFileHierarchyModelState(measure, mostRecentTime, tt);
	}

	@Override
	public double getScore() {
		return 1.0;
	}
	
	@Override
	public int compareTo(MidiModelState other) {
		if (!(other instanceof FromFileHierarchyModelState)) {
			return -1;
		}
		
		FromFileHierarchyModelState o = (FromFileHierarchyModelState) other;
		
		int result = Long.compare(mostRecentTime, o.mostRecentTime);
		if (result != 0) {
			return result;
		}
		
		result = measure.compareTo(o.measure);
		if (result != 0) {
			return result;
		}
		
		if (voiceState.compareTo(o.voiceState) == 0
				&& beatState.compareToNoRecurse(o.beatState) == 0) {
			return 0;
		}
		return 1;
	}
	
	@Override
	public int compareToNoRecurse(MidiModelState other) {
		if (!(other instanceof FromFileHierarchyModelState)) {
			return -1;
		}
		
		FromFileHierarchyModelState o = (FromFileHierarchyModelState) other;
		
		int result = Long.compare(mostRecentTime, o.mostRecentTime);
		if (result != 0) {
			return result;
		}
		
		result = measure.compareTo(o.measure);
		if (result != 0) {
			return result;
		}
		
		return 0;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		sb.append(measure);
		sb.append(" Score=").append(getScore());
		
		return sb.toString();
	}
}
