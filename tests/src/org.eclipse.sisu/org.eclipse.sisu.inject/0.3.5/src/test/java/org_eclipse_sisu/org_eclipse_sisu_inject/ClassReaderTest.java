/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_inject;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.sisu.space.asm.ClassReader;
import org.junit.jupiter.api.Test;

public class ClassReaderTest {
    @Test
    void readsNamedClassFromSystemClassLoaderResource() throws Exception {
        ClassReader reader = new ClassReader("org.eclipse.sisu.space.asm.ClassReader");

        assertThat(reader.getClassName()).isEqualTo("org/eclipse/sisu/space/asm/ClassReader");
        assertThat(reader.getSuperName()).isEqualTo("java/lang/Object");
    }
}
