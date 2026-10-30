/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_toolchain;

import org.apache.maven.api.toolchain.InputLocation;
import org.apache.maven.api.toolchain.InputSource;
import org.apache.maven.api.toolchain.PersistedToolchains;
import org.apache.maven.api.toolchain.ToolchainModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PersistedToolchainsTest {
    @Test
    void snapshotsToolchainListInDescriptorOrder() {
        ToolchainModel jdk = ToolchainModel.newBuilder().type("jdk").provides(Map.of("version", "21")).build();
        ToolchainModel compiler = ToolchainModel.newBuilder().type("compiler").build();
        ToolchainModel custom = ToolchainModel.newBuilder().type("custom").build();
        List<ToolchainModel> definitions = new ArrayList<>(List.of(jdk, compiler, custom));

        PersistedToolchains descriptor = PersistedToolchains.newBuilder()
                .namespaceUri("urn:example:toolchains")
                .modelEncoding("UTF-16")
                .toolchains(definitions)
                .build();
        definitions.clear();

        assertEquals("urn:example:toolchains", descriptor.getNamespaceUri());
        assertEquals("UTF-16", descriptor.getModelEncoding());
        assertEquals(List.of(jdk, compiler, custom), descriptor.getToolchains());
        assertSame(compiler, descriptor.getToolchains().get(1));
        assertEquals(List.of(compiler, custom), descriptor.getToolchains().subList(1, 3));
        assertThrows(UnsupportedOperationException.class, () -> descriptor.getToolchains().add(jdk));
    }

    @Test
    void replacesToolchainsWithoutMutatingOriginalDescriptorOrProvenance() {
        ToolchainModel jdk = ToolchainModel.newBuilder().type("jdk").build();
        ToolchainModel compiler = ToolchainModel.newBuilder().type("compiler").build();
        InputLocation location = new InputLocation(3, 5, new InputSource("toolchains.xml"));
        PersistedToolchains original = PersistedToolchains.newBuilder()
                .namespaceUri("urn:example:toolchains")
                .modelEncoding("UTF-8")
                .toolchains(List.of(jdk))
                .location("toolchains", location)
                .importedFrom(location)
                .build();

        PersistedToolchains replacement = original.withToolchains(List.of(compiler, jdk));
        PersistedToolchains derived = original.with().toolchains(List.of(compiler)).build();

        assertEquals(List.of(jdk), original.getToolchains());
        assertEquals(List.of(compiler, jdk), replacement.getToolchains());
        assertEquals(List.of(compiler), derived.getToolchains());
        assertEquals(original.getNamespaceUri(), replacement.getNamespaceUri());
        assertEquals(original.getModelEncoding(), replacement.getModelEncoding());
        assertSame(location, replacement.getLocation("toolchains"));
        assertSame(location, replacement.getImportedFrom());
        assertSame(location, derived.getLocation("toolchains"));
    }

    @Test
    void emptyDescriptorHasDefaultEncodingAndImmutableEmptyToolchains() {
        PersistedToolchains descriptor = PersistedToolchains.newInstance();

        assertEquals("UTF-8", descriptor.getModelEncoding());
        assertTrue(descriptor.getToolchains().isEmpty());
        assertTrue(PersistedToolchains.newInstance(false).getToolchains().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> descriptor.getToolchains().add(ToolchainModel.newInstance()));
    }
}
