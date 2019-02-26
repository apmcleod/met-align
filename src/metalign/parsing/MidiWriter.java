package metalign.parsing;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.ListIterator;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import metalign.time.KeySignature;
import metalign.time.MidiTimeTracker;
import metalign.time.Tempo;
import metalign.time.TimeSignature;
import metalign.time.TimeTracker;
import metalign.time.TimeTrackerNode;
import metalign.utils.MidiNote;

/**
 * A <code>MidiWriter</code> is able to take in {@link MidiNote}s and a {@link TimeTracker},
 * and write them out to a valid Midi File.
 * <p>
 * This will write out the new file so that any anacrusis is now built into the file.
 * That is, the first measure is extended with rests so that it becomes a full measure.
 * 
 * @author Andrew McLeod - 28 July, 2015
 */
public class MidiWriter {
	/**
	 * The File we want to write to.
	 */
	private File outFile;
	
	/**
	 * The TimeTracker for this Midi data.
	 */
	private TimeTracker timeTracker;
	
	/**
	 * The Sequence containing the Midi data we are going to write out.
	 */
	private Sequence sequence;
	
	/**
	 * The length of the rests we will add to the beginning of this song, in ticks.
	 */
	private int offsetLength;
	
	/**
	 * Create a new MidiWriter to write out to the given File.
	 * 
	 * @param outFile {@link #outFile}
	 * @param tt {@link #timeTracker}
	 * 
	 * @throws InvalidMidiDataException If somehow the TimeTracker has an invalid PPQ value. 
	 */
	public MidiWriter(File outFile, TimeTracker tt) throws InvalidMidiDataException {
		this.outFile = outFile;
		timeTracker = tt;
		
		offsetLength = 0;
		if (tt.getAnacrusisTicks() != 0) {
			offsetLength = tt.getFirstTimeSignature().getNotes32PerBar() * ((int) tt.getPPQ() / 8);
			offsetLength -= tt.getAnacrusisTicks();
		}
		
		sequence = new Sequence(Sequence.PPQ, (int) timeTracker.getPPQ());
		sequence.createTrack();
		
		writeTimeTracker();
	}
	
	/**
	 * Write the proper TimeTracker events out to our {@link #sequence}.
	 * 
	 * @throws InvalidMidiDataException If the TimeTracker contained invalid Midi data. 
	 */
	private void writeTimeTracker() throws InvalidMidiDataException {
		if (!(timeTracker instanceof MidiTimeTracker)) {
			System.err.println("Warning: Time tracker data not written to MIDI file. Unsupported.");
			return;
		}
		
		LinkedList<TimeTrackerNode> nodes = ((MidiTimeTracker) timeTracker).getNodes();
		ListIterator<TimeTrackerNode> iterator = nodes.listIterator();
    	
    	TimeTrackerNode node = iterator.next();
    	long tick = node.getStartTick();
    	
    	if (tick > 0) {
    		tick += offsetLength;
    	}
    	
    	writeKeySignature(node.getKeySignature(), tick);
    	writeTimeSignature(node.getTimeSignature(), tick);
    	writeTempo(node.getTempo(), tick);
    	
    	while (iterator.hasNext()) {
    		TimeTrackerNode prev = node;
    		node = iterator.next();
    		tick = node.getStartTick();
    		
    		if (tick > 0) {
        		tick += offsetLength;
        	}
    		
    		if (!node.getKeySignature().equals(prev.getKeySignature())) {
    			writeKeySignature(node.getKeySignature(), tick);
    		}
    		
    		if (!node.getTimeSignature().equals(prev.getTimeSignature())) {
    			writeTimeSignature(node.getTimeSignature(), tick);
    		}
    		
    		if (!node.getTempo().equals(prev.getTempo())) {
    			writeTempo(node.getTempo(), tick);
    		}
    	}
	}

