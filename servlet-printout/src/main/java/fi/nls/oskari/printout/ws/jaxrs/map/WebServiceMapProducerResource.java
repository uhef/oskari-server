package fi.nls.oskari.printout.ws.jaxrs.map;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.Map;
import java.util.Properties;

import javax.ws.rs.core.StreamingOutput;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;

import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.filter.request.RequestFilterException;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

import fi.nls.oskari.printout.config.ConfigValue;
import fi.nls.oskari.printout.input.geojson.MaplinkGeoJsonParser;
import fi.nls.oskari.printout.input.layers.MapLayerJSONParser;
import fi.nls.oskari.printout.input.maplink.MapLink;
import fi.nls.oskari.printout.input.maplink.MapLinkParser;
import fi.nls.oskari.printout.output.map.MapProducer;
import fi.nls.oskari.printout.output.map.MapProducerResource;
import fi.nls.oskari.printout.output.map.MetricScaleResolutionUtils;
import fi.nls.oskari.printout.printing.PDFProducer;
import fi.nls.oskari.printout.printing.PDFProducer.Options;
import fi.nls.oskari.printout.printing.PDFProducer.Page;
import fi.nls.oskari.printout.ws.jaxrs.format.StreamingDOCXImpl;
import fi.nls.oskari.printout.ws.jaxrs.format.StreamingJSONImpl;
import fi.nls.oskari.printout.ws.jaxrs.format.StreamingPDFImpl;
import fi.nls.oskari.printout.ws.jaxrs.format.StreamingPNGImpl;
import fi.nls.oskari.printout.ws.jaxrs.format.StreamingPPTXImpl;

/*
 * 
 * This class is used in JAX-RS Resource class to implement map imaging .
 *
 * JAX-RS shares and instance of this class for any requests and this is assumed to be
 * threadsafe.
 * 
 * This class is also used in tests.
 * 
 */
public class WebServiceMapProducerResource extends MapProducerResource {

	public WebServiceMapProducerResource(Properties props)
			throws NoSuchAuthorityCodeException, IOException,
			GeoWebCacheException, FactoryException {
		super(props);

	}

	void addGeometryType(SimpleFeatureTypeBuilder typeBuilder, Geometry geometry) {
		typeBuilder.add("geometry", geometry != null ? geometry.getClass()
				: Geometry.class);
		typeBuilder.setDefaultGeometry("geometry");
	}

	SimpleFeatureBuilder createBuilder(CoordinateReferenceSystem crs,
			Geometry geometry, Integer targetWidth, Integer targetHeight,
			String featureName) {
		SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
		typeBuilder.setName("feature");
		typeBuilder.setNamespaceURI("http://geotools.org");
		typeBuilder.setCRS(crs);

		if (geometry != null) {
			addGeometryType(typeBuilder, geometry);
		}

		typeBuilder.add("targetWidth",
				targetWidth != null ? targetWidth.getClass() : Object.class);
		typeBuilder.add("targetHeight",
				targetHeight != null ? targetHeight.getClass() : Object.class);
		typeBuilder.add("featureName",
				featureName != null ? featureName.getClass() : Object.class);

		SimpleFeatureBuilder builder = new SimpleFeatureBuilder(
				typeBuilder.buildFeatureType());
		builder.set("geometry", geometry);
		builder.set("targetWidth", targetWidth);
		builder.set("targetHeight", targetHeight);
		builder.set("featureName", featureName);

		return builder;
	}

