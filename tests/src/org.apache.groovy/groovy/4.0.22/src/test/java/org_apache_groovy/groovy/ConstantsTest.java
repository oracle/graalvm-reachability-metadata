/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovyjarjarasm.asm.ClassVisitor;
import groovyjarjarasm.asm.Opcodes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConstantsTest {
    @Test
    void requiresPreviewBytecodeForAnExperimentalVisitor() {
        IllegalStateException failure = assertThrows(IllegalStateException.class, ExperimentalVisitor::new);
        assertTrue(failure.getMessage().contains("can only be used by classes compiled with --enable-preview"));
    }

    public static class ExperimentalVisitor extends ClassVisitor {
        public ExperimentalVisitor() {
            super(Opcodes.ASM10_EXPERIMENTAL);
        }
    }
}
