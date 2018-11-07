package metalign.time;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;

import metalign.beat.Beat;

/**
 * A <code>TimeTracker</code> is able to interpret MIDI tempo, key, and time signature change events and keep track
 * of the song timing in seconds, instead of just using ticks as MIDI events do. It does this by using
 * a LinkedList of {@link TimeTrackerNode} objects.
 * 
 * @author Andrew McLeod - 23 October, 2014
 */
public class TimeTracker {
	/**
	 * Pulses (ticks) per Quarter note, as in the current Midi song's header.
	 */
	private double PPQ = 120.0;
	
	/**
	 * The LInkedList of TimeTrackerNodes of this TimeTracker, ordered by time.
	 */
	private final LinkedList<TimeTrackerNode> nodes;
	
	/**
	 * The number of ticks which lie before the first full measure in this song.
	 */
	private int anacrusisLength;
	
	/**
	 * The last tick for any event in this song, initially 0.
	 */
	private long lastTick = 0;
	
	/**
	 * The onset time of the first note in this piece.
	 */
	private long firstNoteTime = 0;
    
    /**
	 * Create a new TimeTracker.
	 */
    public TimeTracker() {
    	anacrusisLength = 0;
    	nodes = new LinkedList<TimeTrackerNode>();
    	nodes.add(new TimeTrackerNode(PPQ));
    }
    
    /**
     * A TimeSignature event was detected. Deal with it.
     * 
     * @param event The event.
     * @param mm The message from the event.
     */
    public void addTimeSignatureChange(MidiEvent event, MetaMessage mm) {
    	TimeSignature ts = new TimeSignature(mm.getData());
    	
    	if (nodes.getLast().getStartTick() > event.getTick()) {
    		return;
    	}
    	
    	if (nodes.getLast().getStartTick() == event.getTick()) {
    		// If we're at the same time as a prior time change, combine this with that node.
    		nodes.getLast().setTimeSignature(ts);
    		
    	} else if (!ts.equals(nodes.getLast().getTimeSignature())) {
    		// Some change has been made
    		nodes.add(new TimeTrackerNode(nodes.getLast(), event.getTick(), PPQ));
    		nodes.getLast().setTimeSignature(ts);
    	}
    	
    	nodes.getLast().setIsTimeSignatureDummy(false);
    }
    
    /**
     * Change the time signature to the given value.
     * 
     * @param tick The tick at which the time signature was changed.
     * @param numerator The numerator.
     * @param denominator The denominator.
     */
    public void addTimeSignatureChange(long tick, int numerator, int denominator) {
		TimeSignature ts = new TimeSignature(numerator, denominator);
		
		if (nodes.getLast().getStartTick() > tick) {
    		return;
    	}
		
		if (nodes.getLast().getStartTick() == tick) {
    		// If we're at the same time as a prior time change, combine this with that node.
    		nodes.getLast().setTimeSignature(ts);
    		
    	} else if (!ts.equals(nodes.getLast().getTimeSignature())) {
    		nodes.add(new TimeTrackerNode(nodes.getLast(), tick, PPQ));
    		nodes.getLast().setTimeSignature(ts);
    	}
		
		nodes.getLast().setIsTimeSignatureDummy(false);
	}
    
    /**
     * A Tempo event was detected. Deal with it.
     * 
     * @param event The event.
     * @param mm The message from the event.
     */
    public void addTempoChange(MidiEvent event, MetaMessage mm) {
    	Tempo t = new Tempo(mm.getData());
    	
    	if (nodes.getLast().getStartTick() > event.getTick()) {
    		return;
    	}
    	
    	if (nodes.getLast().getStartTick() == event.getTick()) {
    		// If we're at the same time as a prior time change, combine this with that node.
    		nodes.getLast().setTempo(t);
    		
    	} else if (!t.equals(nodes.getLast().getTempo())) {
    		nodes.add(new TimeTrackerNode(nodes.getLast(), event.getTick(), PPQ));
    		nodes.getLast().setTempo(t);
    	}
    }
    
