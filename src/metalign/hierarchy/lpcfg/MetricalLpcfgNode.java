package metalign.hierarchy.lpcfg;

import java.util.List;

/**
 * A <code>MetricalLpcfgNode</code> represents a node in the tree of the metrical lpcfg.
 * Each node can be either a terminal ({@link MetricalLpcfgTerminal}) or a non-terminal
 * ({@link MetricalLpcfgNonterminal}).
 * 
 * @author Andrew McLeod - 25 April, 2016
 */
public interface MetricalLpcfgNode {
	/**
	 * Get the quantum List of this lpcfg node.
	 * 
	 * @return The quantum List of this node.
	 */
	public List<MetricalLpcfgQuantum> getQuantum();
	
	/**
	 * Get the head of this lpcfg node. This implements the lexicalization of the lpcfg.
	 * 
	 * @return The head of this lpcfg node, relative to the sub beat length,
	 * where the sub beat length is 1.
	 */
	public MetricalLpcfgHead getHead();
	
	/**
	 * Get the length of this node, measured in sub beats.
	 * 
	 * @return The length of this node in sub beats.
	 */
	public int getLength();
	
	/**
	 * Get if this node is empty or not.
	 * 
	 * @return True if this node contains no notes. False otherwise.
	 */
	public boolean isEmpty();
	
	/**
	 * Get if this node starts with a REST or not.
	 * 
	 * @return True if this node starts with a REST. False otherwise.
	 */
	public boolean startsWithRest();
	
	/**
	 * Get a deep copy of this node. Each node needs this because we sometimes need a deep
	 * copy of the entire tree.
	 * 
	 * @return A deep copy of this node.
	 */
	public MetricalLpcfgNode deepCopy();
	
	/**
	 * Print the given node recursively in a pretty format.
	 * 
	 * @param depth The depth of this node.
	 * @param tab The tab String to indent tree levels.
	 * @return This node in a pretty recursive format.
	 */
	public String toStringPretty(int depth, String tab);
}
