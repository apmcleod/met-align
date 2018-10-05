package metalign.time;

import java.util.ArrayList;
import java.util.List;

import metalign.beat.Beat;
import metalign.hierarchy.Measure;

public class NoteBTimeTracker extends TimeTracker {
	
	private final List<Beat> beats;
	private final List<Beat> beatsOnly;
	private TimeSignature timeSig;
	private int anacrusisSubBeats;
	private int firstDownBeatTime;
	private int downBeatLevel;
	private int tatumsPerBar;
	private int barNum;
	private int tatumNum;
	private int minimumForBeat;
	
	public NoteBTimeTracker() {
		this(-1);
	}
	
	public NoteBTimeTracker(int subBeatLength) {
		beats = new ArrayList<Beat>();
		beatsOnly = new ArrayList<Beat>();
		super.subBeatLength = subBeatLength;
		
		anacrusisSubBeats = 0;
		firstDownBeatTime = -1;
		barNum = 0;
		downBeatLevel = 0;
		tatumsPerBar = 0;
		tatumNum = 0;
		minimumForBeat = 0;
	}

	public void addBeat(long time, int level) {
		if (level == 0) {
			// Skip level 0. Extrapolate.
			return;
		}
		
		if (level >= minimumForBeat) {
			int tatumsPerBeat = tatumsPerBar / timeSig.getMetricalMeasure().getBeatsPerMeasure();
			beatsOnly.add(new Beat(barNum, tatumNum / tatumsPerBeat, time, time));
		}
		
		if (firstDownBeatTime == -1 && level == downBeatLevel) {
			firstDownBeatTime = (int) time;
		}
		
		beats.add(new Beat(barNum, tatumNum, time, time));
		
		tatumNum++;
		if (tatumNum == tatumsPerBar) {
			tatumNum = 0;
			barNum++;
		}
	}
	
	public void setTimeSignature(TimeSignature ts) {
		timeSig = ts;
		Measure measure = ts.getMetricalMeasure();
		
		if ((measure.getBeatsPerMeasure() == 4) || (measure.getBeatsPerMeasure() == 2 && measure.getSubBeatsPerBeat() == 3)) {
			downBeatLevel = 4;
			
		} else {
			downBeatLevel = 3;
		}
		
		if (measure.getBeatsPerMeasure() == 2 && measure.getSubBeatsPerBeat() == 3) {
			minimumForBeat = 3;
			
		} else {
			minimumForBeat = 2;
		}
		
		tatumsPerBar = measure.getBeatsPerMeasure() * measure.getSubBeatsPerBeat();
		
		if (subBeatLength == -1) {
			subBeatLength = ts.getNotes32PerBar() / tatumsPerBar;
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
     * @return A List of the 32nd-note Beats of this TimeTracker until the given tick.
     */
    public List<Beat> getTatums() {
    	return fixBeatsGivenSubBeatLength(beats);
    }
    
    public List<Beat> getBeatsOnly() {
    	return beatsOnly;
    }
    
    /**
	 * Fix the given Beats List based on the set {@link #subBeatLength}. That is, remove or add
	 * tacti as needed to get the desired number of tacti per sub beat.
	 * 
	 * @param oldBeats The old Beat List.
	 * 
	 * @return The new, fixed Beat List.
	 */
	private List<Beat> fixBeatsGivenSubBeatLength(List<Beat> oldBeats) {
		if (subBeatLength < 0) {
			return oldBeats;
		}
		
		List<Beat> tatums = oldBeats;
		List<Beat> beats = new ArrayList<Beat>();
		
		for (int i = 1; i < tatums.size(); i++) {
			Beat initialBeat = tatums.get(i - 1);
			Beat finalBeat = tatums.get(i);
			long initialTime = initialBeat.getTime();
			long finalTime = finalBeat.getTime();
			
			beats.add(new Beat(initialBeat.getBar(), initialBeat.getTatum() * subBeatLength, initialTime, initialTime));
			
			double timeDiff = ((double) (finalTime - initialTime)) / subBeatLength;
			for (int j = 1; j < subBeatLength; j++) {
				beats.add(new Beat(initialBeat.getBar(), initialBeat.getTatum() * subBeatLength + j, Math.round(initialTime + timeDiff * j), Math.round(initialTime + timeDiff * j))); 
			}
		}
		
		if (tatums.size() > 0) {
			Beat lastBeat = tatums.get(tatums.size() - 1);
			beats.add(new Beat(lastBeat.getBar(), lastBeat.getTatum() * subBeatLength, lastBeat.getTime(), lastBeat.getTime()));
		}
		
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
		return firstDownBeatTime;
	}
    
    /**
     * Set the anacrusis length of this song to the given number of ticks.
     * 
     * @param length The anacrusis length of this song, measured in ticks.
     */
    public void setAnacrusisSubBeats(int length) {
		anacrusisSubBeats = length;
		if (anacrusisSubBeats != 0) {
			barNum = -1;
			tatumNum = tatumsPerBar - anacrusisSubBeats;
		}
	}
	
	@Override
	public String toString() {
		return beats.toString();
	}

}
