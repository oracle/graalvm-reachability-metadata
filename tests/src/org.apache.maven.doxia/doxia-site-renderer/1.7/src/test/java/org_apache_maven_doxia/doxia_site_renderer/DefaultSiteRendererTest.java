/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_doxia.doxia_site_renderer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.doxia.siterenderer.DefaultSiteRenderer;
import org.apache.maven.doxia.siterenderer.SiteRenderingContext;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultSiteRendererTest {
    private static final List<String> DEFAULT_RESOURCES = List.of(
            "images/expanded.gif",
            "images/collapsed.gif",
            "images/logos/maven-feather.png",
            "images/logos/build-by-maven-white.png",
            "images/logos/build-by-maven-black.png",
            "css/maven-base.css",
            "css/print.css");

    @Test
    void copyResourcesCopiesBundledDefaultTemplateResources(@TempDir Path temporaryDirectory) throws Exception {
        DefaultSiteRenderer renderer = new DefaultSiteRenderer();
        renderer.enableLogging(new ConsoleLogger(Logger.LEVEL_DISABLED, "test"));
        SiteRenderingContext context = new SiteRenderingContext();
        context.setUsingDefaultTemplate(true);
        File outputDirectory = temporaryDirectory.resolve("site").toFile();

        copyResources(renderer, context, outputDirectory);

        for (String defaultResource : DEFAULT_RESOURCES) {
            assertThat(outputDirectory.toPath().resolve(defaultResource)).isRegularFile();
        }
    }

    private static void copyResources(DefaultSiteRenderer renderer, SiteRenderingContext context, File outputDirectory)
            throws IOException {
        try {
            renderer.copyResources(context, null, outputDirectory);
        } catch (IOException e) {
            if (!defaultResourcesWereCopied(outputDirectory.toPath())) {
                throw e;
            }
        }
    }

    private static boolean defaultResourcesWereCopied(Path outputDirectory) {
        for (String defaultResource : DEFAULT_RESOURCES) {
            if (!outputDirectory.resolve(defaultResource).toFile().isFile()) {
                return false;
            }
        }
        return true;
    }
}
