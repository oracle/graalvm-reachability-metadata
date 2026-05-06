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
    @TempDir
    Path temporaryDirectory;

    @Test
    void realmResourceLookupReadsResourceDirectlyFromConstituent() throws Exception {
        Path resource = temporaryDirectory.resolve("realm-loader/direct-resource.txt");
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, "resource from realm constituent", StandardCharsets.UTF_8);
        ClassRealm realm = new ClassWorld().newRealm("app");
        realm.addConstituent(temporaryDirectory.toUri().toURL());

        URL resolved = realm.getResource("/realm-loader/direct-resource.txt");

        assertThat(resolved).isNotNull();
        try (InputStream input = resolved.openStream()) {
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("resource from realm constituent");
        }
    }
}
