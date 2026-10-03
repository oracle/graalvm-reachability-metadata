/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ai_djl.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.djl.engine.rpc.RpcTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.metric.Metrics;
import ai.djl.modality.Output;
import ai.djl.modality.cv.translator.Sam2Translator.Sam2Input;
import ai.djl.ndarray.NDList;
import ai.djl.translate.Translator;
import ai.djl.util.passthrough.PassthroughNDManager;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class RpcTranslatorFactoryInnerDefaultTypeConverterTest {
    private static final String IMAGE_DATA_URI =
            "data:image/png;base64,"
                    + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADUlEQVR42mNk+M/wHwAF/gL+U6kBAAAAAElFTkSuQmCC";

    @Test
    void convertsSuccessfulJsonOutputUsingDiscoveredConverterMethods() throws Exception {
        Translator<String, Sam2Input> translator = new RpcTranslatorFactory()
                .newInstance(String.class, Sam2Input.class, null, Map.of("djl_rpc_uri", "http://localhost"));
        Output output = new Output(200, "ok");
        output.add("{\"image\":\"" + IMAGE_DATA_URI
                + "\",\"prompt\":[{\"type\":\"point\",\"data\":[0,0],\"label\":1}]}");

        try (Predictor.PredictorContext context = new Predictor.PredictorContext(
                null, PassthroughNDManager.INSTANCE, new Metrics())) {
            context.setAttachment("output", output);
            Sam2Input converted = translator.processOutput(context, new NDList());

            assertThat(converted.getImage()).isNotNull();
            assertThat(converted.getPoints()).hasSize(1);
        }
    }
}
