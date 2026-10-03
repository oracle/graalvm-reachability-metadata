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
import ai.djl.inference.streaming.ChunkedBytesSupplier;
import ai.djl.metric.Metrics;
import ai.djl.modality.Output;
import ai.djl.modality.cv.translator.Sam2Translator.Sam2Input;
import ai.djl.ndarray.NDList;
import ai.djl.translate.Translator;
import ai.djl.util.passthrough.PassthroughNDManager;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class RpcTranslatorFactoryInnerDefaultTypeConverterTest {
    private static final String IMAGE_DATA_URI =
            "data:image/png;base64,"
                    + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGNgYGAAAAAEAAH2FzhVAAAAAElFTkSuQmCC";

    @Test
    void convertsSuccessfulJsonOutputUsingDiscoveredConverterMethods() throws Exception {
        Translator<String, Sam2Input> translator = new RpcTranslatorFactory()
                .newInstance(String.class, Sam2Input.class, null, Map.of("djl_rpc_uri", "file:///unused"));
        Output output = new Output(200, "ok");
        output.add("{\"image_url\":\"" + IMAGE_DATA_URI
                + "\",\"prompt\":[{\"type\":\"point\",\"data\":[0,0],\"label\":1}]}");

        try (Predictor.PredictorContext context = new Predictor.PredictorContext(
                null, PassthroughNDManager.INSTANCE, new Metrics())) {
            context.setAttachment("output", output);
            Sam2Input converted = translator.processOutput(context, new NDList());

            assertThat(converted.getImage()).isNotNull();
            assertThat(converted.getPoints()).hasSize(1);
        }
    }

    @Test
    void convertsChunkedOutputUsingIteratorFactoryMethod() throws Exception {
        Translator<String, IteratorJsonOutput> translator = new RpcTranslatorFactory()
                .newInstance(String.class, IteratorJsonOutput.class, null,
                        Map.of("djl_rpc_uri", "file:///unused"));
        ChunkedBytesSupplier chunks = new ChunkedBytesSupplier();
        chunks.appendContent("chunked-value".getBytes(StandardCharsets.UTF_8), true);
        Output output = new Output(200, "ok");
        output.add(chunks);

        try (Predictor.PredictorContext context = new Predictor.PredictorContext(
                null, PassthroughNDManager.INSTANCE, new Metrics())) {
            context.setAttachment("output", output);
            IteratorJsonOutput converted = translator.processOutput(context, new NDList());

            assertThat(converted.value()).isEqualTo("chunked-value");
        }
    }

    public static final class IteratorJsonOutput {
        private final String value;

        private IteratorJsonOutput(String value) {
            this.value = value;
        }

        public static IteratorJsonOutput fromJson(Iterator<String> values) {
            return new IteratorJsonOutput(values.next());
        }

        public String value() {
            return value;
        }
    }
}
