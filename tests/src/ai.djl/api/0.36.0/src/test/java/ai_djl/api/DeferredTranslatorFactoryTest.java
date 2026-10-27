/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ai_djl.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.translate.DeferredTranslatorFactory;
import ai.djl.translate.NoopServingTranslatorFactory;
import ai.djl.translate.Translator;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class DeferredTranslatorFactoryTest {
    @Test
    void createsTranslatorFromConfiguredFactory() throws Exception {
        Map<String, String> arguments = Map.of(
                "translatorFactory", NoopServingTranslatorFactory.class.getName());

        Translator<Input, Output> translator = new DeferredTranslatorFactory()
                .newInstance(Input.class, Output.class, null, arguments);

        assertThat(translator).isNotNull();
    }
}
