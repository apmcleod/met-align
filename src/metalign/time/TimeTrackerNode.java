package metalign.time;

/**
 * A <code>TimeTrackerNode</code> represents the state of a musical score at a given time. That is,
 * it represents a ({@link metalign.time.TimeSignature},
 * {@link metalign.time.Tempo}, {@link metalign.time.KeySignature})
 * triple, and contains information about the times at which that triple is contiguously valid.
 * 
 * @author Andrew McLeod - 11 Feb, 2015
 */
public class TimeTrackerNode {
	/**
	 * The start tick for this TimeTrackerNode. That is, the tick at which this ones triple becomes
	 * valid.
	 */
	private long startTick = 0L;
	
	/**
	 * The start time for this TimeTrackerNode, measured in microseconds. That is, the time at which
	 * this one's triple becomes valid.
	 */
	private long startTime = 0L;
	
	/**
	 * The TimeSignature associated with this TimeTrackerNode.
	 */
	private TimeSignature timeSignature = null;
	
	/**
	 * The Tempo associated with this TimeTrackerNode.
	 */
	private Tempo tempo = null;
	
	/**
	 * The KeySignature associated with this TimeTrackerNode.
	 */
	private KeySignature keySignature = null;
	
	/**
	 * Whether the Time Signature in this Node is a dummy first node or not.
	 */
	private boolean isTimeSignatureDummy;
	
	/**
	 * Create a new dummy first TimeTrackerNode at tick and time 0.
	 * 
	 * @param ppq The pulses per quarter note of the song.
	 */
	public TimeTrackerNode(double ppq) {
		startTick = 0L;
		isTimeSignatureDummy = true;
		
		timeSignature = new TimeSignature();
		tempo = new Tempo();
		keySignature = new KeySignature();
	}
	
	/**
	 * Create a new TimeTrackerNode with the given previous TimeTrackerNode at the given tick.
	 * 
	 * @param prev The previous TimeTrackerNode, cannot be null.
	 * @param ppq The pulses per quarter note of the song.
	 * @param tick The tick at which this new one becomes valid.
	 */
	public TimeTrackerNode(TimeTrackerNode prev, long tick, double ppq) {
		startTick = tick;
		isTimeSignatureDummy = prev.isTimeSignatureDummy;
		
		startTime = prev.getTimeAtTick(tick, ppq);
		timeSignature = prev.getTimeSignature();
		tempo = prev.getTempo();
		keySignature = prev.getKeySignature();
	}
	
	/**
	 * Get the tick number at the given time.
	 * 
	 * @param time The time at which we want the tick, measured in microseconds.
	 * @param ppq The pulses per quarter note of the song.
	 * @return The tick at the given time.
	 */
	public long getTickAtTime(long time, double ppq) {
		long timeOffset = time - getStartTime();
		return (long) (timeOffset / getTimePerTick(ppq)) + getStartTick();
	}
	
	/**
	 * Get the time at the given tick.
	 * 
	 * @param tick The tick at which we want the time.
	 * @param ppq The pulses per quarter note of the song.
	 * @return The time at the given tick, measured in microseconds.
	 */
	public long getTimeAtTick(long tick, double ppq) {
		long tickOffset = tick - getStartTick();
		return (long) (tickOffset * getTimePerTick(ppq)) + getStartTime();
	}
	
	/**
	 * Gets the amount of time, in microseconds, that passes between each tick.
	 * 
	 * @param ppq The pulses per quarter note of the song.
	 * @return The length of a tick in microseconds.
	 */
	public double getTimePerTick(double ppq) {
		return tempo.getMicroSecondsPerQuarter() / ppq;
	}
	
	/**
	 * Get the start tick of this node.
	 * 
	 * @return {@link #startTick}
	 */
	public long getStartTick() {
		return startTick;
	}
	
	/**
	 * Get the start time of this node.
	 * 
	 * @return {@link #startTime}
	 */
	public long getStartTime() {
		return startTime;
	}
	
	/**
	 * Set the Tempo of this node.
	 * 
	 * @param tempo
	 */
	public void setTempo(Tempo tempo) {
		this.tempo = tempo;
	}
	
	/**
	 * Set the TimeSignature of this node.
	 * 
	 * @param timeSignature {@link #timeSignature}
	 */
	public void setTimeSignature(TimeSignature timeSignature) {
		this.timeSignature = timeSignature;
		isTimeSignatureDummy = false;
	}
	
	/**
	 * Get if this node has a dummy time signature.
	 * 
	 * @return {@link #isTimeSignatureDummy}
	 */
	public boolean isTimeSignatureDummy() {
		return isTimeSignatureDummy;
	}
	
	/**
	 * Set if this node's time signature is a dummy node.
	 * 
	 * @param dummy {@link #isTimeSignatureDummy}
	 */
	public void setIsTimeSignatureDummy(boolean dummy) {
		isTimeSignatureDummy = dummy;	
	}
	
	/**
	 * Set the KeySignature of this node.
	 * 
	 * @param keySignature
	 */
	public void setKeySignature(KeySignature keySignature) {
		this.keySignature = keySignature;
	}
	
	/**
	 * Get the TimeSignature of this node.
	 * 
	 * @return {@link #timeSignature}
	 */
	public TimeSignature getTimeSignature() {
		return timeSignature;
	}
	
	/**
	 * Get the Tempo of this node.
	 * 
	 * @return {@link #tempo}
	 */
	public Tempo getTempo() {
		return tempo;
	}
	
	/**
	 * Get the KeySignature of this node.
	 * 
	 * @return {@link #keySignature}
	 */
	public KeySignature getKeySignature() {
		return keySignature;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("{Tick=");
		sb.append(startTick);
		sb.append(" Time=").append(startTime);
		sb.append(" Key=").append(getKeySignature());
		sb.append(" ").append(getTimeSignature());
		sb.append(" ").append(getTempo()).append('}');
		return sb.toString();
	}
}
