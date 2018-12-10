package metalign.hierarchy.lpcfg;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.sound.midi.InvalidMidiDataException;

import metalign.Main;
import metalign.Runner;
import metalign.beat.fromfile.FromFileBeatTrackingModelState;
import metalign.hierarchy.Measure;
import metalign.hierarchy.fromfile.FromFileHierarchyModelState;
import metalign.hierarchy.lpcfg.MetricalLpcfgNonterminal.MetricalLpcfgLevel;
import metalign.joint.JointModel;
import metalign.parsing.EventParser;
import metalign.parsing.NoteListGenerator;
import metalign.time.TimeTracker;
import metalign.voice.fromfile.FromFileVoiceSplittingModelState;

/**
 * A <code>MetricalLpcfg</code> keeps track of the lexicalized pcfg we've generated with
 * a {@link MetricalLpcfgGenerator}.
 * 
 * @author Andrew McLeod - 29 February, 2016
 */
public class MetricalLpcfg implements Serializable {
	/**
	 * Version 1 Serializable
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * A List of the trees contained in this lpcfg.
	 */
	private final List<MetricalLpcfgTree> trees;
	
	/**
	 * The probability tracker for this grammar.
	 */
	private final MetricalLpcfgProbabilityTracker probabilities;
	
	/**
	 * Create a new empty grammar.
	 */
	public MetricalLpcfg() {
		trees = new ArrayList<MetricalLpcfgTree>();
		probabilities = new MetricalLpcfgProbabilityTracker();
	}
	
	/**
	 * Create a new grammar, used by the {@link #deepCopy()} and {@link #shallowCopy()} methods.
	 * 
	 * @param lpcfg The old grammar this one is to be a copy of.
	 * @param deep True if this is to be a deep copy. False otherwise.
	 */
	private MetricalLpcfg(MetricalLpcfg lpcfg) {
		trees = new ArrayList<MetricalLpcfgTree>(lpcfg.trees);
		
		probabilities = lpcfg.probabilities.deepCopy();
	}
	
	/**
	 * Get the log probability that the given tree would occur in this grammar.
	 * 
	 * @param tree The tree whose log probability we want.
	 * @return The log probability of the given tree in our grammar.
	 */
	public double getTreeLogProbability(MetricalLpcfgTree tree) {
		return getNodeLogProbability(tree.getMeasure(), tree.getMeasure().getHead(), tree.getMeasure().getMeasure());
	}
	
	/**
	 * Get the log probability of the given Node.
	 * 
	 * @param node The node whose log probability we want.
	 * @param parentHead The head of the parent of this node.
	 * @param measure The measure of the tree.
	 * @return The log probability of the given node.
	 */
	private double getNodeLogProbability(MetricalLpcfgNode node, MetricalLpcfgHead parentHead, Measure measure) {
		double logProbability = 0.0;
		
		if (node instanceof MetricalLpcfgNonterminal) {
			MetricalLpcfgNonterminal nonterminal = (MetricalLpcfgNonterminal) node;
			
			List<MetricalLpcfgNode> children = nonterminal.getChildren();
			String transitionString = nonterminal.getTransitionString();
			String typeString = nonterminal.getTypeString();
			MetricalLpcfgHead head = nonterminal.getHead();
			MetricalLpcfgLevel level = nonterminal.getLevel();

			// p(nonterminal -> children | nonterminal, head(nonterminal))
			logProbability += probabilities.getTransitionProbability(measure, typeString, head, transitionString, level);
			
			// p(head(nonterminal) | nonterminal, parentHeadLength)
			// Only need for WEAK, all others equal to their parent head length always
			if (!(nonterminal instanceof MetricalLpcfgMeasure)) {
				logProbability += probabilities.getHeadProbability(measure, typeString, parentHead, head, level);
				
			} else {
				// p(head(measure) | measure)
				logProbability += probabilities.getMeasureHeadProbability(measure, head);
			}
			
			// Recursive probability of children nodes
			for (MetricalLpcfgNode child : children) {
				logProbability += getNodeLogProbability(child, head, measure);
			}
		}
		
		return logProbability;
	}

	/**
	 * Extract the given tree from our list of trees.
	 * 
	 * @param toExtract The tree we want to remove from this grammar.
	 * @throws MetricalLpcfgElementNotFoundException If the given tree is not found
	 * in the grammar.
	 */
	public void extractTree(MetricalLpcfgTree toExtract) throws MetricalLpcfgElementNotFoundException {
		Iterator<MetricalLpcfgTree> treeIterator = trees.iterator();
		while (treeIterator.hasNext()) {
			if (treeIterator.next().equals(toExtract)) {
				// Tree found
				treeIterator.remove();
				
				try {
					updateCounts(toExtract.getMeasure(), toExtract.getMeasure().getHead(), toExtract.getMeasure().getMeasure(), false);
				} catch (MetricalLpcfgElementNotFoundException e) {
					System.err.println(e);
					throw new MetricalLpcfgElementNotFoundException(toExtract);
				}
				
				return;
			}
		}
		
		throw new MetricalLpcfgElementNotFoundException(toExtract);
	}

