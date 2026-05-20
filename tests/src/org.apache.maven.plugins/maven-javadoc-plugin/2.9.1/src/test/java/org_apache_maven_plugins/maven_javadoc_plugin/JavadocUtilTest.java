/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugins.maven_javadoc_plugin;

import org.apache.maven.plugin.javadoc.JavadocUtil;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class JavadocUtilTest {
    private static final Map<String, String> LEGACY_TAGLET_CLASSES = Map.of(
        "com/sun/tools/doclets/Taglet.class", """
            yv66vgAAADQABwcAAgEAHGNvbS9zdW4vdG9vbHMvZG9jbGV0cy9UYWdsZXQHAAQBABBqYXZhL2xh
            bmcvT2JqZWN0AQAKU291cmNlRmlsZQEAC1RhZ2xldC5qYXZhBgEAAQADAAAAAAAAAAEABQAAAAIA
            Bg==
            """,
        "example/taglets/DetectedTaglet.class", """
            yv66vgAAADQADwoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClW
            BwAIAQAeZXhhbXBsZS90YWdsZXRzL0RldGVjdGVkVGFnbGV0BwAKAQAcY29tL3N1bi90b29scy9k
            b2NsZXRzL1RhZ2xldAEABENvZGUBAA9MaW5lTnVtYmVyVGFibGUBAApTb3VyY2VGaWxlAQATRGV0
            ZWN0ZWRUYWdsZXQuamF2YQAhAAcAAgABAAkAAAABAAEABQAGAAEACwAAAB0AAQABAAAABSq3AAGx
            AAAAAQAMAAAABgABAAAAAgABAA0AAAACAA4=
            """,
        "example/taglets/AbstractTaglet.class", """
            yv66vgAAADQADwoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClW
            BwAIAQAeZXhhbXBsZS90YWdsZXRzL0Fic3RyYWN0VGFnbGV0BwAKAQAcY29tL3N1bi90b29scy9k
            b2NsZXRzL1RhZ2xldAEABENvZGUBAA9MaW5lTnVtYmVyVGFibGUBAApTb3VyY2VGaWxlAQATQWJz
            dHJhY3RUYWdsZXQuamF2YQQhAAcAAgABAAkAAAABAAEABQAGAAEACwAAAB0AAQABAAAABSq3AAGx
            AAAAAQAMAAAABgABAAAAAgABAA0AAAACAA4=
            """,
        "example/taglets/NotATaglet.class", """
            yv66vgAAADQADQoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClW
            BwAIAQAaZXhhbXBsZS90YWdsZXRzL05vdEFUYWdsZXQBAARDb2RlAQAPTGluZU51bWJlclRhYmxl
            AQAKU291cmNlRmlsZQEAD05vdEFUYWdsZXQuamF2YQAhAAcAAgAAAAAAAQABAAUABgABAAkAAAAd
            AAEAAQAAAAUqtwABsQAAAAEACgAAAAYAAQAAAAIAAQALAAAAAgAM
            """
    );

    @TempDir
    Path temporaryDirectory;

    @Test
    void detectsConcreteLegacyTagletClassesFromJar() throws Exception {
        final Path tagletJar = temporaryDirectory.resolve("legacy-taglets.jar");
        writeJar(tagletJar, LEGACY_TAGLET_CLASSES);

        try {
            final List<String> tagletClassNames = JavadocUtilAccess.getTagletClassNamesFor(tagletJar.toFile());

            assertThat(tagletClassNames).containsExactly("example.taglets.DetectedTaglet");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static void writeJar(Path jarFile, Map<String, String> entries) throws IOException {
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarFile))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                jarOutputStream.putNextEntry(new JarEntry(entry.getKey()));
                jarOutputStream.write(Base64.getMimeDecoder().decode(entry.getValue()));
                jarOutputStream.closeEntry();
            }
        }
    }

    private static final class JavadocUtilAccess extends JavadocUtil {
        private static List<String> getTagletClassNamesFor(File jarFile) throws Exception {
            return getTagletClassNames(jarFile);
        }
    }
}
