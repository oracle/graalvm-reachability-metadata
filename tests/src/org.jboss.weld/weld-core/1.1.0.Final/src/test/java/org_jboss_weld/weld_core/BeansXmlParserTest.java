/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.jboss.weld.bootstrap.spi.BeansXml;
import org.jboss.weld.bootstrap.spi.Metadata;
import org.jboss.weld.xml.BeansXmlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BeansXmlParserTest {
    @TempDir
    Path directory;

    @Test
    void parsesEnabledComponentsAndMergesDescriptors() throws Exception {
        Path first = directory.resolve("first.xml");
        Files.writeString(first, """
                <beans xmlns="http://java.sun.com/xml/ns/javaee">
                    <alternatives>
                        <class>example.Checkout</class>
                        <stereotype>example.Priority</stereotype>
                    </alternatives>
                    <decorators><class>example.AuditDecorator</class></decorators>
                    <interceptors><class>example.TraceInterceptor</class></interceptors>
                </beans>
                """);
        Path second = directory.resolve("second.xml");
        Files.writeString(second, """
                <beans xmlns="http://java.sun.com/xml/ns/javaee">
                    <alternatives><class>example.BackupCheckout</class></alternatives>
                </beans>
                """);
        BeansXmlParser parser = new BeansXmlParser();
        BeansXml parsed = parser.parse(first.toUri().toURL());
        assertThat(parsed.getEnabledAlternativeClasses()).extracting(Metadata::getValue)
                .containsExactly("example.Checkout");
        assertThat(parsed.getEnabledAlternativeStereotypes()).extracting(Metadata::getValue)
                .containsExactly("example.Priority");
        assertThat(parsed.getEnabledDecorators()).extracting(Metadata::getValue)
                .containsExactly("example.AuditDecorator");
        assertThat(parsed.getEnabledInterceptors()).extracting(Metadata::getValue)
                .containsExactly("example.TraceInterceptor");
        BeansXml merged = parser.parse(List.of(first.toUri().toURL(), second.toUri().toURL()));
        assertThat(merged.getEnabledAlternativeClasses()).extracting(Metadata::getValue)
                .containsExactly("example.Checkout", "example.BackupCheckout");
        assertThat(merged.getEnabledDecorators()).extracting(Metadata::getValue)
                .containsExactly("example.AuditDecorator");
    }
}
