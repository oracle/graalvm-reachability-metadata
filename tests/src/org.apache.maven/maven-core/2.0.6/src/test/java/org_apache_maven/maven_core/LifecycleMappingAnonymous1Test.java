/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_core;

import org.apache.maven.lifecycle.mapping.LifecycleMapping;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LifecycleMappingAnonymous1Test {
    @Test
    public void roleNameIsDerivedFromLifecycleMappingInterfaceClass() {
        assertEquals("org.apache.maven.lifecycle.mapping.LifecycleMapping", LifecycleMapping.ROLE);
    }
}
