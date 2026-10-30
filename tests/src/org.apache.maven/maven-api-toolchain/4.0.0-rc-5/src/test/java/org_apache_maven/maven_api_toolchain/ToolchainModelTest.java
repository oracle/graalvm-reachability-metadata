/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_toolchain;

import org.apache.maven.api.toolchain.ToolchainModel;
import org.apache.maven.api.xml.XmlNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ToolchainModelTest {
    @Test
    void buildsJdkToolchainWithConfigurationAndCopiesProvidedProperties() {
        Map<String, String> provides = new HashMap<>();
        provides.put("version", "21");
        provides.put("vendor", "example");
        XmlNode configuration = XmlNode.newBuilder()
                .name("configuration")
                .children(List.of(XmlNode.newInstance("jdkHome", "example-jdk")))
                .build();

        ToolchainModel toolchain = ToolchainModel.newBuilder()
                .type("jdk")
                .provides(provides)
                .configuration(configuration)
                .build();
        provides.put("vendor", "changed");

        assertEquals("jdk", toolchain.getType());
        assertEquals(Map.of("version", "21", "vendor", "example"), toolchain.getProvides());
        assertSame(configuration, toolchain.getConfiguration());
        assertEquals("jdkHome", toolchain.getConfiguration().child("jdkHome").name());
        assertEquals("example-jdk", toolchain.getConfiguration().child("jdkHome").value());
        assertThrows(UnsupportedOperationException.class, () -> toolchain.getProvides().put("version", "other"));
    }

    @Test
    void derivesIndependentToolchainsThroughBuilderAndWithMethods() {
        XmlNode originalConfiguration = XmlNode.newInstance("configuration", "original");
        ToolchainModel original = ToolchainModel.newBuilder()
                .type("jdk")
                .provides(Map.of("vendor", "example"))
                .configuration(originalConfiguration)
                .build();

        ToolchainModel custom = original.with().type("compiler").build();
        ToolchainModel otherType = original.withType("custom");
        ToolchainModel otherVendor = original.withProvides(Map.of("vendor", "alternative"));
        XmlNode replacement = XmlNode.newInstance("configuration", "replacement");
        ToolchainModel reconfigured = original.withConfiguration(replacement);

        assertEquals("compiler", custom.getType());
        assertEquals(original.getProvides(), custom.getProvides());
        assertSame(originalConfiguration, custom.getConfiguration());
        assertEquals("custom", otherType.getType());
        assertEquals(Map.of("vendor", "alternative"), otherVendor.getProvides());
        assertSame(replacement, reconfigured.getConfiguration());
        assertEquals("jdk", original.getType());
        assertEquals(Map.of("vendor", "example"), original.getProvides());
        assertSame(originalConfiguration, original.getConfiguration());
    }

    @Test
    void identifiesToolchainsByTypeAndProvidedProperties() {
        ToolchainModel first = ToolchainModel.newBuilder()
                .type("jdk").provides(Map.of("vendor", "example")).build();
        ToolchainModel equivalent = ToolchainModel.newBuilder()
                .type("jdk").provides(Map.of("vendor", "example")).build();

        assertEquals(first, equivalent);
        assertEquals(first.hashCode(), equivalent.hashCode());
        assertNotEquals(first, first.withType("compiler"));
        assertNotEquals(first, first.withProvides(Map.of("vendor", "another")));
    }

    @Test
    void emptyToolchainHasNoConfigurationOrProvidedProperties() {
        ToolchainModel empty = ToolchainModel.newInstance();

        assertNull(empty.getType());
        assertNull(empty.getConfiguration());
        assertTrue(empty.getProvides().isEmpty());
        assertTrue(ToolchainModel.newInstance(false).getProvides().isEmpty());
    }
}
