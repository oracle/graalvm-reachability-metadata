/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.model.Model;
import ch.qos.logback.core.model.SerializeModelModel;
import ch.qos.logback.core.model.processor.ModelInterpretationContext;
import ch.qos.logback.core.model.processor.SerializeModelModelHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializeModelModelHandlerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void handleSerializesTopModelToConfiguredFile() throws Exception {
        ContextBase context = new ContextBase();
        ModelInterpretationContext modelInterpretationContext = new ModelInterpretationContext(context);
        Model topModel = new Model();
        topModel.setTag("configuration");
        topModel.setLineNumber(1);
        modelInterpretationContext.setTopModel(topModel);

        Path modelFile = temporaryDirectory.resolve("serialized-logback-model.scmo");
        SerializeModelModel serializeModelModel = new SerializeModelModel();
        serializeModelModel.setFile(modelFile.toString());

        SerializeModelModelHandler handler = new SerializeModelModelHandler(context);
        handler.handle(modelInterpretationContext, serializeModelModel);

        assertThat(modelFile).exists();
        assertThat(Files.size(modelFile)).isGreaterThan(0L);
    }
}
