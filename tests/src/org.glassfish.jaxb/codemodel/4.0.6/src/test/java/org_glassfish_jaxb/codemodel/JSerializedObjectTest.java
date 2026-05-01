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
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JSerializedObjectTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void buildWritesSerializedResourceFile() throws Exception {
        String payload = "codemodel serialized payload";
        String resourceName = "payload.ser";
        JCodeModel codeModel = new JCodeModel();
        JPackage generatedPackage = codeModel._package("example.serialized");

        generatedPackage.addResourceFile(new JSerializedObject(resourceName, payload));

        Path sourceDirectory = Files.createDirectory(temporaryDirectory.resolve("sources"));
        Path resourceDirectory = Files.createDirectory(temporaryDirectory.resolve("resources"));
        codeModel.build(sourceDirectory.toFile(), resourceDirectory.toFile(), null);

        Path serializedFile = resourceDirectory.resolve("example").resolve("serialized").resolve(resourceName);
        assertThat(serializedFile).exists().isRegularFile();

        byte[] serializedContent = Files.readAllBytes(serializedFile);
        assertThat(serializedContent).startsWith(new byte[] {(byte) 0xAC, (byte) 0xED, 0x00, 0x05});
        assertThat(serializedContent.length).isGreaterThan(payload.length());
    }
}