    /**
     * Change the tempo to the given number of quarter notes per minute.
     * 
     * @param tick The tick at which to make the given change.
     * @param qpm The number of quarter notes per minute for the new tempo.
     */
    public void addTempoChange(long tick, int qpm) {
		Tempo t = new Tempo(qpm);
		
		if (nodes.getLast().getStartTick() > tick) {
    		return;
    	}
		
		if (nodes.getLast().getStartTick() == tick) {
    		// If we're at the same time as a prior time change, combine this with that node.
    		nodes.getLast().setTempo(t);
    		
    	} else if (!t.equals(nodes.getLast().getTempo())) {
    		nodes.add(new TimeTrackerNode(nodes.getLast(), tick, PPQ));
    		nodes.getLast().setTempo(t);
    	}
	}
    
    /**
     * A KeySignature event was detected. Deal with it.
     * 
     * @param event The event.
     * @param mm The message from the event.
     */
    public void addKeySignatureChange(MidiEvent event, MetaMessage mm) {
    	KeySignature ks = new KeySignature(mm.getData());
    	
    	if (nodes.getLast().getStartTick() > event.getTick()) {
    		return;
    	}
    	
    	if (nodes.getLast().getStartTick() == event.getTick()) {
    		// If we're at the same time as a prior time change, combine this with that node.
    		nodes.getLast().setKeySignature(ks);
    		
    	} else if (!ks.equals(nodes.getLast().getKeySignature())) {
    		nodes.add(new TimeTrackerNode(nodes.getLast(), event.getTick(), PPQ));
    		nodes.getLast().setKeySignature(ks);
    	}
	}
    
    /**
     * Change the key to the given value.
     * 
     * @param tick The tick at which to change the key.
     * @param keyNumber The key number to change to.
     * @param isMajor Whether that key is major or minor.
     */
    public void addKeySignatureChange(long tick, int keyNumber, boolean isMajor) {
		KeySignature ks = new KeySignature(keyNumber, isMajor);
		
		if (nodes.getLast().getStartTick() > tick) {
    		return;
    	}
		
		if (nodes.getLast().getStartTick() == tick) {
    		// If we're at the same time as a prior time change, combine this with that node.
    		nodes.getLast().setKeySignature(ks);
    		
    	} else if (!ks.equals(nodes.getLast().getKeySignature())) {
    		nodes.add(new TimeTrackerNode(nodes.getLast(), tick, PPQ));
    		nodes.getLast().setKeySignature(ks);
    	}
	}
    
    /**
     * Returns the time in microseconds of a given tick number.
     * 
     * @param tick The tick number to calculate the time of
     * @return The time of the given tick number, measured in microseconds since the most recent epoch.
     */
    public long getTimeAtTick(long tick) {
    	return getNodeAtTick(tick).getTimeAtTick(tick, PPQ);
    }
    
    /**
     * Get the TimeTrackerNode which is valid at the given tick.
     * 
     * @param tick The tick.
     * @return The valid TimeTrackerNode.
     */
    private TimeTrackerNode getNodeAtTick(long tick) {
    	ListIterator<TimeTrackerNode> iterator = nodes.listIterator();
    	
    	TimeTrackerNode node = iterator.next();
    	while (iterator.hasNext()) {
    		node = iterator.next();
    		
    		if (node.getStartTick() > tick) {
    			iterator.previous();
    			return iterator.previous();
    		}
    	}

    	return node;
    }
    
    /**
     * Gets the tick number at the given time, measured in microseconds.
     * 
     * @param time The time in microseconds whose tick number we want.
     * @return The tick number which corresponds to the given time.
     */
    public long getTickAtTime(long time) {
    	return getNodeAtTime(time).getTickAtTime(time, PPQ);
    }
    