	/**
	 * Write the given key signature out to {@link #sequence} at the given tick.
	 * 
	 * @param keySignature The key signature to write.
	 * @param tick The tick at which to write it.
	 * @throws InvalidMidiDataException If the key signature produces invalid Midi data.
	 */
	private void writeKeySignature(KeySignature keySignature, long tick) throws InvalidMidiDataException {
		MetaMessage mm = new MetaMessage();
		
		byte[] data = {
				(byte) keySignature.getNumSharps(),
				(byte) (keySignature.isMajor() ? 0 : 1)};
		
		mm.setMessage(MidiEventParser.KEY_SIGNATURE, data, data.length);
		
		sequence.getTracks()[0].add(new MidiEvent(mm, tick));
	}

	/**
	 * Write the given time signature out to {@link #sequence} at the given tick.
	 * 
	 * @param timeSignature The time signature to write.
	 * @param tick The tick at which to write it.
	 * @throws InvalidMidiDataException If the time signature contained invalid Midi data.
	 */
	private void writeTimeSignature(TimeSignature timeSignature, long tick) throws InvalidMidiDataException {
		MetaMessage mm = new MetaMessage();
		
		int denominator = timeSignature.getDenominator();
		
		// Base 2 log calculator for whole numbers
		int i = 0;
		while (denominator != 1) {
			denominator /= 2;
			i++;
		}
		
		byte[] data = {
				(byte) timeSignature.getNumerator(),
				(byte) i,
				(byte) 24,
				(byte) 8};
		
		mm.setMessage(MidiEventParser.TIME_SIGNATURE, data, data.length);
		
		sequence.getTracks()[0].add(new MidiEvent(mm, tick));
	}
	
	/**
	 * Write the given tempo out to {@link #sequence} at the given tick.
	 * 
	 * @param tempo The tempo to write.
	 * @param tick The tick at which to write it.
	 * 
	 * @throws InvalidMidiDataException If the tempo contained invalid Midi data.
	 */
	private void writeTempo(Tempo tempo, long tick) throws InvalidMidiDataException {
		MetaMessage mm = new MetaMessage();
		
		int mspq = tempo.getMicroSecondsPerQuarter();
		
		byte[] data = {
				(byte) ((mspq & 0xff000000) >> 24),
				(byte) ((mspq & 0x00ff0000) >> 16),
				(byte) ((mspq & 0x0000ff00) >> 8),
				(byte) (mspq & 0x000000ff)};
		
		// Clear leading 0's
		int i;
		for (i = 0; i < data.length - 1 && data[i] == 0; i++);
		if (i != 0) {
			data = Arrays.copyOfRange(data, i, data.length);
		}
		
		mm.setMessage(MidiEventParser.TEMPO, data, data.length);
		
		sequence.getTracks()[0].add(new MidiEvent(mm, tick));
	}

	/**
	 * Add the given MidiNote into the {@link #sequence}.
	 *  
	 * @param note The note to add.
	 * 
	 * @throws InvalidMidiDataException If the MidiNote contains invalid Midi data. 
	 */
	public void addMidiNote(MidiNote note) throws InvalidMidiDataException {
		int correctVoice = note.getCorrectVoice();
		long onsetTick = note.getOnsetTick() + offsetLength;
		long offsetTick = note.getOffsetTick() + offsetLength;
		
		// Pad with enough tracks
		while (sequence.getTracks().length <= correctVoice) {
			sequence.createTrack();
		}
		
		// Get the correct track
		Track track = sequence.getTracks()[correctVoice];
		
		ShortMessage noteOn = new ShortMessage();
		noteOn.setMessage(ShortMessage.NOTE_ON | correctVoice, note.getPitch(), note.getVelocity());
		MidiEvent noteOnEvent = new MidiEvent(noteOn, onsetTick);
		
		ShortMessage noteOff = new ShortMessage();
		noteOff.setMessage(ShortMessage.NOTE_OFF | correctVoice, note.getPitch(), 0);
		MidiEvent noteOffEvent = new MidiEvent(noteOff, offsetTick);
		
		track.add(noteOnEvent);
		track.add(noteOffEvent);
	}
	
	/**
	 * Actually write the data out to file.
	 * 
	 * @throws IOException If the file cannot be written to.
	 */
	public void write() throws IOException {
		MidiSystem.write(sequence, 1, outFile);
	}
}
