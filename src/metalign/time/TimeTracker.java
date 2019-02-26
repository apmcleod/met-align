package metalign.time;

import java.util.ArrayList;
import java.util.List;

import metalign.beat.Beat;

/**
 * A <code>TimeTracker</code> contains code for converting between time and tick, and for
 * tracking time signature, beats, and tempo.
 * 
 * @author Andrew - 26 Feb, 2019
 */
public abstract class TimeTracker {
	
	protected double PPQ = 120.0;
	
	protected int anacrusisLength = 0;
	
	protected long lastTick = 0;
	
	/**
	 * The onset time of the first note in this piece.
	 */
	protected long firstNoteTime = 0;
	
	public abstract long getTimeAtTick(long tick);
	
	public abstract List<TimeSignature> getAllTimeSignatures();
	
	public abstract TimeSignature getTimeSignatureAtTime(long time);
	
	public abstract List<Beat> getTatums();
	
	public List<Beat> getBeatsOnly() {
		List<Beat> tatums = getTatums();
		
		List<Beat> beats = new ArrayList<Beat>();
		for (Beat beat : tatums) {
			if (beat.isBeat()) {
				beats.add(beat);
			}
		}
		
		return beats;
	}
	
	/**
     * Get the first non-dummy time signature in this song.
     * 
     * @return The TimeSignature at the time of the first note.
     */
    public TimeSignature getFirstTimeSignature() {
    	return getTimeSignatureAtTime(firstNoteTime);
    }
	
	/**
     * Get the anacrusis length of this TimeTracker, in ticks.
     * 
     * @return {@link #anacrusisLength}
     */
    public int getAnacrusisTicks() {
		return anacrusisLength;
	}
    
    /**
     * Set the anacrusis length of this song to the given number of ticks.
     * 
     * @param length The anacrusis length of this song, measured in ticks.
     */
    public void setAnacrusis(int length) {
		anacrusisLength = length;
	}
	
	/**
     * Set the PPQ for this TimeTracker.
     * 
     * @param ppq {@link #PPQ}
     */
    public void setPPQ(double ppq) {
    	PPQ = ppq;
    }
    
    /**
     * Get the PPQ of this TimeTracker.
     * 
     * @return {@link #PPQ}
     */
    public double getPPQ() {
    	return PPQ;
    }
    
    /**
     * Set the last tick for this song to the given value.
     * 
     * @param lastTick {@link #lastTick}
     */
    public void setLastTick(long lastTick) {
		this.lastTick = lastTick;
	}
    
    /**
     * Get the last tick for this song.
     * 
     * @return {@link #lastTick}
     */
    public long getLastTick() {
    	return lastTick;
    }
    
    /**
     * Set the onset time of the first note in this piece.
     * 
     * @param onsetTime
     */
    public void setFirstNoteTime(long onsetTime) {
		firstNoteTime = onsetTime;
	}
}
