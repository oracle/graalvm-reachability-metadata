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

import org.apache.maven.doxia.site.decoration.DecorationModel;
import org.apache.maven.doxia.site.decoration.PublishDate;
import org.apache.maven.doxia.siterenderer.DefaultSiteRenderer;
import org.apache.maven.doxia.siterenderer.RenderingContext;
import org.apache.maven.doxia.siterenderer.SiteRenderingContext;
import org.apache.velocity.context.Context;
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

    @Test
    void documentVelocityContextIncludesBundledRendererVersion(@TempDir Path temporaryDirectory) {
        ContextExposingSiteRenderer renderer = new ContextExposingSiteRenderer();
        renderer.enableLogging(new ConsoleLogger(Logger.LEVEL_DISABLED, "test"));
        SiteRenderingContext siteRenderingContext = new SiteRenderingContext();
        DecorationModel decoration = new DecorationModel();
        PublishDate publishDate = new PublishDate();
        publishDate.setFormat("yyyy-MM-dd");
        decoration.setPublishDate(publishDate);
        siteRenderingContext.setDecoration(decoration);
        RenderingContext renderingContext =
                new RenderingContext(temporaryDirectory.toFile(), "index.apt", "apt", "apt");

        Context context = renderer.createDocumentContext(renderingContext, siteRenderingContext);

        assertThat(context.get("doxiaSiteRendererVersion"))
                .isInstanceOf(String.class)
                .asString()
                .isNotBlank();
    }

    private static void copyResources(DefaultSiteRenderer renderer, SiteRenderingContext context, File outputDirectory)
            throws IOException {
        renderer.copyResources(context, outputDirectory);
    }

    private static final class ContextExposingSiteRenderer extends DefaultSiteRenderer {
        private Context createDocumentContext(
                RenderingContext renderingContext, SiteRenderingContext siteRenderingContext) {
            return createDocumentVelocityContext(renderingContext, siteRenderingContext);
        }
    }

}
