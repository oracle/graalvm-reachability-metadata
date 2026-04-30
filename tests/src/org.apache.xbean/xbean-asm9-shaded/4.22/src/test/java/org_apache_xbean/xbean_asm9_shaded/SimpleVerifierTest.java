/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_asm9_shaded;

import org.apache.xbean.asm9.Opcodes;
import org.apache.xbean.asm9.commons.AnalyzerAdapter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleVerifierTest {
    @Test
    void initializesLocalsFromMethodDescriptor() {
        AnalyzerAdapter analyzer = new AnalyzerAdapter(
                "example/Owner",
                Opcodes.ACC_PUBLIC,
                "collect",
                "(ILjava/lang/String;[Ljava/lang/Object;)V",
                null);

        assertThat(analyzer.locals)
                .containsExactly("example/Owner", Opcodes.INTEGER, "java/lang/String", "[Ljava/lang/Object;");
        assertThat(analyzer.stack).isEmpty();
    }

    @Test
    void tracksObjectArrayLoadsAndStores() {
        AnalyzerAdapter analyzer = new AnalyzerAdapter(
                "example/Owner",
                Opcodes.ACC_PUBLIC,
                "readElement",
                "([Ljava/lang/String;)Ljava/lang/String;",
                null);

        analyzer.visitVarInsn(Opcodes.ALOAD, 1);
        analyzer.visitInsn(Opcodes.ICONST_0);
        analyzer.visitInsn(Opcodes.AALOAD);

        assertThat(analyzer.stack).containsExactly("java/lang/String");

        analyzer.visitVarInsn(Opcodes.ASTORE, 2);

        assertThat(analyzer.stack).isEmpty();
        assertThat(analyzer.locals).containsExactly("example/Owner", "[Ljava/lang/String;", "java/lang/String");
    }
}
