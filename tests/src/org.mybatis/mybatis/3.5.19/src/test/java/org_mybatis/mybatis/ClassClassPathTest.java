/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.io.DataInputStream;
import java.io.InputStream;

import org.apache.ibatis.javassist.ClassClassPath;
import org.apache.ibatis.javassist.bytecode.ClassFile;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ClassClassPathTest {
    @Test
    void locatesAndReadsExistingClassBytecode() throws Exception {
        ClassClassPath path = new ClassClassPath(Configuration.class);
        String name = "org.apache.ibatis.session.Configuration";
        assertNotNull(path.find(name));
        try (InputStream stream = path.openClassfile(name);
                DataInputStream input = new DataInputStream(stream)) {
            assertEquals(name, new ClassFile(input).getName());
        }
    }
}
