package io.fabric8.jube.registry;

import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.Map;
import java.util.Set;

public class RegistryFacadeTest {

    protected static RegistryFacade facade;
    private static boolean runTest;

    @BeforeClass
    public static void before() throws Exception {

        facade = new RegistryFacade();

        // only run if there is a mavenIndex dir already
        // otherwise the test takes very long time as it indexes maven central
        File dir = facade.getMavenIndexerCacheDirectory();
        if (dir != null && dir.exists() && dir.isDirectory()) {
            String[] files = dir.list();
            // there need to be more than the hawtio.lock file
            if (files != null && files.length > 1) {
                runTest = true;
            }
        }

        if (runTest) {
            facade.init();
        }
    }

    @AfterClass
    public static void after() throws Exception {
        if (runTest) {
            facade.destroy();
        }
    }

    @Test
    public void testSearch() throws Exception {
        if (!runTest) {
            return;
        }

        RepositoriesDTO repositories = facade.search("karaf");
        // should get back something!
        assertTrue(!repositories.results.isEmpty());
    }

    @Test
    public void testTags() throws Exception {
        if (!runTest) {
            return;
        }

        RepositoriesDTO repositories = facade.search("karaf");
        assertTrue(!repositories.results.isEmpty());
        for (RepositoryDTO repository : repositories.results) {
            Map<String, String> tags = facade.tags(repository.namespace(), repository.repository());
            Set<String> keys = tags.keySet();
            assertTrue(!keys.isEmpty());
        }
    }

    @Test
    public void testImageJson() throws Exception {
        if (!runTest) {
            return;
        }

        RepositoriesDTO repositories = facade.search("karaf");
        assertTrue(!repositories.results.isEmpty());
        for (RepositoryDTO repository : repositories.results) {
            Map<String, String> tags = facade.tags(repository.namespace(), repository.repository());
            Set<String> keys = tags.keySet();
            assertTrue(!keys.isEmpty());
            for (String key : keys) {
                Object json = facade.imageJson(tags.get(key));
                assertTrue(json != null);
            }
        }
    }
}
