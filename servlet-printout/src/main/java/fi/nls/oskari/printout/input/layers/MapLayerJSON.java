package fi.nls.oskari.printout.input.layers;

import java.util.HashMap;
import java.util.Map;

import com.vividsolutions.jts.io.WKTReader;

/**
 * This cclass
 * 
 */
public class MapLayerJSON {

	Map<String, LayerDefinition> layerDefs;

	final WKTReader wktReader = new WKTReader();

	public MapLayerJSON() {
		layerDefs = new HashMap<String, LayerDefinition>();
	}

	public MapLayerJSON(Map<String, LayerDefinition> layerDefs) {
		this.layerDefs = layerDefs;
	}

	public Map<String, LayerDefinition> getLayerDefs() {
		return layerDefs;
	}

}
