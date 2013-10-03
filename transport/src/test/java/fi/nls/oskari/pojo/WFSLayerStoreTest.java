package fi.nls.oskari.pojo;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;

public class WFSLayerStoreTest {

	String json = "{\"layerId\":216,\"nameLocales\":{\"fi\":{\"name\":\"Palvelupisteiden kyselypalvelu\",\"subtitle\":\"\"},\"sv\":{\"name\":\"Söktjänst för serviceställen\",\"subtitle\":\"\"},\"en\":{\"name\":\"Public services query service\",\"subtitle\":\"\"}},\"username\":\"\",\"password\":\"\",\"maxFeatures\":100,\"featureNamespace\":\"pkartta\",\"featureNamespaceURI\":\"www.pkartta.fi\",\"featureElement\":\"toimipaikat\",\"featureType\":{},\"selectedFeatureParams\":{\"default\": [\"nimi\", \"osoite\"]},\"featureParamsLocales\":{\"fi\": [\"nimi\", \"osoite\"]},\"geometryType\":\"2d\",\"getMapTiles\":true,\"getFeatureInfo\":true,\"tileRequest\":false,\"minScale\":50000.0,\"maxScale\":1.0,\"templateName\":null,\"templateDescription\":null,\"templateType\":null,\"requestTemplate\":null,\"responseTemplate\":null,\"selectionSLDStyle\":null,\"styles\":{\"default\":{\"id\":\"1\",\"name\":\"default\",\"SLDStyle\":\"<?xml version=\\\"1.0\\\" encoding=\\\"ISO-8859-1\\\"?><StyledLayerDescriptor version=\\\"1.0.0\\\" xmlns=\\\"http://www.opengis.net/sld\\\" xmlns:ogc=\\\"http://www.opengis.net/ogc\\\" xmlns:xlink=\\\"http://www.w3.org/1999/xlink\\\" xmlns:xsi=\\\"http://www.w3.org/2001/XMLSchema-instance\\\" xsi:schemaLocation=\\\"http://www.opengis.net/sld http://schemas.opengis.net/sld/1.0.0/StyledLayerDescriptor.xsd\\\"><NamedLayer><Name>Palvelupisteet</Name><UserStyle><Title>Palvelupisteiden tyyli</Title><Abstract/><FeatureTypeStyle><Rule><Title>Piste</Title><PointSymbolizer><Graphic><Mark><WellKnownName>circle</WellKnownName><Fill><CssParameter name=\\\"fill\\\">#FFFFFF</CssParameter></Fill><Stroke><CssParameter name=\\\"stroke\\\">#000000</CssParameter><CssParameter name=\\\"stroke-width\\\">2</CssParameter></Stroke></Mark><Size>12</Size></Graphic></PointSymbolizer></Rule></FeatureTypeStyle></UserStyle></NamedLayer></StyledLayerDescriptor>\"}},\"URL\":\"http://kartta.suomi.fi/geoserver/wfs\",\"GMLGeometryProperty\":\"the_geom\",\"SRSName\":\"EPSG:3067\",\"GMLVersion\":\"3.1.1\",\"WFSVersion\":\"1.1.0\",\"WMSLayerId\":null}";
	String jsonFail = "{\"layerId:216,\"nameLocales\":{\"fi\":{\"name\":\"Palvelupisteiden kyselypalvelu\",\"subtitle\":\"\"},\"sv\":{\"name\":\"Söktjänst för serviceställen\",\"subtitle\":\"\"},\"en\":{\"name\":\"Public services query service\",\"subtitle\":\"\"}},\"username\":\"\",\"password\":\"\",\"maxFeatures\":100,\"featureNamespace\":\"pkartta\",\"featureNamespaceURI\":\"www.pkartta.fi\",\"featureElement\":\"toimipaikat\",\"featureType\":\"\",\"selectedFeatureParams\":[],\"featureParamsLocales\":{},\"geometryType\":\"2d\",\"getMapTiles\":true,\"getFeatureInfo\":true,\"tileRequest\":false,\"minScale\":50000.0,\"maxScale\":1.0,\"templateName\":null,\"templateDescription\":null,\"templateType\":null,\"requestTemplate\":null,\"responseTemplate\":null,\"selectionSLDStyle\":null,\"styles\":{\"default\":{\"id\":\"1\",\"name\":\"default\",\"SLDStyle\":\"<?xml version=\\\"1.0\\\" encoding=\\\"ISO-8859-1\\\"?><StyledLayerDescriptor version=\\\"1.0.0\\\" xmlns=\\\"http://www.opengis.net/sld\\\" xmlns:ogc=\\\"http://www.opengis.net/ogc\\\" xmlns:xlink=\\\"http://www.w3.org/1999/xlink\\\" xmlns:xsi=\\\"http://www.w3.org/2001/XMLSchema-instance\\\" xsi:schemaLocation=\\\"http://www.opengis.net/sld http://schemas.opengis.net/sld/1.0.0/StyledLayerDescriptor.xsd\\\"><NamedLayer><Name>Palvelupisteet</Name><UserStyle><Title>Palvelupisteiden tyyli</Title><Abstract/><FeatureTypeStyle><Rule><Title>Piste</Title><PointSymbolizer><Graphic><Mark><WellKnownName>circle</WellKnownName><Fill><CssParameter name=\\\"fill\\\">#FFFFFF</CssParameter></Fill><Stroke><CssParameter name=\\\"stroke\\\">#000000</CssParameter><CssParameter name=\\\"stroke-width\\\">2</CssParameter></Stroke></Mark><Size>12</Size></Graphic></PointSymbolizer></Rule></FeatureTypeStyle></UserStyle></NamedLayer></StyledLayerDescriptor>\"}},\"URL\":\"http://kartta.suomi.fi/geoserver/wfs\",\"GMLGeometryProperty\":\"the_geom\",\"SRSName\":\"EPSG:3067\",\"GMLVersion\":\"3.1.1\",\"WFSVersion\":\"1.1.0\",\"WMSLayerId\":null}";

	@Test
	public void testJSON() throws IOException {
		WFSLayerStore store = WFSLayerStore.setJSON(json);
		String jsonResult = store.getAsJSON();
		
		// jsonResult and json are not the same because of null values - testing with 2. json result
		WFSLayerStore store2 = WFSLayerStore.setJSON(jsonResult);
        assertTrue("should get  'nimi' as 1.", store2.getSelectedFeatureParams("default").get(0).equals("nimi"));
        assertTrue("should get  'osoite' as 2.", store2.getSelectedFeatureParams("default").get(1).equals("osoite"));

        // returns default cause key was not found
        assertTrue("should get  'nimi' as 1.", store2.getSelectedFeatureParams("lol").get(0).equals("nimi"));
        assertTrue("should get  'osoite' as 2.", store2.getSelectedFeatureParams("lol").get(1).equals("osoite"));

        assertTrue("should get  'nimi' as 1.", store2.getFeatureParamsLocales("fi").get(0).equals("nimi"));
        assertTrue("should get  'osoite' as 2.", store2.getFeatureParamsLocales("fi").get(1).equals("osoite"));
		String jsonResult2 = store2.getAsJSON();
		assertTrue("should get same json", (jsonResult2.equals(jsonResult)));
	}
	
	@Test(expected=IOException.class)
	public void testJSONIOException() throws IOException {
		WFSLayerStore.setJSON(jsonFail);
	}
	
}