	/**
	 * gets map using parameters from JSON spec
	 * 
	 * @param inp
	 * @param xClientInfo
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 * @throws GeoWebCacheException
	 * @throws XMLStreamException
	 * @throws FactoryConfigurationError
	 * @throws RequestFilterException
	 * @throws TransformException
	 * @throws InterruptedException
	 * @throws NoSuchAuthorityCodeException
	 * @throws FactoryException
	 * @throws com.vividsolutions.jts.io.ParseException
	 * @throws org.json.simple.parser.ParseException
	 * @throws URISyntaxException
	 */
	public StreamingDOCXImpl getGeoJsonMapDOCX(InputStream inp,
			String xClientInfo) throws IOException, ParseException,
			GeoWebCacheException, XMLStreamException,
			FactoryConfigurationError, RequestFilterException,
			TransformException, InterruptedException,
			NoSuchAuthorityCodeException, FactoryException,
			com.vividsolutions.jts.io.ParseException,
			org.json.simple.parser.ParseException, URISyntaxException {

		MapProducer producer = fork(xClientInfo);

		TileLayer tileLayer = config.getTileLayer(producer.getTemplateLayer());
		GridSubset gridSubset = tileLayer.getGridSubset(gridSubsetName);

		MaplinkGeoJsonParser parser = new MaplinkGeoJsonParser();
		boolean isDebug = ConfigValue.GEOJSON_DEBUG.getConfigProperty(props,
				"false").equals("true");

		parser.setDebug(isDebug);

		Map<String, ?> root = parser.parse(inp);

		MapLayerJSONParser mapLayerJsonParser = new MapLayerJSONParser(props);

		MapLink mapLink = mapLayerJsonParser.parseMapLinkJSON(root, getGf(),
				gridSubset.getResolutions());

		Map<String, String> values = mapLink.getValues();

		if (values.get("PAGESIZE") != null) {
			Page page = Page.valueOf(values.get("PAGESIZE"));

			values.put("WIDTH",
					Integer.toString(page.getWidthTargetInPoints(), 10));
			values.put("HEIGHT",
					Integer.toString(page.getHeightTargetInPoints(), 10));
			mapLink.setWidth(page.getWidthTargetInPoints());
			mapLink.setHeight(page.getHeightTargetInPoints());
		} else {
			mapLink.setWidth(Integer.valueOf(values.get("WIDTH"), 10));
			mapLink.setHeight(Integer.valueOf(values.get("HEIGHT"), 10));
		}

		mapLayerJsonParser.getMapLinkParser().validate(mapLink);

		StreamingDOCXImpl result = new StreamingDOCXImpl(producer, mapLink);

		result.underflow();
		return result;
	}

	/**
	 * gets map using parameters from JSON spec
	 * 
	 * @param inp
	 * @param xClientInfo
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 * @throws GeoWebCacheException
	 * @throws XMLStreamException
	 * @throws FactoryConfigurationError
	 * @throws RequestFilterException
	 * @throws TransformException
	 * @throws InterruptedException
	 * @throws NoSuchAuthorityCodeException
	 * @throws FactoryException
	 * @throws com.vividsolutions.jts.io.ParseException
	 * @throws org.json.simple.parser.ParseException
	 * @throws URISyntaxException
	 */
	public StreamingPDFImpl getGeoJsonMapPDF(InputStream inp, String xClientInfo)
			throws IOException, ParseException, GeoWebCacheException,
			XMLStreamException, FactoryConfigurationError,
			RequestFilterException, TransformException, InterruptedException,
			NoSuchAuthorityCodeException, FactoryException,
			com.vividsolutions.jts.io.ParseException,
			org.json.simple.parser.ParseException, URISyntaxException {

		MapProducer producer = fork(xClientInfo);
		TileLayer tileLayer = config.getTileLayer(producer.getTemplateLayer());
		GridSubset gridSubset = tileLayer.getGridSubset(gridSubsetName);

		MaplinkGeoJsonParser parser = new MaplinkGeoJsonParser();
		boolean isDebug = ConfigValue.GEOJSON_DEBUG.getConfigProperty(props,
				"false").equals("true");

		parser.setDebug(isDebug);

		Map<String, ?> root = parser.parse(inp);

		MapLayerJSONParser mapLayerJsonParser = new MapLayerJSONParser(props);

		MapLink mapLink = mapLayerJsonParser.parseMapLinkJSON(root, getGf(),
				gridSubset.getResolutions());

		Map<String, String> values = mapLink.getValues();

		Page page = Page.valueOf(values.get("PAGESIZE"));

		values.put("WIDTH", Integer.toString(page.getWidthTargetInPoints(), 10));
		values.put("HEIGHT",
				Integer.toString(page.getHeightTargetInPoints(), 10));
		mapLink.setWidth(page.getWidthTargetInPoints());
		mapLink.setHeight(page.getHeightTargetInPoints());

		mapLayerJsonParser.getMapLinkParser().validate(mapLink);

		PDFProducer.Options pageOptions = getPageOptions(values);

		StreamingPDFImpl result = new StreamingPDFImpl(producer, mapLink, page,
				pageOptions);

		result.underflow();
		return result;
	}

