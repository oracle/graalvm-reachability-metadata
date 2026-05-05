/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_core;

import org.apache.maven.usability.plugin.ExpressionDocumenter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExpressionDocumenterTest {
    @Test
    public void loadReadsBundledExpressionDocumentation() throws Exception {
        Map<?, ?> documentation = ExpressionDocumenter.load();

        assertNotNull(documentation);
        assertFalse(documentation.isEmpty());
        assertTrue(documentation.containsKey("project.artifact"));
        assertTrue(documentation.containsKey("settings.offline"));
        assertTrue(documentation.containsKey("localRepository"));
    }
}
