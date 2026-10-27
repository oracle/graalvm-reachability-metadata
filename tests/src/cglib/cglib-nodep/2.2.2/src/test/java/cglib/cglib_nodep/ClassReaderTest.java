/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import net.sf.cglib.asm.ClassReader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassReaderTest {
    @Test
    void readsClassMetadataByBinaryName() throws Exception {
        ClassReader reader = new ClassReader(ClassReader.class.getName());

        assertThat(reader.getClassName()).isEqualTo("net/sf/cglib/asm/ClassReader");
    }
}
