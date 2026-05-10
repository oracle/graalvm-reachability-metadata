/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_doxia.doxia_site_renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.doxia.siterenderer.DefaultSiteRenderer;
import org.apache.maven.doxia.siterenderer.SiteRenderingContext;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DefaultSiteRendererTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    public void copyResourcesReadsResourceListAndCopiesClasspathResource() throws Exception {
        Path outputDirectory = Files.createDirectories(temporaryDirectory.resolve("site-output"));

        SiteRenderingContext context = new SiteRenderingContext();
        context.setUsingDefaultTemplate(true);

        DefaultSiteRenderer renderer = new DefaultSiteRenderer();
        renderer.enableLogging(new ConsoleLogger(Logger.LEVEL_INFO, "default-site-renderer-test"));

        IOException exception = assertThrows(
                IOException.class,
                () -> renderer.copyResources(context, null, outputDirectory.toFile()));

        String expectedMessage = "The resource missing-resource-for-test.txt doesn't exist.";
        assertEquals(expectedMessage, exception.getMessage());
        Path copiedResource = outputDirectory.resolve("css").resolve("maven-base.css");
        assertTrue(Files.isRegularFile(copiedResource));
        assertTrue(Files.size(copiedResource) > 0L);
    }
}