    /**
     * Get the TimeTrackerNode which is valid at the given time.
     * 
     * @param time The time.
     * @return The valid TimeTrackerNode.
     */
    public TimeTrackerNode getNodeAtTime(long time) {
    	ListIterator<TimeTrackerNode> iterator = nodes.listIterator();
    	
    	TimeTrackerNode node = iterator.next();
    	while (iterator.hasNext()) {
    		node = iterator.next();
    		
    		if (node.getStartTime() > time) {
    			iterator.previous();
    			return iterator.previous();
    		}
    	}

    	return node;
    }
    
    /**
     * Get a List of all of the time signatures of this TimeTracker, excluding the dummy one.
     * 
     * @return A List of all of the time signatures of this TimeTracker, excluding the dummy one.
     */
    public List<TimeSignature> getAllTimeSignatures() {
		List<TimeSignature> meters = new ArrayList<TimeSignature>();
		
		int skipped = 0;
		for (TimeTrackerNode node : nodes) {
			if (!node.isTimeSignatureDummy() && node.getStartTime() >= firstNoteTime) {
				TimeSignature meter = node.getTimeSignature();
				
				if (meters.isEmpty() && node.getStartTime() > firstNoteTime) {
					meters.add(nodes.get(skipped - 1).getTimeSignature());
				}
				
				// First meter or new meter
				if (meters.isEmpty() || !meter.equals(meters.get(meters.size() - 1))) {
					meters.add(meter);
				}
			} else {
				skipped++;
			}
		}
		
		// Add the dummy
		if (meters.isEmpty()) {
			meters.add(nodes.get(0).getTimeSignature());
		}
		
		return meters;
	}
    
    /**
     * Get a List of the tatums found by this TimeTracker up until (but not including)
     * the {@link #lastTick}.
     * 
     * @return A List of the tatums of this TimeTracker, down to the sub beat level.
     */
    public List<Beat> getTatums() {
    	List<Beat> beats = new ArrayList<Beat>();
    	
    	TimeTrackerNode firstNode = getNodeAtTick(0);
    	int ticksPerNote32 = (int) (PPQ / 8);
    	int notes32PerBar = firstNode.getTimeSignature().getNotes32PerBar();
    	int beatsPerBar = firstNode.getTimeSignature().getMeasure().getBeatsPerBar();
    	int subBeatsPerBeat = firstNode.getTimeSignature().getMeasure().getSubBeatsPerBeat();
    	
    	int measureNum = 1;
    	int note32Num = 0;
    	long tick = anacrusisLength;
    	
    	// Go back to catch anacrusis
    	while (tick > 0) {
    		tick -= ticksPerNote32;
    		
    		note32Num--;
    		if (note32Num < 0) {
    			note32Num += notes32PerBar;
    			measureNum--;
    		}
    	}
    	
    	int beatNum = note32Num / (notes32PerBar / beatsPerBar);
    	int subBeatNum = (note32Num / (notes32PerBar / beatsPerBar / subBeatsPerBeat)) % subBeatsPerBeat;
    	int tatumNum = note32Num % (notes32PerBar / beatsPerBar / subBeatsPerBeat);
    	
    	if (tatumNum == 0) {
    		beats.add(new Beat(measureNum, beatNum, subBeatNum, tatumNum, getTimeAtTick(tick), tick));
    	}
    	
    	tick += ticksPerNote32;
    	note32Num++;
    	if (note32Num >= notes32PerBar) {
    		measureNum++;
    		note32Num -= notes32PerBar;
    	}
    	
    	ListIterator<TimeTrackerNode> iterator = nodes.listIterator();
    	TimeTrackerNode node = iterator.next();
    	
    	while (iterator.hasNext() && node.getStartTick() <= lastTick) {
    		TimeTrackerNode next = iterator.next();
    		
        	notes32PerBar = node.getTimeSignature().getNotes32PerBar();
    		
    		while (tick <= lastTick && tick < next.getStartTick()) {
    			beatNum = note32Num / (notes32PerBar / beatsPerBar);
    	    	subBeatNum = (note32Num / (notes32PerBar / beatsPerBar / subBeatsPerBeat)) % subBeatsPerBeat;
    	    	tatumNum = note32Num % (notes32PerBar / beatsPerBar / subBeatsPerBeat);
    	    	
    	    	if (tatumNum == 0) {
    	    		beats.add(new Beat(measureNum, beatNum, subBeatNum, tatumNum, getTimeAtTick(tick), tick));
    	    	}
    			
    			tick += ticksPerNote32;
    	    	note32Num++;
    	    	if (note32Num >= notes32PerBar) {
    	    		measureNum++;
    	    		note32Num -= notes32PerBar;
    	    	}
    		}
    		
    		node = next;
    	}
    	
    	// Add remainder beats from the last node
    	if (node.getStartTick() <= lastTick) {
        	notes32PerBar = node.getTimeSignature().getNotes32PerBar();
        	
    		while (tick <= lastTick) {
    			beatNum = note32Num / (notes32PerBar / beatsPerBar);
    	    	subBeatNum = (note32Num / (notes32PerBar / beatsPerBar / subBeatsPerBeat)) % subBeatsPerBeat;
    	    	tatumNum = note32Num % (notes32PerBar / beatsPerBar / subBeatsPerBeat);
    	    	
    	    	if (tatumNum == 0) {
    	    		beats.add(new Beat(measureNum, beatNum, subBeatNum, tatumNum, getTimeAtTick(tick), tick));
    	    	}
    			
    			tick += ticksPerNote32;
    	    	note32Num++;
    	    	if (note32Num >= notes32PerBar) {
    	    		measureNum++;
    	    		note32Num -= notes32PerBar;
    	    	}
    		}
    	}
    	
    	return beats;
    }
    