	/**
	 * Add a tree to our list of trees ({@link #trees}).
	 * 
	 * @param tree A new tree we want to add to this grammar.
	 */
	public void addTree(MetricalLpcfgTree tree) {
		if (MetricalLpcfgGeneratorRunner.SAVE_TREES) {
			trees.add(tree);
		}
		
		try {
			updateCounts(tree.getMeasure(), tree.getMeasure().getHead(), tree.getMeasure().getMeasure(), true);
		} catch (MetricalLpcfgElementNotFoundException e) {
			System.err.println("Element not found Exception on add? This should never happen:");
			System.err.println(e.getLocalizedMessage());
		}
	}
	
	/**
	 * Update the counts maps recursively for each node under the given one.
	 * 
	 * @param node The node whose counts we want to update.
	 * @param parentHead The head of the parent of this node.
	 * @param measure The measure of the tree.
	 * @param adding True if we are adding to the counts, false if we're removing.
	 * @throws MetricalLpcfgElementNotFoundException If some
	 */
	private void updateCounts(MetricalLpcfgNode node, MetricalLpcfgHead parentHead, Measure measure, boolean adding) throws MetricalLpcfgElementNotFoundException {
		if (node instanceof MetricalLpcfgNonterminal) {
			MetricalLpcfgNonterminal nonterminal = (MetricalLpcfgNonterminal) node;
			
			List<MetricalLpcfgNode> children = nonterminal.getChildren();
			String transitionString = nonterminal.getTransitionString();
			String typeString = nonterminal.getTypeString();
			MetricalLpcfgHead head = nonterminal.getHead();
			MetricalLpcfgLevel level = nonterminal.getLevel();

			// p(nonterminal -> children | nonterminal, head(nonterminal))
			if (adding) {
				probabilities.addTransition(measure, typeString, head, transitionString, level);
			} else {
				try {
					probabilities.removeTransition(measure, typeString, head, transitionString, level);
				} catch (MetricalLpcfgElementNotFoundException e) {
					throw new MetricalLpcfgElementNotFoundException(node);
				}
			}
			
			// p(head(nonterminal) | nonterminal, parentHeadLength)
			// Only need for non measures
			if (!(nonterminal instanceof MetricalLpcfgMeasure)) {
				if (adding) {
					probabilities.addHead(measure, typeString, parentHead, head, level);
				} else {
					try {
						probabilities.removeHead(measure, typeString, parentHead, head, level);
					} catch (MetricalLpcfgElementNotFoundException e) {
						throw new MetricalLpcfgElementNotFoundException(node);
					}
				}
				
			} else {
				// p(head(measure) | measure)
				if (adding) {
					probabilities.addMeasureHead(measure, head);
				} else {
					try {
						probabilities.removeMeasureHead(measure, head);
					} catch (MetricalLpcfgElementNotFoundException e) {
						throw new MetricalLpcfgElementNotFoundException(node);
					}
				}
			}
			
			// Recursive probability of children nodes
			for (MetricalLpcfgNode child : children) {
				updateCounts(child, head, measure, adding);
			}
		}
	}
	/**
	 * Get a Set of the Measures which are contained in any tree from within this lpcfg.
	 * 
	 * @return The Set of the Measures which are in this grammar.
	 */
	public Set<Measure> getMeasures() {
		return probabilities.getMeasures();
	}
	
	/**
	 * Get the trees contained in this grammar.
	 * 
	 * @return {@link #trees}
	 */
	public List<MetricalLpcfgTree> getTrees() {
		return trees;
	}
	
	/**
	 * Get the probability tracker of this grammar.
	 * 
	 * @return {@link #probabilities}
	 */
	public MetricalLpcfgProbabilityTracker getProbabilityTracker() {
		return probabilities;
	}
	
	/**
	 * Merge the given other grammar into this one.
	 * 
	 * @param other The grammar to merge into this one.
	 */
	public void mergeGrammar(MetricalLpcfg other) {
		// Don't use addTree() here because that also updates probabilities.
		// We do it separately in case the other grammar was built with -x (i.e. has no trees).
		for (MetricalLpcfgTree tree : other.getTrees()) {
			trees.add(tree);
		}
		
		probabilities.merge(other.getProbabilityTracker());
	}
	
	/**
	 * Get a shallow copy of this grammar.
	 * 
	 * @return A shallow copy of this grammar.
	 */
	public MetricalLpcfg shallowCopy() {
		return new MetricalLpcfg(this);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		for (MetricalLpcfgTree tree : trees) {
			sb.append(tree).append('\n');
		}
		
		if (trees.size() > 0) {
			sb.deleteCharAt(sb.length() - 1);
		}
		
		return sb.toString();
	}

