/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.URL;

import javassist.ClassClassPath;

import org.junit.jupiter.api.Test;

public class ClassClassPathTest {
    private static final String FIXTURE_CLASS_NAME = ClassClassPathFixture.class.getName();
    private static final int CLASS_FILE_MAGIC_FIRST_BYTE = 0xCA;

    @Test
    void openClassfileReadsClassResourceRelativeToAnchorClass() throws Exception {
        ClassClassPath classPath = new ClassClassPath(ClassClassPathTest.class);

        try (InputStream classfile = classPath.openClassfile(FIXTURE_CLASS_NAME)) {
            assertThat(classfile).isNotNull();
            assertThat(classfile.read()).isEqualTo(CLASS_FILE_MAGIC_FIRST_BYTE);
        }
    }

    @Test
    void findReturnsClassResourceUrlRelativeToAnchorClass() throws Exception {
        ClassClassPath classPath = new ClassClassPath(ClassClassPathTest.class);

        URL classResource = classPath.find(FIXTURE_CLASS_NAME);

        assertThat(classResource).isNotNull();
        try (InputStream classfile = classResource.openStream()) {
            assertThat(classfile.read()).isEqualTo(CLASS_FILE_MAGIC_FIRST_BYTE);
        }
    }

    public static class ClassClassPathFixture {
    }
}
