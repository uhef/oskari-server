package fi.nls.oskari.control.sotka.requests;

import fi.nls.oskari.cache.JedisManager;
import fi.nls.oskari.control.ActionException;

/**
 * Request class for SotkaNET statistics query to list regions.
 * @author SMAKINEN
 */
public class Regions extends SotkaRequest {

    private final static String CACHE_KEY = "oskari_sotka_regions_list";

    @Override
    public String getName() {
        return "regions";
    }

    @Override
    public String getRequestSpecificParams() {
        return "/regions";
    }

    @Override
    public String getData() throws ActionException {
        final String cachedData = JedisManager.get(CACHE_KEY);
        if(cachedData != null && !cachedData.isEmpty()) {
            return cachedData;
        }
        final String data = super.getData();
        JedisManager.setex(CACHE_KEY, JedisManager.EXPIRY_TIME_DAY, data);
        return data;
    }
}
