/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jdt_core_compiler.ecj;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.jdt.internal.compiler.ReadManager;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.junit.jupiter.api.Test;

public class ReadManagerTest {
    @Test
    void createsReadManagerAndFallsBackToDirectContentReadForUnmanagedUnit() {
        final TestCompilationUnit unit = new TestCompilationUnit("ManagedSource", "class ManagedSource {}");
        final ReadManager readManager = new ReadManager(new ICompilationUnit[0], 0);
        try {
            assertThat(readManager.getContents(unit)).isEqualTo(unit.getContents());
        } finally {
            readManager.shutdown();
        }
    }

    private static final class TestCompilationUnit implements ICompilationUnit {
        private final char[] mainTypeName;
        private final char[] contents;
        private final char[] fileName;

        private TestCompilationUnit(String mainTypeName, String source) {
            this.mainTypeName = mainTypeName.toCharArray();
            this.contents = source.toCharArray();
            this.fileName = (mainTypeName + ".java").toCharArray();
        }

        @Override
        public char[] getContents() {
            return this.contents;
        }

        @Override
        public char[] getMainTypeName() {
            return this.mainTypeName;
        }

        @Override
        public char[][] getPackageName() {
            return new char[0][];
        }

        @Override
        public boolean ignoreOptionalProblems() {
            return false;
        }

        @Override
        public char[] getFileName() {
            return this.fileName;
        }
    }
}
