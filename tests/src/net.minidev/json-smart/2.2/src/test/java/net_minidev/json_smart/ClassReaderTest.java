/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.json_smart;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import net.minidev.json.JSONValue;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

public class ClassReaderTest {
    @Test
    void loadsJsonSmartClassBytesFromSystemClassLoaderResource() throws IOException {
        ClassReader reader = new ClassReader(JSONValue.class.getName());

        assertThat(reader.getClassName()).isEqualTo("net/minidev/json/JSONValue");
        assertThat(reader.getSuperName()).isEqualTo("java/lang/Object");
    }
}