	/**
	 * gets Map PNG using parameters from JSON spec
	 * 
	 * @param inp
	 * @param xClientInfo
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 * @throws GeoWebCacheException
	 * @throws XMLStreamException
	 * @throws FactoryConfigurationError
	 * @throws RequestFilterException
	 * @throws TransformException
	 * @throws NoSuchAuthorityCodeException
	 * @throws FactoryException
	 * @throws com.vividsolutions.jts.io.ParseException
	 * @throws org.json.simple.parser.ParseException
	 * @throws URISyntaxException
	 */
	public StreamingPNGImpl getGeoJsonMapPNG(InputStream inp, String xClientInfo)
			throws IOException, ParseException, GeoWebCacheException,
			XMLStreamException, FactoryConfigurationError,
			RequestFilterException, TransformException,
			NoSuchAuthorityCodeException, FactoryException,
			com.vividsolutions.jts.io.ParseException,
			org.json.simple.parser.ParseException, URISyntaxException {

		MapProducer producer = fork(xClientInfo);
		TileLayer tileLayer = config.getTileLayer(producer.getTemplateLayer());
		GridSubset gridSubset = tileLayer.getGridSubset(gridSubsetName);

		MaplinkGeoJsonParser parser = new MaplinkGeoJsonParser();

		boolean isDebug = ConfigValue.GEOJSON_DEBUG.getConfigProperty(props,
				"false").equals("true");

		parser.setDebug(isDebug);

		Map<String, ?> root = parser.parse(inp);

		MapLayerJSONParser mapLayerJsonParser = new MapLayerJSONParser(props);

		MapLink mapLink = mapLayerJsonParser.parseMapLinkJSON(root, getGf(),
				gridSubset.getResolutions());

		Map<String, String> values = mapLink.getValues();

		if (values.get("PAGESIZE") != null) {
			Page page = Page.valueOf(values.get("PAGESIZE"));

			values.put("WIDTH",
					Integer.toString(page.getWidthTargetInPoints(), 10));
			values.put("HEIGHT",
					Integer.toString(page.getHeightTargetInPoints(), 10));
			mapLink.setWidth(page.getWidthTargetInPoints());
			mapLink.setHeight(page.getHeightTargetInPoints());

		}

		/* fixes to help UI */
		if (values.get("SCALEDWIDTH") != null
				&& values.get("SCALEDHEIGHT") == null) {

			/* calc based on WIDHT/HEIGHT */
			int targetScaledHeight = Integer.valueOf(values.get("SCALEDWIDTH"),
					10)
					* Integer.valueOf(values.get("HEIGHT"), 10)
					/ Integer.valueOf(values.get("WIDTH"), 10);

			values.put("SCALEDHEIGHT", Integer.toString(targetScaledHeight, 10));

		} else if (values.get("SCALEDWIDTH") == null
				&& values.get("SCALEDHEIGHT") != null) {

			int targetScaledWidth = Integer.valueOf(values.get("SCALEDHEIGHT"),
					10)
					* Integer.valueOf(values.get("WIDTH"), 10)
					/ Integer.valueOf(values.get("HEIGHT"), 10);

			/* calc based on WIDHT/HEIGHT */
			values.put("SCALEDHEIGHT", Integer.toString(targetScaledWidth, 10));

		}

		mapLayerJsonParser.getMapLinkParser().validate(mapLink);

		StreamingPNGImpl result = new StreamingPNGImpl(producer, mapLink);
		result.underflow();

		return result;
	}

