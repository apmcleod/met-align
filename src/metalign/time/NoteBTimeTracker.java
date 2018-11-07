package metalign.time;

import java.util.ArrayList;
import java.util.List;

import metalign.beat.Beat;
import metalign.hierarchy.Measure;

public class NoteBTimeTracker extends TimeTracker {
	
	private final List<Beat> beats;
	private TimeSignature timeSig;
	private int anacrusisSubBeats;
	
	private int barNum;
	private int beatNum;
	private int subBeatNum;
	
	private int downBeatLevel;
	private int minimumForBeat;
	private int minimumForSubBeat;
	
	public NoteBTimeTracker() {
		beats = new ArrayList<Beat>();
		
		anacrusisSubBeats = 0;
		barNum = 0;
		beatNum = 0;
		subBeatNum = 0;
		
		downBeatLevel = 0;
		minimumForBeat = 0;
		minimumForSubBeat = 0;
	}

	public void addBeat(long time, int level) {
		if (level == 0) {
			// Skip level 0. Extrapolate.
			return;
		}
		
		if (level == downBeatLevel) {
			barNum++;
			beatNum = 0;
			subBeatNum = 0;
			
		} else if (level >= minimumForBeat) {
			beatNum++;
			subBeatNum = 0;
			
		} else if (level >= minimumForSubBeat) {
			subBeatNum++;
		}
		
		if (level >= minimumForSubBeat) {
			Beat beat = new Beat(barNum, beatNum, subBeatNum, 0, time , time);
			beats.add(beat);
		}
	}
	
	public void setTimeSignature(TimeSignature ts) {
		timeSig = ts;
		Measure measure = ts.getMeasure();
		
		if ((measure.getBeatsPerBar() == 4) || (measure.getBeatsPerBar() == 2 && measure.getSubBeatsPerBeat() == 3)) {
			downBeatLevel = 4;
			
		} else {
			downBeatLevel = 3;
		}
		
		if (measure.getBeatsPerBar() == 2 && measure.getSubBeatsPerBeat() == 3) {
			minimumForBeat = 3;
			minimumForSubBeat = 2;
			
		} else {
			minimumForBeat = 2;
			minimumForSubBeat = 1;
		}
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
     * @return A List of the tatums found by this TimeTracker down to the sub beat level.
     */
    public List<Beat> getTatums() {
    	return beats;
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
     * Get the anacrusis length of this TimeTracker, in sub beats.
     * 
     * @return {@link #anacrusisLength}, in sub beats
     */
    public int getAnacrusisSubBeats() {
    	return anacrusisSubBeats;
	}
    
    /**
     * Get the anacrusis length of this TimeTracker, in ticks.
     * 
     * @return {@link #anacrusisLength}
     */
    public int getAnacrusisTicks() {
		for (Beat beat : beats) {
			if (beat.isDownbeat()) {
				return (int) beat.getTime();
			}
		}
		
		return -1;
	}
    
    /**
     * Set the anacrusis length of this song to the given number of ticks.
     * 
     * @param length The anacrusis length of this song, measured in ticks.
     */
    public void setAnacrusisSubBeats(int length) {
		anacrusisSubBeats = length;
		
		if (anacrusisSubBeats != 0) {
			subBeatNum = (-anacrusisSubBeats + timeSig.getMeasure().getSubBeatsPerBeat() * timeSig.getMeasure().getBeatsPerBar()) % timeSig.getMeasure().getSubBeatsPerBeat() - 1 - 1;
			beatNum = (-anacrusisSubBeats + timeSig.getMeasure().getSubBeatsPerBeat() * timeSig.getMeasure().getBeatsPerBar()) % timeSig.getMeasure().getBeatsPerBar();
		}
	}
	
	@Override
	public String toString() {
		return beats.toString();
	}

}
