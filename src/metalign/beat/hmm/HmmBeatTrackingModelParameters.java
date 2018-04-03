package metalign.beat.hmm;

import metalign.utils.MathUtils;

public class HmmBeatTrackingModelParameters {
	
	/**
	 * Tempo change between bars standard deviation.
	 */
	public final double TEMPO_PERCENT_CHANGE_STD;
	
	/**
	 * The evenness standard deviation.
	 */
	public final double BEAT_SPACING_STD;
	
	/**
	 * The evenness mean.
	 */
	public final double BEAT_SPACING_MEAN;
	
	/**
	 * The evenness norm factor.
	 */
	public final double BEAT_SPACING_NORM_FACTOR;
	
	/**
	 * Difference between note and closest tatum standard deviation.
	 */
	public final double NOTE_STD;
	
	/**
	 * The strength by which the beat pulls towards notes.
	 */
	public final double MAGNETISM_BEAT;
	
	/**
	 * The strength by which the sub beat pulls towards notes.
	 */
	public final double MAGNETISM_SUB_BEAT;
	
	/**
	 * The minimum allowed tempo, measured in microseconds.
	 */
	public final double MINIMUM_TEMPO;
	
	/**
	 * The maximum allowed tempo, measured in microseconds.
	 */
	public final double MAXIMUM_TEMPO;
	
	/**
	 * The mean of the initial tempo.
	 */
	public final double INITIAL_TEMPO_MEAN;
	
	/**
	 * The standard deviation of the initial tempo.
	 */
	public final double INITIAL_TEMPO_STD;

	/**
	 * The minimum difference in tatum and tempo to be equal.
	 */
	public final double DIFF_MIN;
	
	/**
	 * Create a new beat tracking model with default parameters.
	 */
	public HmmBeatTrackingModelParameters() {
		TEMPO_PERCENT_CHANGE_STD = 0.0743;
		
		BEAT_SPACING_STD = 0.0336;
		BEAT_SPACING_MEAN = 0.0181;
		BEAT_SPACING_NORM_FACTOR = 0.5 + BEAT_SPACING_MEAN / BEAT_SPACING_STD * MathUtils.getStandardNormal(BEAT_SPACING_MEAN, BEAT_SPACING_MEAN, BEAT_SPACING_STD);
		
		NOTE_STD = 6655;
		
		MINIMUM_TEMPO = 400000; // 400 milliseconds
		MAXIMUM_TEMPO = 3000000; // 3000 milliseconds
		
		INITIAL_TEMPO_MEAN = 1088500;
		INITIAL_TEMPO_STD = 709918;
		
		MAGNETISM_BEAT = 1.0;
		MAGNETISM_SUB_BEAT = 0.5;
		
		DIFF_MIN = 1000;
	}
	
	/**
	 * Create a params object with the given values.
	 * 
	 * @param TC {@link #TEMPO_PERCENT_CHANGE_STD}
	 * @param BS {@link #BEAT_SPACING_STD}
	 * @param N {@link #NOTE_STD}
	 * @param MB {@link #MAGNETISM_BEAT}
	 * @param MSB {@link #MAGNETISM_SUB_BEAT}
	 * @param MAX {@link #MAXIMUM_TEMPO}
	 * @param MIN {@link #MINIMUM_TEMPO}
	 * @param IM {@link #INITIAL_TEMPO_MEAN}
	 * @param IS {@link #INITIAL_TEMPO_STD}
	 */
	public HmmBeatTrackingModelParameters(double TC, double BS, double BM, double N, double MB, double MSB,
			double MAX, double MIN, double IM, double IS, double TEDM) {
		TEMPO_PERCENT_CHANGE_STD = TC;
		
		BEAT_SPACING_STD = BS;
		BEAT_SPACING_MEAN = BM;
		BEAT_SPACING_NORM_FACTOR = 0.5 + BEAT_SPACING_MEAN * MathUtils.gaussianWindow(BEAT_SPACING_MEAN, BEAT_SPACING_MEAN, BEAT_SPACING_STD);
		
		NOTE_STD = N;
		
		MINIMUM_TEMPO = MIN;
		MAXIMUM_TEMPO = MAX;
		
		INITIAL_TEMPO_MEAN = IM;
		INITIAL_TEMPO_STD = IS;
		
		MAGNETISM_BEAT = MB;
		MAGNETISM_SUB_BEAT = MSB;
		
		DIFF_MIN = TEDM;
	}
}
