package metalign.beat.frombeats;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import metalign.beat.Beat;
import metalign.beat.BeatTrackingModelState;
import metalign.generic.MidiModelState;
import metalign.utils.MidiNote;

public class FromBeatsBeatTrackingModelState extends BeatTrackingModelState {
	/**
	 * A List of the Beats generated from the given TimeTracker.
	 */
	private final List<Beat> beats;
	
	/**
	 * A List of the Beat times.
	 */
	private final List<Integer> beatTimes;
	
	/**
	 * The index of the current Beat in our Beats list.
	 */
	private int mostRecentIndex;
	
	/**
	 * The most recent time for which we have seen a note offset so far, initially 0.
	 */
	private long mostRecentTime;
	
	public FromBeatsBeatTrackingModelState(List<Beat> tatums) {
		beats = tatums;
		
		beatTimes = new ArrayList<Integer>(beats.size());
		for (Beat beat : beats) {
			beatTimes.add((int) beat.getTime());
		}
		
		mostRecentTime = 0;
		mostRecentIndex = 0;
	}
	
	/**
	 * Create a new state, a copy of the given one.
	 * 
	 * @param state The state whose copy we want.
	 */
	private FromBeatsBeatTrackingModelState(FromBeatsBeatTrackingModelState state) {
		hierarchyState = state.hierarchyState;
		beats = state.beats;
		beatTimes = state.beatTimes;
		mostRecentTime = state.mostRecentTime;
		mostRecentIndex = state.mostRecentIndex;
	}
	
	@Override
	public List<Beat> getBeats() {
		// Get Beats only up to those we are supposed to have seen so far
		for (; mostRecentIndex < beats.size(); mostRecentIndex++) {
			if (beats.get(mostRecentIndex).getTime() > mostRecentTime) {
				return beats.subList(0, mostRecentIndex);
			}
		}
		
		return beats;
	}

	@Override
	public List<Integer> getBeatTimes() {
		// Get Beat times only up to those we are supposed to have seen so far
		for (; mostRecentIndex < beats.size(); mostRecentIndex++) {
			if (beats.get(mostRecentIndex).getTime() > mostRecentTime) {
				return beatTimes.subList(0, mostRecentIndex);
			}
		}
		
		return beatTimes;
	}

	@Override
	public int getLastTatumTime() {
		List<Integer> tatums = getBeatTimes();
		return tatums.isEmpty() ? -1 : tatums.get(tatums.size() - 1);
	}

	@Override
	public int getNumTatums() {
		return getBeatTimes().size();
	}

	@Override
	public int getBarCount() {
		return beats.get(Math.min(mostRecentIndex, beats.size() - 1)).getBar();
	}

	@Override
	public TreeSet<? extends BeatTrackingModelState> handleIncoming(List<MidiNote> notes) {
		TreeSet<FromBeatsBeatTrackingModelState> newState = new TreeSet<FromBeatsBeatTrackingModelState>();
		
		if (!notes.isEmpty()) {
			mostRecentTime = notes.get(0).getOnsetTime();
		}
		newState.add(this);
		
		return newState;
	}

	@Override
	public TreeSet<? extends BeatTrackingModelState> close() {
		TreeSet<FromBeatsBeatTrackingModelState> newState = new TreeSet<FromBeatsBeatTrackingModelState>();
		
		mostRecentIndex = beats.size();
		mostRecentTime = beats.get(beats.size() - 1).getTime();
		newState.add(this);
		
		return newState;
	}
	
	@Override
	public double getScore() {
		return 0.0;
	}
	
	@Override
	public BeatTrackingModelState deepCopy() {
		return new FromBeatsBeatTrackingModelState(this);
	}
	
	@Override
	public boolean isDuplicateOf(BeatTrackingModelState state) {
		return state instanceof FromBeatsBeatTrackingModelState;
	}

	@Override
	public int compareTo(MidiModelState other) {
		if (!(other instanceof FromBeatsBeatTrackingModelState)) {
			return -1;
		}
		
		FromBeatsBeatTrackingModelState o = (FromBeatsBeatTrackingModelState) other;
		
		int result = Double.compare(o.getScore(), getScore());
		if (result != 0) {
			return result;
		}
		
		result = beats.size() - o.beats.size();
		if (result != 0) {
			return result;
		}
		
		for (int i = 0; i < beats.size(); i++) {
			result = beats.get(i).compareTo(o.beats.get(i));
			if (result != 0) {
				return result;
			}
		}
		
		return hierarchyState.compareTo(o.hierarchyState);
	}
	
	@Override
	public int compareToNoRecurse(MidiModelState other) {
		if (!(other instanceof FromBeatsBeatTrackingModelState)) {
			return -1;
		}
		
		FromBeatsBeatTrackingModelState o = (FromBeatsBeatTrackingModelState) other;
		
		int result = Double.compare(o.getScore(), getScore());
		if (result != 0) {
			return result;
		}
		
		result = beats.size() - o.beats.size();
		if (result != 0) {
			return result;
		}
		
		for (int i = 0; i < beats.size(); i++) {
			result = beats.get(i).compareTo(o.beats.get(i));
			if (result != 0) {
				return result;
			}
		}
		
		return 0;
	}
}
