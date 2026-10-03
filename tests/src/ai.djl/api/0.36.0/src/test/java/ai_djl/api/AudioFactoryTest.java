/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ai_djl.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.djl.modality.audio.Audio;
import ai.djl.modality.audio.AudioFactory;
import org.junit.jupiter.api.Test;

public class AudioFactoryTest {
    @Test
    void createsAudioThroughDiscoveredFactory() {
        AudioFactory factory = AudioFactory.newInstance()
                .setChannels(1)
                .setSampleRate(16_000)
                .setSampleFormat(16);
        Audio audio = factory.fromData(new float[] {0.25f, -0.5f});

        assertThat(factory.getChannels()).isEqualTo(1);
        assertThat(factory.getSampleRate()).isEqualTo(16_000);
        assertThat(factory.getSampleFormat()).isEqualTo(16);
        assertThat(audio.getData()).containsExactly(0.25f, -0.5f);
    }
}
