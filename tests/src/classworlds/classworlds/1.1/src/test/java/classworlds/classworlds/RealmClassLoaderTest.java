/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package classworlds.classworlds;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.codehaus.classworlds.ClassRealm;
import org.codehaus.classworlds.ClassWorld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RealmClassLoaderTest {
    private static final String REALM_ID = "resource-realm";
    private static final String RESOURCE_NAME = "realm-resource/message.txt";
    private static final String RESOURCE_CONTENT = "loaded from a realm constituent";

    @TempDir
    private Path tempDir;

    @Test
    void getResourceLoadsResourceDirectlyFromRealmConstituent() throws Exception {
        Path resource = tempDir.resolve(RESOURCE_NAME);
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, RESOURCE_CONTENT, StandardCharsets.UTF_8);
        ClassRealm realm = new ClassWorld().newRealm(REALM_ID);
        realm.addConstituent(tempDir.toUri().toURL());

        URL resolved = realm.getResource("/" + RESOURCE_NAME);

        assertThat(resolved).isNotNull();
        assertThat(Files.readString(Path.of(resolved.toURI()), StandardCharsets.UTF_8))
                .isEqualTo(RESOURCE_CONTENT);
    }
}
