package metalign.parsing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import metalign.beat.Beat;

public class XMLParser {
	
	/**
	 * The document to read events from.
	 */
	private final Document deviationXml;
	
	/**
	 * The document for musicXML events.
	 */
	private final Document musicXml;
	
	/**
	 * The current tempo, measured in beats per minute.
	 */
	private double tempo;
	
	/**
	 * The tempo deviation of the current beat. Multiply the tempo-predicted length by this value for
	 * the live length.
	 */
	private double tempoDeviation;
	
	/**
	 * The predicted time of the next beat.
	 */
	private double nextBeatTime;
	
	/**
	 * A List of the times of the beats in this piece.
	 */
	private final List<Double> beatTimes;
	
	/**
	 * A List of the beats found in this piece.
	 */
	private final List<Beat> beats;
	
	/**
	 * The number of beats per bar in each measure.
	 */
	private final List<Integer> beatsPerBar;
	
	/**
	 * The numerators of the time signature, by bar.
	 */
	private final List<Integer> numerators;
	
	/**
	 * The number of the first measure.
	 */
	private int firstMeasure;
	
	/**
	 * The note deviations, as a factor of the current tempo (quarter note length) at that note.
	 */
	private final List<Double> noteDeviations;
	
	/**
	 * Create a new XML parser from the given xml file.
	 * 
	 * @param deviationXmlFile
	 * @param musicXmlFile
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 */
	public XMLParser(File deviationXmlFile, File musicXmlFile) throws ParserConfigurationException, SAXException, IOException {
		tempo = 0.0;
		tempoDeviation = 1.0;
		nextBeatTime = 0.0;
		firstMeasure = Integer.MAX_VALUE;
		
		beatTimes = new ArrayList<Double>();
		beats = new ArrayList<Beat>();
		beatsPerBar = new ArrayList<Integer>();
		
		numerators = new ArrayList<Integer>();
		
		noteDeviations = new ArrayList<Double>();
		
		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		deviationXml = builder.parse(deviationXmlFile);
		
		builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		builder.setErrorHandler(null);
		Document tmpDoc;
		try {
			tmpDoc = builder.parse(musicXmlFile);
			
		} catch (SAXParseException e) {
			// Fix CrestMusePEDB XML bug
			try {
				BufferedReader br = new BufferedReader(new FileReader(musicXmlFile));
				BufferedWriter bw = new BufferedWriter(new FileWriter(new File(musicXmlFile.getAbsolutePath() + "fixed")));
				
				String line;
				int lineNum = 0;
				while ((line = br.readLine()) != null) {
					if (lineNum == 0 || lineNum > 2) {
						bw.write(line);
					}
					lineNum++;
				}
				
				br.close();
				bw.close();
				
			} catch (IOException e2) {
				throw e2;
			}
			
			tmpDoc = builder.parse(new File(musicXmlFile.getAbsolutePath() + "fixed"));
		}
		musicXml = tmpDoc;
	}

	/**
	 * Parse the xml file.
	 */
	public void run() {
		// Parse musicxml for time signatures and beats
		Element root = musicXml.getDocumentElement();
		Node part = root.getElementsByTagName("part").item(0);
		int numerator = 4;
		
		for (Node measure : getAllChildrenByName(part, "measure")) {
			List<Node> nodeList = getAllChildrenByName(measure, "attributes");
			
			if (!nodeList.isEmpty()) {
				nodeList = getAllChildrenByName(nodeList.get(0), "time");
				
				if (!nodeList.isEmpty()) {
					nodeList = getAllChildrenByName(nodeList.get(0), "beats");
					
					if (!nodeList.isEmpty()) {
						numerator = Integer.parseInt(nodeList.get(0).getFirstChild().getNodeValue());
					}
				}
			}
			
			numerators.add(numerator);
		}
		
		int multiplier = 1;
		if (numerators.get(0) < 6 || numerators.get(0) % 3 != 0) {
			multiplier *= 2;
		}
		
		// Parse deviation xml for beat times and deviations
		root = deviationXml.getDocumentElement();
		
		nextBeatTime = Double.parseDouble(root.getAttribute("init-silence")) * 1e6;
		
		Node nonPartwise = root.getElementsByTagName("non-partwise").item(0);
		
		for (Node measure : getAllChildrenByName(nonPartwise, "measure")) {
			handleMeasure(measure);
		}
		
		// Parse deviation xml for note deviations
		Node notewise = root.getElementsByTagName("notewise").item(0);
		
		for (Node noteDeviation : getAllChildrenByName(notewise, "note-deviation")) {
			for (Node attack : getAllChildrenByName(noteDeviation, "attack")) {
				noteDeviations.add((Double.parseDouble(attack.getFirstChild().getNodeValue())) * multiplier + 1.0);
			}
		}
	}

