/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ai_djl.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.djl.BaseModel;
import ai.djl.MalformedModelException;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.translate.NoopServingTranslatorFactory;
import ai.djl.translate.ServingTranslatorFactory;
import ai.djl.translate.Translator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ServingTranslatorFactoryTest {
    @Test
    void createsTranslatorFromConfiguredFactory() throws Exception {
        Map<String, String> arguments = Map.of(
                "translatorFactory", NoopServingTranslatorFactory.class.getName());

        Translator<Input, Output> translator = new ServingTranslatorFactory()
                .newInstance(Input.class, Output.class, new TestModel(), arguments);

        assertThat(translator).isNotNull();
    }

    private static final class TestModel extends BaseModel {
        private TestModel() {
            super("translator-test-model");
        }

        @Override
        public void load(Path modelDir, String modelName, Map<String, ?> options)
                throws IOException, MalformedModelException {
            // The configured factory path does not load model contents.
        }
    }
}
