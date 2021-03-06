package fi.nls.oskari.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Locale;
import java.util.Properties;

import static org.junit.Assert.*;

/**
 * @author SMAKINEN
 */
public class PropertyUtilTest {

    @Before
    public void setUp() {
        PropertyUtil.clearProperties();
        Properties properties = new Properties();
        try {
            properties.load(PropertyUtilTest.class.getResourceAsStream("test.properties"));
            PropertyUtil.addProperties(properties);
        } catch (IOException ioe) {
            fail("Should not throw IOException:\n" + ioe.getStackTrace());
        } catch(DuplicateException de) {
            fail("Should not throw DuplicateException:\n" + de.getMessage());
        }
    }

    @After
    public  void teardown() {
        PropertyUtil.clearProperties();
    }

    @Test
    public void test() {
        String workerCount = PropertyUtil.get("workerCount");
        assertEquals("Should get 10", workerCount, "10");

        String redisHostname = PropertyUtil.get("redisHostname");
        assertTrue("Should get 'localhost'", redisHostname.equals("localhost"));

        String redisPort = PropertyUtil.get("redisPort");
        assertEquals("Should get 6379", redisPort, "6379");
    }

    @Test(expected = DuplicateException.class)
    public void testDuplicate() throws Exception {
        PropertyUtil.addProperty("workerCount", "30");
        throw new IllegalStateException("Should not get this far");
    }

    @Test
    public void testDuplicateWithOverwrite() throws Exception {
        PropertyUtil.addProperty("workerCount", "30", true);
        assertEquals("Should get 30", PropertyUtil.get("workerCount"), "30");
    }

    @Test
    public void testLocales() throws Exception {
        final String propertyName = "myproperty";
        PropertyUtil.addProperty(propertyName, "for default");
        PropertyUtil.addProperty(propertyName, "for english", Locale.ENGLISH);
        PropertyUtil.addProperty(propertyName, "for germany", Locale.GERMANY);
        assertEquals("Should get 'for default'", PropertyUtil.get(propertyName), "for default");
        assertEquals("Should get 'for english'", PropertyUtil.get(Locale.ENGLISH, propertyName), "for english");
        assertEquals("Should get 'for germany'", PropertyUtil.get(Locale.GERMANY, propertyName), "for germany");
        assertEquals("Should get 'for default'", PropertyUtil.get(Locale.CHINA, propertyName), "for default");
    }

    @Test
    public void testOptional() throws Exception {
        assertEquals("Should get 'localhost'", PropertyUtil.getOptional("redisHostname"), "localhost");
        assertEquals("Should get '10'", PropertyUtil.getOptional("workerCount"), "10");
        assertEquals("Should get <null>", PropertyUtil.getOptional("non-existing-property"), null);
    }

    @Test
    public void testCommaSeparatedProperty() throws Exception {
        String[] values1 = PropertyUtil.getCommaSeparatedList("commaseparatedNoSpaces");
        String[] values2 = PropertyUtil.getCommaSeparatedList("commaseparatedWithSpaces");
        for(int i = 0 ; i < values1.length; ++i) {
            assertEquals("Values in both arrays should match", values1[i], values2[i]);
        }
        String[] values3 = PropertyUtil.getCommaSeparatedList("non-existing-property");
        assertEquals("Non-existing list should be zero length", values3.length, 0);
    }
}
