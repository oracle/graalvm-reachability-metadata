/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_doxia.doxia_site_renderer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.doxia.site.PublishDate;
import org.apache.maven.doxia.site.SiteModel;
import org.apache.maven.doxia.siterenderer.DefaultSiteRenderer;
import org.apache.maven.doxia.siterenderer.DocumentRenderingContext;
import org.apache.maven.doxia.siterenderer.SiteRenderingContext;
import org.apache.velocity.context.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultSiteRendererTest {
    @Test
    void copyDirectoryCopiesNestedResources(@TempDir Path temporaryDirectory) throws Exception {
        ContextExposingSiteRenderer renderer = new ContextExposingSiteRenderer();
        Path resourcesDirectory = temporaryDirectory.resolve("resources");
        Path stylesheet = resourcesDirectory.resolve("css/site.css");
        Path image = resourcesDirectory.resolve("images/logo.txt");
        Files.createDirectories(stylesheet.getParent());
        Files.createDirectories(image.getParent());
        Files.writeString(stylesheet, "body { color: black; }", StandardCharsets.UTF_8);
        Files.writeString(image, "logo", StandardCharsets.UTF_8);
        File outputDirectory = temporaryDirectory.resolve("site").toFile();

        renderer.copyDirectory(resourcesDirectory.toFile(), outputDirectory);

        assertThat(outputDirectory.toPath().resolve("css/site.css"))
                .isRegularFile()
                .content(StandardCharsets.UTF_8)
                .isEqualTo("body { color: black; }");
        assertThat(outputDirectory.toPath().resolve("images/logo.txt"))
                .isRegularFile()
                .content(StandardCharsets.UTF_8)
                .isEqualTo("logo");
    }

    @Test
    void documentVelocityContextIncludesBundledRendererVersion(@TempDir Path temporaryDirectory) {
        ContextExposingSiteRenderer renderer = new ContextExposingSiteRenderer();
        SiteRenderingContext siteRenderingContext = new SiteRenderingContext();
        siteRenderingContext.setSiteModel(createSiteModel());
        DocumentRenderingContext renderingContext =
                new DocumentRenderingContext(temporaryDirectory.toFile(), "", "index.apt", "apt", "apt", true);

        Context context = renderer.createDocumentContext(renderingContext, siteRenderingContext);

        assertThat(context.get("doxiaSiteRendererVersion"))
                .isInstanceOf(String.class)
                .asString()
                .isNotBlank();
    }

    private static SiteModel createSiteModel() {
        SiteModel siteModel = new SiteModel();
        PublishDate publishDate = new PublishDate();
        publishDate.setFormat("yyyy-MM-dd");
        publishDate.setTimezone("UTC");
        siteModel.setPublishDate(publishDate);
        return siteModel;
    }

    private static final class ContextExposingSiteRenderer extends DefaultSiteRenderer {
        private Context createDocumentContext(
                DocumentRenderingContext renderingContext, SiteRenderingContext siteRenderingContext) {
            return createDocumentVelocityContext(renderingContext, siteRenderingContext);
        }

        @Override
        public void copyDirectory(File source, File destination) throws IOException {
            super.copyDirectory(source, destination);
        }
    }
}
