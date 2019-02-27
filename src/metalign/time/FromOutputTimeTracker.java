package metalign.time;

import java.util.ArrayList;
import java.util.List;

import metalign.beat.Beat;

public class FromOutputTimeTracker extends TimeTracker {
	
	private final List<Beat> beats;
	private final List<Beat> beatsOnly;
	private TimeSignature timeSig;
	private int firstDownBeatTime;
	
	public FromOutputTimeTracker() {
		beats = new ArrayList<Beat>();
		beatsOnly = new ArrayList<Beat>();
		
		firstDownBeatTime = -1;
	}

	public void addBeat(Beat beat) {
		if (beat.isDownbeat()) {
			beatsOnly.add(beat);
			
			if (firstDownBeatTime == -1) {
				firstDownBeatTime = (int) beat.getTime();
			}
		}
		
		beats.add(beat);
	}
	
	public void setTimeSignature(TimeSignature ts) {
		timeSig = ts;
	}
    
    /**
     * Returns the time in microseconds of a given tick number.
     * 
     * @param tick The tick number to calculate the time of
     * @return The time of the given tick number, measured in microseconds since the most recent epoch.
     */
    public long getTimeAtTick(long tick) {
    	return tick;
    }
    
    /**
     * Gets the tick number at the given time, measured in microseconds.
     * 
     * @param time The time in microseconds whose tick number we want.
     * @return The tick number which corresponds to the given time.
     */
    public long getTickAtTime(long time) {
    	return time;
    }
    
    /**
     * Get a List of all of the time signatures of this TimeTracker, excluding the dummy one.
     * 
     * @return A List of all of the time signatures of this TimeTracker, excluding the dummy one.
     */
    public List<TimeSignature> getAllTimeSignatures() {
		List<TimeSignature> meters = new ArrayList<TimeSignature>();
		
		meters.add(timeSig);
		
		return meters;
	}
    
    /**
     * Get a List of the Beats found by this TimeTracker up until (but not including)
     * the {@link #lastTick}.
     * 
     * @return A List of the 32nd-note Beats of this TimeTracker until the given tick.
     */
    public List<Beat> getTatums() {
    	return beats;
    }
    
    public List<Beat> getBeatsOnly() {
    	return beatsOnly;
    }
    
    /**
     * Get the first non-dummy time signature in this song.
     * 
     * @return The TimeSignature of the first node which isn't a dummy, or the initial
     * dummy TimeSignature if there is none.
     */
    public TimeSignature getFirstTimeSignature() {
    	return timeSig;
    }
    
    /**
     * Get the anacrusis length of this TimeTracker, in ticks.
     * 
     * @return {@link #anacrusisLengthTicks}
     */
    public int getAnacrusisTicks() {
		return firstDownBeatTime;
	}
	
	@Override
	public String toString() {
		return beats.toString();
	}

	@Override
	public TimeSignature getTimeSignatureAtTime(long time) {
		return timeSig;
	}
}
