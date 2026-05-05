/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.codemodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.fmt.JSerializedObject;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JSerializedObjectTest {
    @TempDir
    Path outputDirectory;

    @Test
    void packageBuildSerializesObjectResource() throws IOException, ClassNotFoundException {
        JCodeModel codeModel = new JCodeModel();
        JPackage generatedPackage = codeModel._package("generated.example");
        generatedPackage.addResourceFile(new JSerializedObject("message.ser", "hello codemodel"));

        File output = outputDirectory.toFile();
        codeModel.build(output, output, null);

        Path serializedFile = outputDirectory.resolve("generated/example/message.ser");
        assertThat(serializedFile).exists();
        assertThat(readObject(serializedFile)).isEqualTo("hello codemodel");
    }

    private Object readObject(Path serializedFile) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(serializedFile))) {
            return input.readObject();
        }
    }
}
