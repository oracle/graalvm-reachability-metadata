/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package classworlds.classworlds;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.codehaus.classworlds.ClassRealm;
import org.codehaus.classworlds.ClassWorld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RealmClassLoaderTest {
    private static final String RESOURCE_NAME = "META-INF/classworlds/realm-resource.txt";
    private static final String RESOURCE_CONTENT = "resource loaded directly from a realm constituent";

    @TempDir
    Path temporaryDirectory;

    @Test
    void getResourceFindsResourceFromRealmConstituent() throws Exception {
        Path constituentDirectory = temporaryDirectory.resolve("realm-constituent");
        Path resourceFile = constituentDirectory.resolve(RESOURCE_NAME);
        Files.createDirectories(resourceFile.getParent());
        Files.writeString(resourceFile, RESOURCE_CONTENT, StandardCharsets.UTF_8);
        ClassRealm realm = new ClassWorld().newRealm("direct-resource-realm");
        realm.addConstituent(constituentDirectory.toUri().toURL());

        URL resource = realm.getResource(RESOURCE_NAME);

        assertThat(resource).isNotNull();
        try (InputStream inputStream = resource.openStream()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo(RESOURCE_CONTENT);
        }
    }
}
