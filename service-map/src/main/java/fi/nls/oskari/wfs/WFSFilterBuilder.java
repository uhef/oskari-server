package fi.nls.oskari.wfs;

import java.io.ByteArrayOutputStream;

import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.xml.Configuration;
import org.geotools.xml.Encoder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opengis.filter.Filter;
import org.opengis.filter.identity.FeatureId;
import org.opengis.filter.FilterFactory2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Date: 6.8.2013
 * 
 */
public class WFSFilterBuilder {

    public static final String KEY_FILTERS = "filters";
    public static final String KEY_FEATUREIDS = "featureIds";
    public static final String KEY_VALUE = "value";
    public static final String KEY_ATTRIBUTE = "attribute";
    public static final String KEY_BOOLEAN = "boolean";
    public static final String KEY_OPERATOR = "operator";
    public static final String KEY_BBOX = "bbox";
    public static final String CLEAN_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    public static final String PROPERTY_TEMPLATE = "<wfs:PropertyName>{property}</wfs:PropertyName>";
    public static final String PROPERTY_PROPERTY = "{property}";

    private static final Logger log = LogFactory
            .getLogger(WFSFilterBuilder.class);
    private static final FilterFactory2 ff = CommonFactoryFinder
            .getFilterFactory2(null);

    /**
     * @param filter_js Analysis filter
     *            sample json syntax
     *            {
     *            "bbox" : {
     *            "bottom":6672660,"left":384863,"right":388283,"top":6674522
     *            },
     *            "filters" :[
     *            {"value" : "00530", "attribute" : "postinumero" , "operator" :
     *            "="},
     *            {"boolean" : "OR"},
     *            {"value" : "00500", "attribute" : "postinumero" , "operator" :
     *            "=" }
     *            ]
     *            }
     *            Sample 2:
     *            {"featureIds":["toimipaikat.11101","toimipaikat.13226",
     *            "toimipaikat.14699"
     *            ],"filters":[{"caseSensitive":false,"attribute"
     *            :"postinumero","operator":"=","value":"00530"}]}
     * @param srsName  name of GML coordinate reference system (e.g. EPSG:3067)
     * @param geom_elem name of geometry property in FeatureCollection
     * @return WFS filter in xml syntax
     */
    public static String parseWfsFilter(final JSONObject filter_js,
            String srsName, String geom_elem) {

        if (filter_js == null) {
            return null;
        }
        if (!filter_js.has(KEY_FILTERS) && !filter_js.has(KEY_FEATUREIDS)) {
            return null;
        }
        JSONArray jsArray = new JSONArray();

        try {
            if (filter_js.has(KEY_FILTERS)) {
                // Get feature ID filter input
                jsArray = filter_js.getJSONArray(KEY_FILTERS);
            }

            // Loop items
            // Add featureId filters 1st if any
            final List<Filter> andConditions = new ArrayList<Filter>();
            final List<Filter> orConditions = new ArrayList<Filter>();
            final List<Filter> notConditions = new ArrayList<Filter>();

            // Check 1st logical operator, default to "AND"
            boolean isAnd = true, isOr = false, isNot = false;
            if (jsArray.length() > 2) {
                // only check if we have more than 2 elements
                final JSONObject booleanOperator = jsArray.getJSONObject(1);
                if (booleanOperator.has(KEY_BOOLEAN)) {
                    // in that case the first boolean operation SHOULD be found
                    // on second index
                    final String operatorStr = booleanOperator
                            .optString(KEY_BOOLEAN);
                    isAnd = "AND".equals(operatorStr);
                    isOr = "OR".equals(operatorStr);
                    isNot = "NOT".equals(operatorStr);
                }
            }

            for (int i = 0; i < jsArray.length(); i++) {
                final JSONObject filter_item = jsArray.getJSONObject(i);

                if (filter_item.has(KEY_BOOLEAN)) {
                    final String operatorStr = filter_item
                            .optString(KEY_BOOLEAN);
                    isAnd = "AND".equals(operatorStr);
                    isOr = "OR".equals(operatorStr);
                    isNot = "NOT".equals(operatorStr);
                    // move to next item since this was a boolean operator
                    continue;
                }

                // Get operator
                final String operator = filter_item.optString(KEY_OPERATOR);
                final String attribute = filter_item.optString(KEY_ATTRIBUTE);
                final Object value = filter_item.opt(KEY_VALUE);

                Filter filter = getFilter(operator, attribute, value);

                if (filter == null) {
                    // Special case for NOT like operator
                    filter = getNotFilter(operator, attribute, value);
                    if (filter != null) {
                        isAnd = false;
                        isOr = false;
                        isNot = true;
                    }
                }

                if (filter != null) {
                    if (isAnd)
                        andConditions.add(filter);
                    else if (isOr)
                        orConditions.add(filter);
                    else if (isNot)
                        notConditions.add(filter);
                }
            }

            // Combine filters

            Filter all = null;

            // include 1st Feature Id filters if any
            final Filter ids = getFeatureIdFilters(filter_js);
            if (ids != null) {
                all = ids;
            }

            if (andConditions.size() > 0) {
                all = appendFilter(all, ff.and(andConditions));
            }
            if (orConditions.size() > 0) {
                all = appendFilter(all, ff.or(orConditions));
            }
            for (Filter mynot : notConditions) {
                all = appendFilter(all, ff.not(mynot));
            }

            // include BBOX filter if any
            final Filter fbbox = getBboxFilter(filter_js, srsName, geom_elem);
            if (fbbox != null) {
                all = appendFilter(all, fbbox);
            }

            // output Filter as string
            return getFilterAsString(all);
        } catch (JSONException e) {
            log.warn(e, "JSON parse failed");
        }
        return null;
    }