	/**
	 * gets map using parameters from JSON spec
	 * 
	 * @param inp
	 * @param xClientInfo
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 * @throws GeoWebCacheException
	 * @throws XMLStreamException
	 * @throws FactoryConfigurationError
	 * @throws RequestFilterException
	 * @throws TransformException
	 * @throws InterruptedException
	 * @throws NoSuchAuthorityCodeException
	 * @throws FactoryException
	 * @throws com.vividsolutions.jts.io.ParseException
	 * @throws org.json.simple.parser.ParseException
	 * @throws URISyntaxException
	 */
	public StreamingPPTXImpl getGeoJsonMapPPTX(InputStream inp,
			String xClientInfo) throws IOException, ParseException,
			GeoWebCacheException, XMLStreamException,
			FactoryConfigurationError, RequestFilterException,
			TransformException, InterruptedException,
			NoSuchAuthorityCodeException, FactoryException,
			com.vividsolutions.jts.io.ParseException,
			org.json.simple.parser.ParseException, URISyntaxException {
		MapProducer producer = fork(xClientInfo);
		TileLayer tileLayer = config.getTileLayer(producer.getTemplateLayer());
		GridSubset gridSubset = tileLayer.getGridSubset(gridSubsetName);
		MaplinkGeoJsonParser parser = new MaplinkGeoJsonParser();
		boolean isDebug = ConfigValue.GEOJSON_DEBUG.getConfigProperty(props,
				"false").equals("true");

		parser.setDebug(isDebug);

		Map<String, ?> root = parser.parse(inp);

		MapLayerJSONParser mapLayerJsonParser = new MapLayerJSONParser(props);

		MapLink mapLink = mapLayerJsonParser.parseMapLinkJSON(root, getGf(),
				gridSubset.getResolutions());

		Map<String, String> values = mapLink.getValues();

		if (values.get("PAGESIZE") != null) {
			Page page = Page.valueOf(values.get("PAGESIZE"));

			values.put("WIDTH",
					Integer.toString(page.getWidthTargetInPoints(), 10));
			values.put("HEIGHT",
					Integer.toString(page.getHeightTargetInPoints(), 10));
			mapLink.setWidth(page.getWidthTargetInPoints());
			mapLink.setHeight(page.getHeightTargetInPoints());
		} else {
			mapLink.setWidth(Integer.valueOf(values.get("WIDTH"), 10));
			mapLink.setHeight(Integer.valueOf(values.get("HEIGHT"), 10));
		}

		mapLayerJsonParser.getMapLinkParser().validate(mapLink);

		StreamingPPTXImpl result = new StreamingPPTXImpl(producer, mapLink);

		result.underflow();
		return result;
	}

