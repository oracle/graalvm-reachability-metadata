/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.codemodel;

import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.fmt.JSerializedObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class JSerializedObjectTest {
    @TempDir
    Path tempDir;

    @Test
    void writesSerializedObjectAsPackageResource() throws Exception {
        Path sourceDirectory = Files.createDirectory(tempDir.resolve("generated-sources"));
        Path resourceDirectory = Files.createDirectory(tempDir.resolve("generated-resources"));
        JCodeModel codeModel = new JCodeModel();
        JPackage packageModel = codeModel._package("example.resources");
        packageModel.addResourceFile(new JSerializedObject("message.ser", "codemodel-payload"));

        codeModel.build(sourceDirectory.toFile(), resourceDirectory.toFile(), null);

        Path serializedFile = resourceDirectory.resolve("example/resources/message.ser");
        assertThat(serializedFile).exists();
        try (ObjectInputStream inputStream = new ObjectInputStream(Files.newInputStream(serializedFile))) {
            assertThat(inputStream.readObject()).isEqualTo("codemodel-payload");
        }
    }
}
