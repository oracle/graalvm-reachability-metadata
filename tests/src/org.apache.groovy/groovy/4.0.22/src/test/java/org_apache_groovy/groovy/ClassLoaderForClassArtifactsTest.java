/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.reflection.ClassLoaderForClassArtifacts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ClassLoaderForClassArtifactsTest {
    @Test
    void resolvesAnExistingOwnerAndAllocatesDistinctArtifactNames() throws Exception {
        ClassLoaderForClassArtifacts loader = new ClassLoaderForClassArtifacts(ClassLoaderForClassArtifactsTest.class);
        assertSame(getClass(), loader.loadClass(getClass().getName()));
        assertEquals(getClass().getName() + "$label", loader.createClassName("label"));
        assertEquals(getClass().getName() + "$label$0", loader.createClassName("label"));
    }
}