	/**
	 * gets snapshot PNG using values from JAX-RS GET request
	 * 
	 * @param values
	 * @param xClientInfo
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 * @throws GeoWebCacheException
	 * @throws XMLStreamException
	 * @throws FactoryConfigurationError
	 * @throws RequestFilterException
	 * @throws TransformException
	 * @throws NoSuchAuthorityCodeException
	 * @throws FactoryException
	 * @throws com.vividsolutions.jts.io.ParseException
	 */
	public StreamingJSONImpl getMapExtentJSON(Map<String, String> values,
			String xClientInfo) throws IOException, ParseException,
			GeoWebCacheException, XMLStreamException,
			FactoryConfigurationError, RequestFilterException,
			TransformException, NoSuchAuthorityCodeException, FactoryException,
			com.vividsolutions.jts.io.ParseException {

		MapProducer producer = fork(xClientInfo);
		TileLayer tileLayer = config.getTileLayer(producer.getTemplateLayer());
		GridSubset gridSubset = tileLayer.getGridSubset(gridSubsetName);

		Page page = Page.valueOf(values.get("PAGESIZE"));

		values.put("WIDTH", Integer.toString(page.getWidthTargetInPoints(), 10));
		values.put("HEIGHT",
				Integer.toString(page.getHeightTargetInPoints(), 10));

		String scaleResolverId = ConfigValue.SCALE_RESOLVER.getConfigProperty(
				props, "m_ol212");
		MapLinkParser mapLinkParser = new MapLinkParser(
				MetricScaleResolutionUtils.getScaleResolver(scaleResolverId),
				producer.getZoomOffset());

		MapLink mapLink = mapLinkParser.parseValueMapLink(values, layerJson,
				gf, gridSubset.getResolutions());
		mapLink.getValues().putAll(values);

		mapLinkParser.validate(mapLink);

		int width = page.getWidthTargetInPoints();
		int height = page.getHeightTargetInPoints();
		Point centre = mapLink.getCentre();
		Envelope env = producer.getProcessor().getEnvFromPointZoomAndExtent(
				centre, mapLink.getZoom(), width, height);

		/*
		 * producer.getGsf().setEnvelope(env); Polygon extent =
		 * producer.getGsf().createRectangle();
		 */
		GeometryFactory gf = new GeometryFactory();
		Polygon extent = gf.createPolygon(
				gf.createLinearRing(new Coordinate[] {
						new Coordinate(env.getMinX(), env.getMinY()),
						new Coordinate(env.getMaxX(), env.getMinY()),
						new Coordinate(env.getMaxX(), env.getMaxY()),
						new Coordinate(env.getMinX(), env.getMaxY()),
						new Coordinate(env.getMinX(), env.getMinY()) }), null);

		DefaultFeatureCollection features = new DefaultFeatureCollection(null,
				null);

		SimpleFeatureBuilder builder = createBuilder(producer.getCrs(), extent,
				width, height, "MapExtent");

		SimpleFeature f = builder.buildFeature("");
		features.add(f);

		mapLinkParser.validate(mapLink);

		StreamingJSONImpl result = new StreamingJSONImpl(features);

		return result;
	}

	/**
	 * gets map using parameters from JSON spec
	 * 
	 * @param inp
	 * @param xClientInfo
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 * @throws GeoWebCacheException
	 * @throws XMLStreamException
	 * @throws FactoryConfigurationError
	 * @throws RequestFilterException
	 * @throws TransformException
	 * @throws InterruptedException
	 * @throws NoSuchAuthorityCodeException
	 * @throws FactoryException
	 * @throws com.vividsolutions.jts.io.ParseException
	 * @throws URISyntaxException
	 */
	public StreamingPDFImpl getMapPDF(InputStream inp, String xClientInfo)
			throws IOException, ParseException, GeoWebCacheException,
			XMLStreamException, FactoryConfigurationError,
			RequestFilterException, TransformException, InterruptedException,
			NoSuchAuthorityCodeException, FactoryException,
			com.vividsolutions.jts.io.ParseException, URISyntaxException {
		MapProducer producer = fork(xClientInfo);
		TileLayer tileLayer = config.getTileLayer(producer.getTemplateLayer());
		GridSubset gridSubset = tileLayer.getGridSubset(gridSubsetName);
		MapLayerJSONParser mapLayerJsonParser = new MapLayerJSONParser(props);

		MapLink mapLink = mapLayerJsonParser.parseMapLinkJSON(inp, getGf(),
				gridSubset.getResolutions());

		Map<String, String> values = mapLink.getValues();

		Page page = Page.valueOf(values.get("PAGESIZE"));

		values.put("WIDTH", Integer.toString(page.getWidthTargetInPoints(), 10));
		values.put("HEIGHT",
				Integer.toString(page.getHeightTargetInPoints(), 10));
		mapLink.setWidth(page.getWidthTargetInPoints());
		mapLink.setHeight(page.getHeightTargetInPoints());

		mapLayerJsonParser.getMapLinkParser().validate(mapLink);

		PDFProducer.Options pageOptions = getPageOptions(values);

		StreamingPDFImpl result = new StreamingPDFImpl(producer, mapLink, page,
				pageOptions);

		result.underflow();
		return result;
	}

