package metalign.hierarchy.lpcfg;

/**
 * A <code>MetricalLpcfgElementNotFOundException</code> is thrown any time
 * the {@link MetricalLpcfg#extractTree(MetricalLpcfgTree)} method is called
 * but the given tree is not found in the grammar. It will be thrown with
 * whatever MetricalLpcfg object caused the Exception.
 * 
 * @author Andrew McLeod - 6 February, 2017
 */
public class MetricalLpcfgElementNotFoundException extends Exception {
	/**
	 * The serial ID.
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * The tree which caused the Exception.
	 */
	private final MetricalLpcfgTree tree;
	
	/**
	 * The Node which caused the Exception.
	 */
	private final MetricalLpcfgNode node;
	
	/**
	 * Create a new Exception based on the given Tree.
	 * 
	 * @param tree {@link #tree}
	 */
	public MetricalLpcfgElementNotFoundException(MetricalLpcfgTree tree) {
		this.tree = tree;
		node = null;
	}
	
	/**
	 * Create a new Exception based on the given Tree.
	 * 
	 * @param node {@link #node}
	 */
	public MetricalLpcfgElementNotFoundException(MetricalLpcfgNode node) {
		tree = null;
		this.node = node;
	}
	
	@Override
	public String getLocalizedMessage() {
		StringBuilder sb = new StringBuilder("The following ");
		sb.append(tree == null ? "node" : "tree");
		sb.append(" was not found in the grammar to be extracted:\n");
		
		sb.append(tree == null ? (node == null ? "ERROR" : node.toStringPretty(0, " ")) : tree.toStringPretty(" "));
		
		return sb.toString();
	}
}
