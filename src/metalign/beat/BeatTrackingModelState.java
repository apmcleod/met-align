package metalign.beat;

import java.util.List;
import java.util.TreeSet;

import metalign.generic.MidiModelState;
import metalign.hierarchy.HierarchyModelState;
import metalign.utils.MidiNote;

/**
 * A <code>BeatTrackingModelState</code> is a {@link MidiModelState} which contains
 * a List of {@link Beat}s which has been created from the incoming MIDI data. In
 * order to get these beats, the {@link #getBeats()} method should be called.
 * 
 * @author Andrew McLeod - 7 Sept, 2015
 */
public abstract class BeatTrackingModelState extends MidiModelState {
	
	/**
	 * The HierarchyModelState which these beats will be based on.
	 */
	protected HierarchyModelState hierarchyState;
	
	/**
	 * Set the HierarchyModelState which this BeatTrackingModelState is to be
	 * based on.
	 * 
	 * @param hierarchyState {@link BeatTrackingModelState#hierarchyState}
	 */
	public void setHierarchyState(HierarchyModelState hierarchyState) {
		this.hierarchyState = hierarchyState;
	}

	/**
	 * Get the HierarchyModelState which this beat tracker is based on.
	 * 
	 * @return {@link #hierarchyState}
	 */
	public HierarchyModelState getHierarchyState() {
		return hierarchyState;
	}
	
	/**
	 * Gets the Beats which are contained by this state currently.
	 * 
	 * @return A List of the Beats contained by this State.
	 */
	public abstract List<Beat> getBeats();
	
	/**
	 * Gets the Beat times which are contained by this state currently.
	 * 
	 * @return A List of the beat times contained by this State.
	 */
	public abstract List<Integer> getBeatTimes();
	
	/**
	 * Check if this state has started yet. That is, have we placed any tatums yet.
	 * 
	 * @return False if {@link #getBeatTimes()} returns an empty list. False otherwise. 
	 */
	public boolean isStarted() {
		return !getBeatTimes().isEmpty();
	}
	
	/**
	 * Get the time of the last tatum.
	 * 
	 * @return The time of the last tatum.
	 */
	public abstract int getLastTatumTime();
	
	/**
	 * Get the number of tatums inthis state.
	 * 
	 * @return The number of tatums in this state.
	 */
	public abstract int getNumTatums();
	
	/**
	 * Get the number of full bars we have gone through so far.
	 * 
	 * @return The number of full bars we have gone through so far.
	 */
	public abstract int getBarCount();
	
	/**
	 * Get the number of tacti which are present in each measure of the Beats of this state.
	 * This is needed because for a {@link metalign.beat.fromfile.FromFileBeatTrackingModelState},
	 * the measures are incremented correctly, but for any other BeatTrackingModelState, this is not the case.
	 * 
	 * @return The number of tacti per measure for a {@link metalign.beat.fromfile.FromFileBeatTrackingModelState},
	 * or 0 for any other model.
	 */
	public int getTatumsPerMeasure() {
		return 0;
	}
	
	/**
	 * Decide whether the given state is a duplicate of this one.
	 * 
	 * @param state The state we want to check for a duplicate.
	 * @return True if the states are duplicates. False otherwise.
	 */
	public abstract boolean isDuplicateOf(BeatTrackingModelState state);
	
	/**
	 * Create a deep copy of this BeatTrackingModelState.
	 * 
	 * @return A deep copy of this BeatTrackingModelState.
	 */
	public abstract BeatTrackingModelState deepCopy();
	
	@Override
	public abstract TreeSet<? extends BeatTrackingModelState> handleIncoming(List<MidiNote> notes);
	
	@Override
	public abstract TreeSet<? extends BeatTrackingModelState> close();
	
	public abstract int compareToNoRecurse(MidiModelState o);
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("[");
		
		for (Beat beat : getBeats()) {
			sb.append(beat).append(',');
		}
		
		sb.setCharAt(sb.length() - 1, ']');
		
		sb.append(' ').append(getScore());
		return sb.toString();
	}

	/**
	 * Get a comma-separated string of tatum times beginning at start,
	 * and getting every "step"th tatum.
	 * 
	 * @param start The index to start from.
	 * @param step The step length to return.
	 * @return A comma-separated String of the wanted tatum times.
	 */
	public String getTatumTimesString(int start, int step) {
		StringBuilder sb = new StringBuilder();
		
		List<Beat> beats = getBeats();
		for (int i = start; i < beats.size(); i += step) {
			Beat beat = beats.get(i);
			sb.append(beat.getTime()).append(',');
		}
		
		if (sb.length() > 0) {
			sb.deleteCharAt(sb.length() - 1);
		}
		
		return sb.toString();
	}

	/**
	 * Get the tatum times of this state as a comma-separated String.
	 * 
	 * @return The tatum times, in microseconds, separated by commas.
	 */
	public String getTatumTimesString() {
		return getTatumTimesString(0, 1);
	}
	
	/**
	 * Get the sub-beat times of this state as a comma-separated String.
	 * 
	 * @return The sub-beat times, in microseconds, separated by commas.
	 */
	public String getSubBeatTimesString() {
		return getTatumTimesString(0, hierarchyState.getSubBeatLength());
	}
	
	/**
	 * Get the beat times of this state as a comma-separated String.
	 * 
	 * @return The beat times, in microseconds, separated by commas.
	 */
	public String getBeatTimesString() {
		int anacrusis = hierarchyState.getAnacrusis();
		int subBeatsPerBeat = hierarchyState.getMetricalMeasure().getSubBeatsPerBeat();
		int subBeatLength = hierarchyState.getSubBeatLength();
		int beatLength = subBeatsPerBeat * subBeatLength;
		
		return getTatumTimesString((anacrusis % subBeatsPerBeat) * subBeatLength, beatLength);
	}
	
	/**
	 * Get the downbeat times of this state as a comma-separated String.
	 * 
	 * @return The downbeat times, in microseconds, separated by commas.
	 */
	public  String getDownbeatTimesString() {
		int anacrusis = hierarchyState.getAnacrusis();
		int barLength = hierarchyState.getMetricalMeasure().getBeatsPerMeasure() * hierarchyState.getMetricalMeasure().getSubBeatsPerBeat() * hierarchyState.getSubBeatLength();
		
		return getTatumTimesString(anacrusis * hierarchyState.getSubBeatLength(), barLength);
	}
}