	/**
	 * Parse a measure xml node for its beats and tempo deviations.
	 * 
	 * @param measure The measure Node to parse.
	 */
	private void handleMeasure(Node measure) {
		int measureNumber = Integer.parseInt(measure.getAttributes().getNamedItem("number").getNodeValue());
		if (firstMeasure == Integer.MAX_VALUE) {
			firstMeasure = measureNumber;
		}
		
		int beatNum = -1;
		int maxBeatNum = -1;
		
		int pickupCount = 0;
		if (measureNumber == 1) {
			pickupCount = beats.size();
		}
		
		for (Node control : getAllChildrenByName(measure, "control")) {
			int beatNumTmp = ((int) Double.parseDouble(control.getAttributes().getNamedItem("beat").getNodeValue())) - 1;
			
			if (beatNumTmp != beatNum) {
				maxBeatNum = Math.max(beatNumTmp, maxBeatNum);
				
				if (!beats.isEmpty()) {
					// 60 gets tempo from per minute to per second
					// 1e6 converts to microseconds
					nextBeatTime += 1.0 / tempo * 60 / tempoDeviation * 1e6;
				}
				
				beatNum = beatNumTmp;
				beats.add(new Beat(measureNumber, beatNum, 0, 0, Math.round(nextBeatTime)));
				beatTimes.add(nextBeatTime);
				tempoDeviation = 1.0;
			}
			
			NodeList tempoNodes = control.getChildNodes();
			for (int i = 0; i < tempoNodes.getLength(); i++) {
				Node tempoNode = tempoNodes.item(i);
				
				if ("tempo".equals(tempoNode.getNodeName())) {
					tempo = Double.parseDouble(tempoNode.getFirstChild().getNodeValue());
					
				} else if ("tempo-deviation".equals(tempoNode.getNodeName())) {
					tempoDeviation = Double.parseDouble(tempoNode.getFirstChild().getNodeValue());
				}
			}
		}
		
		beatsPerBar.add(maxBeatNum + 1);
		
		if (pickupCount != 0) {
			beatsPerBar.set(0, maxBeatNum + 1);
			
			for (int i = pickupCount - 1; i >= 0; i--) {
				Beat oldBeat = beats.get(i);
				beats.set(i, new Beat(oldBeat.getBar(), maxBeatNum--, oldBeat.getSubBeat(), oldBeat.getTatum(), oldBeat.getTime()));
			}
		}
		
		// TODO: reshuffle towards sub beats in case of compound bar 
	}

	/**
	 * Get a List of all of the children of a given Node which have the given name.
	 * 
	 * @param parent The parent node.
	 * @param name The name we are looking for.
	 * @return A List of all of the children of the given parent node with the correct name.
	 */
	private List<Node> getAllChildrenByName(Node parent, String name) {
		List<Node> children = new ArrayList<Node>();
		
		NodeList childrenNodes = parent.getChildNodes();
		for (int i = 0; i < childrenNodes.getLength(); i++) {
			Node child = childrenNodes.item(i);
			
			if (name.equals(child.getNodeName())) {
				children.add(child);
			}
		}
		
		return children;
	}
	
	/**
	 * Get the number of beats per bar in every measure.
	 * 
	 * @return {@link #beatsPerBar}
	 */
	public int getBeatsPerBar(int measure) {
		return beatsPerBar.get(measure - firstMeasure);
	}
	
	/**
	 * Get the deviation of each note.
	 * 
	 * @return {@link #noteDeviations}
	 */
	public List<Double> getNoteDeviations() {
		return noteDeviations;
	}
	
	/**
	 * Get the beats from this parser.
	 * 
	 * @return {@link #beats}
	 */
	public List<Beat> getBeats() {
		return beats;
	}
	
	/**
	 * Get the list of the numerator for each measure.
	 * 
	 * @return {@link #numerators}
	 */
	public int getNumerators(int measure) {
		return numerators.get(measure - firstMeasure);
	}
}
