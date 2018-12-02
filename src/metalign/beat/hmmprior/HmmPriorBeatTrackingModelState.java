package metalign.beat.hmmprior;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

import metalign.beat.Beat;
import metalign.beat.BeatTrackingModelState;
import metalign.beat.hmm.HmmBeatTrackingModelParameters;
import metalign.generic.MidiModelState;
import metalign.utils.MidiNote;

public class HmmPriorBeatTrackingModelState extends BeatTrackingModelState {
	/**
	 * The log probability of this sequence of beats having occurred.
	 */
	private double logProb;
	
	/**
	 * The tatums which we have found so far.
	 */
	private final List<Integer> tatums;
	
	/**
	 * The tempo of the previous bar.
	 */
	private double previousTempo;
	
	/**
	 * The number of full bars we've seen so far.
	 */
	private int barCount;
	
	/**
	 * The parameters for this model.
	 */
	private final HmmBeatTrackingModelParameters params;
	
	/**
	 * The downbeat priors for this model.
	 */
	private final DownbeatPriors priors;
	
	/**
	 * A LinkedList of those note times which we haven't assigned to a Beat yet.
	 */
	private final LinkedList<Integer> unusedNoteTimes;
	
	public HmmPriorBeatTrackingModelState(HmmBeatTrackingModelParameters params, DownbeatPriors priors) {
		logProb = 0.0;
		tatums = new ArrayList<Integer>();
		unusedNoteTimes = new LinkedList<Integer>();
		previousTempo = 0;
		barCount = 0;
		
		this.params = params;
		this.priors = priors;
	}
	
	public HmmPriorBeatTrackingModelState(HmmPriorBeatTrackingModelState state) {
		tatums = new ArrayList<Integer>(state.tatums);
		logProb = state.logProb;
		params = state.params;
		priors = state.priors;
		unusedNoteTimes = new LinkedList<Integer>(state.unusedNoteTimes);
		previousTempo = state.previousTempo;
		barCount = state.barCount;
		
		setHierarchyState(state.hierarchyState);
		hierarchyState.setBeatState(this);
	}

	@Override
	public TreeSet<? extends BeatTrackingModelState> handleIncoming(List<MidiNote> notes) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public TreeSet<? extends BeatTrackingModelState> close() {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public List<Beat> getBeats() {
		List<Beat> beats = new ArrayList<Beat>(tatums.size());
		
		for (int i = 0; i < tatums.size(); i++) {
			beats.add(new Beat(0, i, tatums.get(i), tatums.get(i)));
		}
		
		return beats;
	}

	@Override
	public List<Integer> getBeatTimes() {
		return tatums;
	}

	@Override
	public int getLastTatumTime() {
		return tatums.isEmpty() ? -1 : tatums.get(tatums.size() - 1);
	}

	@Override
	public int getNumTatums() {
		return tatums.size();
	}

	@Override
	public int getBarCount() {
		return barCount;
	}

	@Override
	public boolean isDuplicateOf(BeatTrackingModelState state) {
		if (!(state instanceof HmmPriorBeatTrackingModelState)) {
			return false;
		}
		
		HmmPriorBeatTrackingModelState hmm = (HmmPriorBeatTrackingModelState) state;
		
		return Math.abs(previousTempo - hmm.previousTempo) < params.DIFF_MIN &&
				Math.abs(getLastTatumTime() - hmm.getLastTatumTime()) < params.DIFF_MIN;
	}

	@Override
	public BeatTrackingModelState deepCopy() {
		return new HmmPriorBeatTrackingModelState(this);
	}
	
	@Override
	public int compareTo(MidiModelState other) {
		if (!(other instanceof HmmPriorBeatTrackingModelState)) {
			return -1;
		}
		
		HmmPriorBeatTrackingModelState o = (HmmPriorBeatTrackingModelState) other;
		
		int result = Double.compare(o.getScore(), getScore());
		if (result != 0) {
			return result;
		}
		
		result = tatums.size() - o.tatums.size();
		if (result != 0) {
			return result;
		}
		
		for (int i = 0; i < tatums.size(); i++) {
			result = tatums.get(i).compareTo(o.tatums.get(i));
			if (result != 0) {
				return result;
			}
		}
		
		result = unusedNoteTimes.size() - o.unusedNoteTimes.size();
		if (result != 0) {
			return result;
		}
		
		for (int i = 0; i < unusedNoteTimes.size(); i++) {
			result = unusedNoteTimes.get(i).compareTo(o.unusedNoteTimes.get(i));
			if (result != 0) {
				return result;
			}
		}
		
		result = Double.compare(previousTempo, o.previousTempo);
		if (result != 0) {
			return result;
		}
		
		return hierarchyState.compareToNoRecurse(o.hierarchyState);
		//return 0;
	}
	
	@Override
	public int compareToNoRecurse(MidiModelState other) {
		if (!(other instanceof HmmPriorBeatTrackingModelState)) {
			return -1;
		}
		
		HmmPriorBeatTrackingModelState o = (HmmPriorBeatTrackingModelState) other;
		
		int result = Double.compare(o.getScore(), getScore());
		if (result != 0) {
			return result;
		}
		
		result = tatums.size() - o.tatums.size();
		if (result != 0) {
			return result;
		}
		
		for (int i = 0; i < tatums.size(); i++) {
			result = tatums.get(i).compareTo(o.tatums.get(i));
			if (result != 0) {
				return result;
			}
		}
		
		result = unusedNoteTimes.size() - o.unusedNoteTimes.size();
		if (result != 0) {
			return result;
		}
		
		for (int i = 0; i < unusedNoteTimes.size(); i++) {
			result = unusedNoteTimes.get(i).compareTo(o.unusedNoteTimes.get(i));
			if (result != 0) {
				return result;
			}
		}
		
		result = Double.compare(previousTempo, o.previousTempo);
		if (result != 0) {
			return result;
		}
		
		return 0;
	}

	@Override
	public double getScore() {
		return logProb;
	}

}
