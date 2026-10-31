/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovyjarjarasm.asm.ClassReader;
import groovy.lang.Reference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClassReaderTest {
    @Test
    void readsClassInformationFromAClasspathClassFile() throws Exception {
        ClassReader reader = new ClassReader(Reference.class.getName());
        assertEquals("groovy/lang/Reference", reader.getClassName());
        assertEquals("groovy/lang/GroovyObjectSupport", reader.getSuperName());
        assertEquals("java/io/Serializable", reader.getInterfaces()[0]);
    }
}
