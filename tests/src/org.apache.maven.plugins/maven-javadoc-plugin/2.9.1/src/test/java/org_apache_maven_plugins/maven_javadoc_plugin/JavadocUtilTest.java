/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugins.maven_javadoc_plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import com.sun.javadoc.Tag;
import com.sun.tools.doclets.Taglet;
import org.apache.maven.plugin.javadoc.JavadocUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JavadocUtilTest {
    @TempDir
    Path tempDir;

    @Test
    void tagletDiscoveryInspectsLegacyJavadocToolsJar() throws Exception {
        assumeFalse(isNativeImageRuntime(), "Legacy taglet discovery relies on URLClassLoader-based jar loading");

        File tagletJar = createLegacyTagletJar();
        List<String> tagletClasses = JavadocUtilAccessor.tagletClassNames(tagletJar);

        assertThat(tagletClasses).contains(LegacyTagletFixture.class.getName());
    }

    private File createLegacyTagletJar() throws IOException {
        Path jar = tempDir.resolve("legacy-taglet.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            addClass(output, Taglet.class);
            addClass(output, LegacyTagletFixture.class);
        }
        return jar.toFile();
    }

    private static void addClass(JarOutputStream output, Class<?> type) throws IOException {
        String entryName = type.getName().replace('.', '/') + ".class";
        output.putNextEntry(new JarEntry(entryName));
        try (InputStream input = type.getClassLoader().getResourceAsStream(entryName)) {
            assertThat(input).isNotNull();
            input.transferTo(output);
        }
        output.closeEntry();
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    private static final class JavadocUtilAccessor extends JavadocUtil {
        private static List<String> tagletClassNames(File jarFile) throws Exception {
            return getTagletClassNames(jarFile);
        }
    }
}

class LegacyTagletFixture implements Taglet {
    @Override
    public boolean inField() {
        return false;
    }

    @Override
    public boolean inConstructor() {
        return false;
    }

    @Override
    public boolean inMethod() {
        return true;
    }

    @Override
    public boolean inOverview() {
        return false;
    }

    @Override
    public boolean inPackage() {
        return false;
    }

    @Override
    public boolean inType() {
        return false;
    }

    @Override
    public boolean isInlineTag() {
        return false;
    }

    @Override
    public String getName() {
        return "legacy";
    }

    @Override
    public String toString(Tag tag) {
        return tag == null ? "" : tag.text();
    }

    @Override
    public String toString(Tag[] tags) {
        return "";
    }
}
