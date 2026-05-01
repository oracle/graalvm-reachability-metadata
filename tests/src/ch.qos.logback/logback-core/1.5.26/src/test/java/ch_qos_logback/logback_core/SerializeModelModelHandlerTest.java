/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.model.Model;
import ch.qos.logback.core.model.SerializeModelModel;
import ch.qos.logback.core.model.processor.ModelInterpretationContext;
import ch.qos.logback.core.model.processor.SerializeModelModelHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SerializeModelModelHandlerTest {
    private static final byte OBJECT_STREAM_MAGIC_FIRST_BYTE = (byte) 0xAC;
    private static final byte OBJECT_STREAM_MAGIC_SECOND_BYTE = (byte) 0xED;

    @TempDir
    Path temporaryDirectory;

    @Test
    void handleSerializesTopModelToConfiguredFile() throws Exception {
        ContextBase context = new ContextBase();
        ModelInterpretationContext interpretationContext = new ModelInterpretationContext(context);
        Model topModel = new Model();
        Model childModel = new Model();
        topModel.addSubModel(childModel);
        interpretationContext.setTopModel(topModel);

        Path serializedModelFile = temporaryDirectory.resolve("logback-model.ser");
        SerializeModelModel serializeModelModel = new SerializeModelModel();
        serializeModelModel.setFile(serializedModelFile.toString());

        SerializeModelModelHandler handler = new SerializeModelModelHandler(context);
        handler.handle(interpretationContext, serializeModelModel);

        byte[] serializedBytes = Files.readAllBytes(serializedModelFile);
        assertThat(serializedBytes).startsWith(OBJECT_STREAM_MAGIC_FIRST_BYTE, OBJECT_STREAM_MAGIC_SECOND_BYTE);
    }
}