	/**
	 * gets PDF snapshot using parameters from JAX-RS GET request
	 * 
	 * @param values
	 * @param xClientInfo
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 * @throws GeoWebCacheException
	 * @throws XMLStreamException
	 * @throws FactoryConfigurationError
	 * @throws RequestFilterException
	 * @throws TransformException
	 * @throws InterruptedException
	 * @throws NoSuchAuthorityCodeException
	 * @throws FactoryException
	 * @throws com.vividsolutions.jts.io.ParseException
	 * @throws URISyntaxException
	 */
	public StreamingPDFImpl getMapPDF(Map<String, String> values,
			String xClientInfo) throws IOException, ParseException,
			GeoWebCacheException, XMLStreamException,
			FactoryConfigurationError, RequestFilterException,
			TransformException, InterruptedException,
			NoSuchAuthorityCodeException, FactoryException,
			com.vividsolutions.jts.io.ParseException, URISyntaxException {
		MapProducer producer = fork(xClientInfo);
		TileLayer tileLayer = config.getTileLayer(producer.getTemplateLayer());
		GridSubset gridSubset = tileLayer.getGridSubset(gridSubsetName);
		Page page = Page.valueOf(values.get("PAGESIZE"));

		values.put("WIDTH", Integer.toString(page.getWidthTargetInPoints(), 10));
		values.put("HEIGHT",
				Integer.toString(page.getHeightTargetInPoints(), 10));

		String scaleResolverId = ConfigValue.SCALE_RESOLVER.getConfigProperty(
				props, "m_ol212");
		MapLinkParser mapLinkParser = new MapLinkParser(
				MetricScaleResolutionUtils.getScaleResolver(scaleResolverId),
				producer.getZoomOffset());

		MapLink mapLink = mapLinkParser.parseValueMapLink(values, layerJson,
				gf, gridSubset.getResolutions());
		mapLink.getValues().putAll(values);

		mapLinkParser.validate(mapLink);

		PDFProducer.Options pageOptions = getPageOptions(values);

		StreamingPDFImpl result = new StreamingPDFImpl(producer, mapLink, page,
				pageOptions);

		result.underflow();
		return result;
	}