	/**
	 * Get the pretty, recursive version of this grammar with the given tab String.
	 * 
	 * @param tab The tab String to use for indenting tree levels.
	 * @return The pretty recursive version of this grammar.
	 */
	public String toStringPretty(String tab) {
		StringBuilder sb = new StringBuilder();
		
		for (MetricalLpcfgTree tree : trees) {
			sb.append(tree.toStringPretty(tab)).append('\n');
		}
		
		if (trees.size() > 0) {
			sb.deleteCharAt(sb.length() - 1);
		}
		
		return sb.toString();
	}
	
	/**
	 * Extract the grammar from the given file from this grammar.
	 * 
	 * @param file The file to extract.
	 * @throws InterruptedException 
	 * @throws InvalidMidiDataException 
	 * @throws IOException 
	 * @throws MetricalLpcfgElementNotFoundException 
	 */
	public void extract(File file, List<File> anacrusisFiles, boolean useChannel) throws IOException, InvalidMidiDataException, InterruptedException, MetricalLpcfgElementNotFoundException {
		TimeTracker tt = new TimeTracker(Main.SUB_BEAT_LENGTH);
		tt.setAnacrusis(MetricalLpcfgGeneratorRunner.getAnacrusisLength(file, anacrusisFiles));
		NoteListGenerator nlg = new NoteListGenerator(tt);
		
		// PARSE!
		EventParser ep = Runner.parseFile(file, nlg, tt, useChannel);
		
		// RUN!
		JointModel jm = new JointModel(
				new FromFileVoiceSplittingModelState(ep),
				new FromFileBeatTrackingModelState(tt),
				new FromFileHierarchyModelState(tt));
		
		Runner.performInference(jm, nlg);
		
		// GRAMMARIZE
		MetricalLpcfgGenerator generator = new MetricalLpcfgGenerator();
		generator.parseSong(jm, tt);
		
		for (MetricalLpcfgTree tree : generator.getGrammar().trees) {
			extractTree(tree);
		}
	}

	/**
	 * Add the grammar from the given file back into this grammar.
	 * 
	 * @param file The file to add back into the grammar.
	 * @throws InterruptedException 
	 * @throws InvalidMidiDataException 
	 * @throws IOException 
	 */
	public void unextract(File file, List<File> anacrusisFiles, boolean useChannel) throws IOException, InvalidMidiDataException, InterruptedException {
		TimeTracker tt = new TimeTracker();
		tt.setAnacrusis(MetricalLpcfgGeneratorRunner.getAnacrusisLength(file, anacrusisFiles));
		NoteListGenerator nlg = new NoteListGenerator(tt);
		
		// PARSE!
		EventParser ep = Runner.parseFile(file, nlg, tt, useChannel);
		
		// RUN!
		JointModel jm = new JointModel(
				new FromFileVoiceSplittingModelState(ep),
				new FromFileBeatTrackingModelState(tt),
				new FromFileHierarchyModelState(tt));
		
		Runner.performInference(jm, nlg);
		
		// GRAMMARIZE
		MetricalLpcfgGenerator generator = new MetricalLpcfgGenerator();
		generator.parseSong(jm, tt);
		
		for (MetricalLpcfgTree tree : generator.getGrammar().trees) {
			addTree(tree);
		}
	}
	
	/**
	 * Serialize the given grammar and write it out to the given file.
	 * 
	 * @param grammar The grammar we want to serialize.
	 * @param file The file to write out the grammar to.
	 * 
	 * @throws FileNotFoundException The file could not be opened for writing.
	 * @throws IOException Some IO error occurred.
	 * 
	 * @see #deserialize(File)
	 */
	public static void serialize(MetricalLpcfg grammar, File file) throws FileNotFoundException, IOException {
		grammar.probabilities.smooth();
		ObjectOutputStream streamOutput = new ObjectOutputStream(
				new GZIPOutputStream(
						new FileOutputStream(file)));
		streamOutput.writeObject(grammar);
		streamOutput.flush();
		streamOutput.close();
	}
	
	/**
	 * Load a grammar from a serialized file.
	 * 
	 * @param serialized The file containing a serialized grammar.
	 * @return The grammar loaded from the given file.
	 * 
	 * @throws ClassNotFoundException The class could not be deserialized properly.
	 * @throws IOException Some IO error occurred.
	 * 
	 * @see #serialize(MetricalLpcfg, File)
	 */
	public static MetricalLpcfg deserialize(File serialized) throws ClassNotFoundException, IOException {
		ObjectInputStream streamInput = new ObjectInputStream(
				new GZIPInputStream(
						new FileInputStream(serialized)));
		MetricalLpcfg grammar = (MetricalLpcfg) streamInput.readObject();
		streamInput.close();
		return grammar;
	}
}