    public List<Beat> getBeatsOnly() {
    	return new ArrayList<Beat>();
    }
    
    /**
     * Get the first non-dummy time signature in this song.
     * 
     * @return The TimeSignature of the first node which isn't a dummy, or the initial
     * dummy TimeSignature if there is none.
     */
    public TimeSignature getFirstTimeSignature() {
    	return getNodeAtTime(firstNoteTime).getTimeSignature();
    }
    
    /**
     * Set the onset time of the first note in this piece.
     * 
     * @param onsetTime
     */
    public void setFirstNoteTime(long onsetTime) {
		firstNoteTime = onsetTime;
	}
    
    /**
     * Get the anacrusis length of this TimeTracker, in sub beats.
     * 
     * @return {@link #anacrusisLength}, in sub beats
     */
    public int getAnacrusisSubBeats() {
    	TimeSignature timeSig = getFirstTimeSignature();
    	
    	int ticksPerNote32 = (int) (PPQ / 8);
    	int notes32PerBar = timeSig.getNotes32PerBar();
    	int ticksPerBar = ticksPerNote32 * notes32PerBar;
    	
    	int subBeatsPerBar = timeSig.getMeasure().getBeatsPerBar() * timeSig.getMeasure().getSubBeatsPerBeat();
    	int ticksPerSubBeat = ticksPerBar / subBeatsPerBar;
    	
		return anacrusisLength / ticksPerSubBeat;
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
     * Get a list of the {@link TimeTrackerNode}s tracked by this object.
     * 
     * @return {@link #nodes}
     */
    public LinkedList<TimeTrackerNode> getNodes() {
    	return nodes;
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
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("[");
		
		ListIterator<TimeTrackerNode> iterator = nodes.listIterator();
		
		while (iterator.hasNext()) {
			sb.append(iterator.next().toString()).append(',');
		}
		
		sb.deleteCharAt(sb.length() - 1);
		sb.append(']');
		return sb.toString();
	}
}