	/**
	 * gets Map PNG using parameters from JSON spec
	 * 
	 * @param inp
	 * @param xClientInfo
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 * @throws GeoWebCacheException
	 * @throws XMLStreamException
	 * @throws FactoryConfigurationError
	 * @throws RequestFilterException
	 * @throws TransformException
	 * @throws NoSuchAuthorityCodeException
	 * @throws FactoryException
	 * @throws com.vividsolutions.jts.io.ParseException
	 * @throws URISyntaxException
	 */
	public StreamingPNGImpl getMapPNG(InputStream inp, String xClientInfo)
			throws IOException, ParseException, GeoWebCacheException,
			XMLStreamException, FactoryConfigurationError,
			RequestFilterException, TransformException,
			NoSuchAuthorityCodeException, FactoryException,
			com.vividsolutions.jts.io.ParseException, URISyntaxException {
		MapProducer producer = fork(xClientInfo);
		TileLayer tileLayer = config.getTileLayer(producer.getTemplateLayer());
		GridSubset gridSubset = tileLayer.getGridSubset(gridSubsetName);

		MapLayerJSONParser mapLayerJsonParser = new MapLayerJSONParser(props);

		MapLink mapLink = mapLayerJsonParser.parseMapLinkJSON(inp, getGf(),
				gridSubset.getResolutions());

		Map<String, String> values = mapLink.getValues();

		if (values.get("PAGESIZE") != null) {
			Page page = Page.valueOf(values.get("PAGESIZE"));

			values.put("WIDTH",
					Integer.toString(page.getWidthTargetInPoints(), 10));
			values.put("HEIGHT",
					Integer.toString(page.getHeightTargetInPoints(), 10));
			mapLink.setWidth(page.getWidthTargetInPoints());
			mapLink.setHeight(page.getHeightTargetInPoints());

		}

		/* fixes to help UI */
		if (values.get("SCALEDWIDTH") != null
				&& values.get("SCALEDHEIGHT") == null) {

			/* calc based on WIDHT/HEIGHT */
			int targetScaledHeight = Integer.valueOf(values.get("SCALEDWIDTH"),
					10)
					* Integer.valueOf(values.get("HEIGHT"), 10)
					/ Integer.valueOf(values.get("WIDTH"), 10);

			values.put("SCALEDHEIGHT", Integer.toString(targetScaledHeight, 10));

		} else if (values.get("SCALEDWIDTH") == null
				&& values.get("SCALEDHEIGHT") != null) {

			int targetScaledWidth = Integer.valueOf(values.get("SCALEDHEIGHT"),
					10)
					* Integer.valueOf(values.get("WIDTH"), 10)
					/ Integer.valueOf(values.get("HEIGHT"), 10);

			/* calc based on WIDHT/HEIGHT */
			values.put("SCALEDHEIGHT", Integer.toString(targetScaledWidth, 10));

		}

		String scaleResolverId = ConfigValue.SCALE_RESOLVER.getConfigProperty(
				props, "m_ol212");
		MapLinkParser mapLinkParser = new MapLinkParser(
				MetricScaleResolutionUtils.getScaleResolver(scaleResolverId),
				producer.getZoomOffset());

		mapLinkParser.validate(mapLink);

		StreamingPNGImpl result = new StreamingPNGImpl(producer, mapLink);
		result.underflow();

		return result;
	}

	/**
	 * gets snapshot PNG using values from JAX-RS GET request
	 * 
	 * @param values
	 * @param xClientInfo
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 * @throws GeoWebCacheException
	 * @throws XMLStreamException
	 * @throws FactoryConfigurationError
	 * @throws RequestFilterException
	 * @throws TransformException
	 * @throws NoSuchAuthorityCodeException
	 * @throws FactoryException
	 * @throws com.vividsolutions.jts.io.ParseException
	 * @throws URISyntaxException
	 */
	public StreamingPNGImpl getMapPNG(Map<String, String> values,
			String xClientInfo) throws IOException, ParseException,
			GeoWebCacheException, XMLStreamException,
			FactoryConfigurationError, RequestFilterException,
			TransformException, NoSuchAuthorityCodeException, FactoryException,
			com.vividsolutions.jts.io.ParseException, URISyntaxException {
		MapProducer producer = fork(xClientInfo);
		TileLayer tileLayer = config.getTileLayer(producer.getTemplateLayer());
		GridSubset gridSubset = tileLayer.getGridSubset(gridSubsetName);

		if (values.get("PAGESIZE") != null) {
			Page page = Page.valueOf(values.get("PAGESIZE"));

			values.put("WIDTH",
					Integer.toString(page.getWidthTargetInPoints(), 10));
			values.put("HEIGHT",
					Integer.toString(page.getHeightTargetInPoints(), 10));
		}

		/* fixes to help UI */
		if (values.get("SCALEDWIDTH") != null
				&& values.get("SCALEDHEIGHT") == null) {

			/* calc based on WIDHT/HEIGHT */
			int targetScaledHeight = Integer.valueOf(values.get("SCALEDWIDTH"),
					10)
					* Integer.valueOf(values.get("HEIGHT"), 10)
					/ Integer.valueOf(values.get("WIDTH"), 10);

			values.put("SCALEDHEIGHT", Integer.toString(targetScaledHeight, 10));

		} else if (values.get("SCALEDWIDTH") == null
				&& values.get("SCALEDHEIGHT") != null) {

			int targetScaledWidth = Integer.valueOf(values.get("SCALEDHEIGHT"),
					10)
					* Integer.valueOf(values.get("WIDTH"), 10)
					/ Integer.valueOf(values.get("HEIGHT"), 10);

			/* calc based on WIDHT/HEIGHT */
			values.put("SCALEDHEIGHT", Integer.toString(targetScaledWidth, 10));

		}

		String scaleResolverId = ConfigValue.SCALE_RESOLVER.getConfigProperty(
				props, "m_ol212");
		MapLinkParser mapLinkParser = new MapLinkParser(
				MetricScaleResolutionUtils.getScaleResolver(scaleResolverId),
				producer.getZoomOffset());

		MapLink mapLink = mapLinkParser.parseValueMapLink(values, layerJson,
				gf, gridSubset.getResolutions());
		mapLink.getValues().putAll(values);

		mapLinkParser.validate(mapLink);

		StreamingPNGImpl result = new StreamingPNGImpl(producer, mapLink);
		result.underflow();

		return result;
	}

