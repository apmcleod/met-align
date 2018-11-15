package metalign.hierarchy;

import java.util.List;
import java.util.TreeSet;

import metalign.beat.BeatTrackingModelState;
import metalign.generic.MidiModelState;
import metalign.utils.MidiNote;
import metalign.voice.VoiceSplittingModelState;

/**
 * A <code>HierarchyModelState</code> is used in beat hierarchy detection, and
 * contains some representation of a song's hierarchy.
 * 
 * @author Andrew McLeod - 8 Sept, 2015
 */
public abstract class HierarchyModelState extends MidiModelState {
	/**
	 * The VoiceSplittingModelState which these hierarchies will be based on.
	 */
	protected VoiceSplittingModelState voiceState;
	
	/**
	 * The BeatTrackingModelState which we are modeling jointly with.
	 */
	protected BeatTrackingModelState beatState;
	
	/**
	 * Gets the Measure which is contained by this state currently.
	 * 
	 * @return The measure contained by this state.
	 */
	public abstract Measure getMetricalMeasure();
	
	/**
	 * Get the number of tatums per sub beat.
	 * 
	 * @return The number of tatums per sub beat of this hierarchy.
	 */
	public abstract int getSubBeatLength();
	
	/**
	 * Get the length of the anacrusis, measured in sub beats.
	 * 
	 * @return The number of sub beats which fall before the first downbeat of this hierarchy.
	 */
	public abstract int getAnacrusis();
	
	/**
	 * Get the number of full bars we have gone through so far.
	 * 
	 * @return The number of full bars we have gone through so far.
	 */
	public abstract int getBarCount();
	
	/**
	 * Decide whether the given state is a duplicate of this one.
	 * 
	 * @param state The state we want to check for a duplicate.
	 * @return True if the states are duplicates. False otherwise.
	 */
	public abstract boolean isDuplicateOf(HierarchyModelState state);
	
	@Override
	public abstract TreeSet<? extends HierarchyModelState> handleIncoming(List<MidiNote> notes);
	
	@Override
	public abstract TreeSet<? extends HierarchyModelState> close();
	
	/**
	 * Set the BeatTrackingModelState which this HierarchyModelState is to be based on.
	 * 
	 * @param beatState {@link HierarchyModelState#beatState}
	 */
	public void setBeatState(BeatTrackingModelState beatState) {
		this.beatState = beatState;
	}
	
	/**
	 * Get the VoiceSplittingModelState which this hierarchy is based on.
	 * 
	 * @return {@link #voiceState}
	 */
	public VoiceSplittingModelState getVoiceState() {
		return voiceState;
	}
	
	/**
	 * Get the BeatTrackingModelState which this hierarchy is based on.
	 * 
	 * @return {@link #beatState}
	 */
	public BeatTrackingModelState getBeatState() {
		return beatState;
	}
	
	/**
	 * Set the VoiceSplittingModelState which this HierarchyModelState is to be based on.
	 * 
	 * @param voiceState {@link HierarchyModelState#voiceState}
	 */
	public void setVoiceState(VoiceSplittingModelState voiceState) {
		this.voiceState = voiceState;
	}
	
	/**
	 * Create a deep copy of this HierarchyModelState.
	 * 
	 * @return A deep copy of this HierarchyModelState.
	 */
	public abstract HierarchyModelState deepCopy();
	
	public abstract int compareToNoRecurse(MidiModelState o);

	public abstract Iterable<Measure> getMeasureTypes();
}
