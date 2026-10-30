/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_settings;

import org.apache.maven.api.settings.Activation;
import org.apache.maven.api.settings.ActivationFile;
import org.apache.maven.api.settings.ActivationOS;
import org.apache.maven.api.settings.ActivationProperty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ActivationTest {
    @Test
    void buildsNestedProfileActivationCriteria() {
        ActivationOS os = ActivationOS.newBuilder()
                .name("Linux").family("unix").arch("amd64").version("6").build();
        ActivationProperty property = ActivationProperty.newBuilder().name("environment").value("ci").build();
        ActivationFile file = ActivationFile.newBuilder().exists("pom.xml").build();
        Activation activation = Activation.newBuilder()
                .activeByDefault(true)
                .jdk("[21,)")
                .os(os)
                .property(property)
                .file(file)
                .packaging("jar")
                .condition("true")
                .build();

        assertTrue(activation.isActiveByDefault());
        assertEquals("[21,)", activation.getJdk());
        assertEquals("Linux", activation.getOs().getName());
        assertEquals("unix", activation.getOs().getFamily());
        assertEquals("amd64", activation.getOs().getArch());
        assertEquals("6", activation.getOs().getVersion());
        assertEquals("environment", activation.getProperty().getName());
        assertEquals("ci", activation.getProperty().getValue());
        assertEquals("pom.xml", activation.getFile().getExists());
        assertNull(activation.getFile().getMissing());
        assertEquals("jar", activation.getPackaging());
        assertEquals("true", activation.getCondition());

        Activation modified = activation.withActiveByDefault(false);
        assertFalse(modified.isActiveByDefault());
        assertTrue(activation.isActiveByDefault());
        assertSame(os, modified.getOs());
        assertSame(property, modified.getProperty());
        assertSame(file, modified.getFile());
        assertEquals(activation.getJdk(), modified.getJdk());
        assertEquals(activation.getPackaging(), modified.getPackaging());
    }

    @Test
    void derivesIndependentNestedCriteria() {
        ActivationProperty original = ActivationProperty.newBuilder().name("mode").value("fast").build();
        ActivationProperty updated = original.withValue("full");
        ActivationFile missing = ActivationFile.newBuilder().missing("build-marker").build();
        ActivationOS unix = ActivationOS.newBuilder().family("unix").arch("amd64").build();
        ActivationOS arm = unix.withArch("aarch64");

        assertEquals("fast", original.getValue());
        assertEquals("full", updated.getValue());
        assertEquals("mode", updated.getName());
        assertEquals("build-marker", missing.getMissing());
        assertNull(missing.getExists());
        assertEquals("amd64", unix.getArch());
        assertEquals("aarch64", arm.getArch());
        assertEquals("unix", arm.getFamily());
        assertFalse(Activation.newInstance().isActiveByDefault());
    }
}