	public StreamingOutput getMapPPTX(Map<String, String> values,
			String xClientInfo) throws NoSuchAuthorityCodeException,
			IOException, GeoWebCacheException, FactoryException,
			com.vividsolutions.jts.io.ParseException, ParseException,
			XMLStreamException, FactoryConfigurationError,
			RequestFilterException, TransformException, URISyntaxException {

		MapProducer producer = fork(xClientInfo);
		TileLayer tileLayer = config.getTileLayer(producer.getTemplateLayer());
		GridSubset gridSubset = tileLayer.getGridSubset(gridSubsetName);
		Page page = Page.valueOf(values.get("PAGESIZE"));

		values.put("WIDTH", Integer.toString(page.getWidthTargetInPoints(), 10));
		values.put("HEIGHT",
				Integer.toString(page.getHeightTargetInPoints(), 10));

		String scaleResolverId = ConfigValue.SCALE_RESOLVER.getConfigProperty(
				props, "m_ol212");
		MapLinkParser mapLinkParser = new MapLinkParser(
				MetricScaleResolutionUtils.getScaleResolver(scaleResolverId),
				producer.getZoomOffset());

		MapLink mapLink = mapLinkParser.parseValueMapLink(values, layerJson,
				gf, gridSubset.getResolutions());
		mapLink.getValues().putAll(values);

		mapLinkParser.validate(mapLink);

		StreamingPPTXImpl result = new StreamingPPTXImpl(producer, mapLink);

		result.underflow();
		return result;

	}

	/**
	 * this is bad stuff
	 * 
	 * @param values
	 * @return
	 */
	private Options getPageOptions(Map<String, String> values) {
		PDFProducer.Options pageOptions = new PDFProducer.Options();
		pageOptions.setPageTitle(values.get("PAGETITLE"));
		pageOptions.setPageDate(values.get("PAGEDATE") != null ? values.get(
				"PAGEDATE").equals("true") : false);
		pageOptions.setPageScale(values.get("PAGESCALE") != null ? values.get(
				"PAGESCALE").equals("true") : false);
		pageOptions.setPageLogo(values.get("PAGELOGO") != null ? values.get(
				"PAGELOGO").equals("true") : false);

		pageOptions.setPageLegend(values.get("PAGELEGEND") != null ? values
				.get("PAGELEGEND").equals("true") : false);
		pageOptions.setPageCopyleft(values.get("PAGECOPYLEFT") != null ? values
				.get("PAGECOPYLEFT").equals("true") : false);

		return pageOptions;
	}

}
