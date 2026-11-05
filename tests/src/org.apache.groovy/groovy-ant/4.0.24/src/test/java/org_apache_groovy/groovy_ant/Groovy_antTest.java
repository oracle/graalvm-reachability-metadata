/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_ant;

import groovy.ant.AntBuilder;
import org.apache.tools.ant.taskdefs.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

@Timeout(30)
public class Groovy_antTest {
    @Test
    void expandsPropertiesInASharedProject() {
        AntBuilder original = new AntBuilder();
        original.invokeMethod("property", new Object[]{Map.of("name", "build.message", "value", "ready")});
        AntBuilder shared = new AntBuilder(original.getProject());

        Object task = shared.invokeMethod("property", new Object[]{
                Map.of("name", "build.summary", "value", "${build.message}: packaged")
        });

        assertInstanceOf(Property.class, task);
        assertSame(original.getProject(), shared.getProject());
        assertEquals("ready: packaged", original.getProject().getProperty("build.summary"));
    }
}