    private static Filter appendFilter(final Filter main, final Filter toAppend) {
        if (main == null) {
            return toAppend;
        } else {
            return ff.and(main, toAppend);
        }
    }

    /**
     * Creates a filter based on given parameters in analysis
     * @param operator filter operator
     * @param attribute WFS property name
     * @param value  filter property value
     * @return
     */
    private static Filter getFilter(final String operator,
            final String attribute, final Object value) {
        final boolean matchCase = false;

        if ("=".equals(operator)) {
            return ff.equal(ff.property(attribute), ff.literal(value),
                    matchCase);
        } else if (">".equals(operator)) {
            return ff.greater(ff.property(attribute), ff.literal(value),
                    matchCase);

        } else if ("<".equals(operator)) {
            return ff
                    .less(ff.property(attribute), ff.literal(value), matchCase);
        } else if ("~=".equals(operator)) {
            return ff.like(ff.property(attribute), (String) value, "*", "#",
                    "!", matchCase);
        } else if ("≥".equals(operator)) {
            return ff.greaterOrEqual(ff.property(attribute), ff.literal(value),
                    matchCase);

        } else if ("≠".equals(operator)) {
            return ff.notEqual(ff.property(attribute), ff.literal(value),
                    matchCase);

        } else if ("≤".equals(operator)) {
            return ff.lessOrEqual(ff.property(attribute), ff.literal(value),
                    matchCase);
        }
        return null;
    }

    /**
     * Creates a filter based on given parameters in analysis
     * Only for operator  "~≠"  NOT like
     * @param operator filter operator
     * @param attribute WFS property name
     * @param value  filter property value
     * @return
     */
    private static Filter getNotFilter(final String operator,
                                    final String attribute, final Object value) {
        final boolean matchCase = false;

        if ("~≠".equals(operator)) {
            List<Filter> nlikes = new ArrayList<Filter>();
            nlikes.add(ff.like(ff.property(attribute), (String) value, "*", "#",
                    "!", matchCase));
            return ff.and(nlikes);
        }
        return null;
    }
    /**
     * Creates WFS BBOX filter for WFS query
     * @param filter_js analysis filter json
     * @param srsName   name of GML coordinate reference system
     * @param geom_elem  name of geometry property in FeatureCollection
     * @return WFS BBOX filter
     * @throws JSONException
     */
    private static Filter getBboxFilter(final JSONObject filter_js,
            String srsName, String geom_elem) throws JSONException {
        if (filter_js.has(KEY_BBOX)) {
            // Add BBOX filter
            JSONObject bbox = filter_js.getJSONObject(KEY_BBOX);
            return ff.bbox(geom_elem, bbox.getDouble("left"), bbox
                    .getDouble("bottom"), bbox.getDouble("right"), bbox
                    .getDouble("top"), srsName);
        }
        return null;
    }

    /**
     * WFS filter to xml string
     * @param all
     * @return filter in xml syntax
     */
    private static String getFilterAsString(final Filter all) {

        final Configuration conf2 = new org.geotools.filter.v1_1.OGCConfiguration();
        final Encoder encoder = new Encoder(conf2);
        encoder.setIndenting(true);
        encoder.setIndentSize(2);
        final ByteArrayOutputStream ostream = new ByteArrayOutputStream();
        try {
            encoder.encode(all, org.geotools.filter.v1_1.OGC.Filter, ostream);
            final String filter_out = new String(ostream.toByteArray(), "UTF-8");
            return filter_out.replace(CLEAN_HEADER, "");
        } catch (Exception ee) {
            log.debug(">> test " + ee.getMessage());
        }
        return null;
    }

    /**
     * Creates WFS filter for selected feature ids
     * @param filter_js Analysis filter json
     * @return
     * @throws JSONException
     */
    private static Filter getFeatureIdFilters(final JSONObject filter_js)
            throws JSONException {
        Set<FeatureId> selected = new HashSet<FeatureId>();

        if (filter_js.has(KEY_FEATUREIDS)) {
            // Get feature ID filter input
            final JSONArray jsIdArray = filter_js.getJSONArray(KEY_FEATUREIDS);
            for (int i = 0; i < jsIdArray.length(); i++) {
                final String featureid = jsIdArray.optString(i);

                selected.add(ff.featureId(featureid));
            }
            return ff.id(selected);
        } else
            return null;

    }

    public static String parseProperties(List<String> props, String ns, String geom_prop) {
        String query = "";
        for (String prop : props) {
            String temp = PROPERTY_TEMPLATE.replace(PROPERTY_PROPERTY, ns + ":"
                    + prop);
            query = query + temp;
        }
        if(!query.isEmpty())
        {
            // geometry is not retreaved, if this is lacking
            String temp = PROPERTY_TEMPLATE.replace(PROPERTY_PROPERTY, geom_prop);
            query = query + temp;
        }

        return query;
    }
}
