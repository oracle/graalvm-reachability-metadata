/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Reference;
import org.apache.tools.ant.types.ResourceLocation;
import org.apache.tools.ant.types.XMLCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

public class XMLCatalogTest {
    private static final String PUBLIC_ID = "-//Example//DTD Referenced Catalog Entry//EN";

    @TempDir
    Path temporaryDirectory;

    @Test
    void referencedCatalogDelegatesEntityResolutionToTargetCatalog() throws Exception {
        Project project = new Project();
        project.setBaseDir(temporaryDirectory.toFile());

        XMLCatalog targetCatalog = new XMLCatalog();
        targetCatalog.setProject(project);
        targetCatalog.addDTD(resourceLocation(PUBLIC_ID, writeDtd().toString()));
        project.addReference("sharedCatalog", targetCatalog);

        XMLCatalog referringCatalog = new XMLCatalog();
        referringCatalog.setProject(project);
        referringCatalog.setRefid(new Reference("sharedCatalog"));

        InputSource inputSource = referringCatalog.resolveEntity(PUBLIC_ID, "missing.dtd");

        assertThat(inputSource).isNotNull();
        assertThat(inputSource.getSystemId()).endsWith("referenced-catalog-entry.dtd");
    }

    private Path writeDtd() throws IOException {
        return Files.writeString(
                temporaryDirectory.resolve("referenced-catalog-entry.dtd"),
                "<!ELEMENT example EMPTY>",
                StandardCharsets.UTF_8);
    }

    private ResourceLocation resourceLocation(String publicId, String location) {
        ResourceLocation resourceLocation = new ResourceLocation();
        resourceLocation.setPublicId(publicId);
        resourceLocation.setLocation(location);
        return resourceLocation;
    }
}
