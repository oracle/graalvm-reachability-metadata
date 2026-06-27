/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jruby_jcodings.jcodings;

import java.nio.charset.StandardCharsets;

import org.jcodings.transcode.AsciiCompatibility;
import org.jcodings.transcode.Transcoder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TranscoderTest {
    @Test
    void loadsSpecificTranscoderByImplementationClassName() {
        Transcoder transcoder = Transcoder.load("org.jcodings.transcode.specific.From_UTF_16BE_Transcoder");

        assertThat(new String(transcoder.getSource(), StandardCharsets.US_ASCII)).isEqualTo("UTF-16BE");
        assertThat(new String(transcoder.getDestination(), StandardCharsets.US_ASCII)).isEqualTo("UTF-8");
        assertThat(transcoder.toString()).isEqualTo("UTF-16BE => UTF-8");
        assertThat(transcoder.inputUnitLength).isEqualTo(2);
        assertThat(transcoder.maxInput).isEqualTo(4);
        assertThat(transcoder.maxOutput).isEqualTo(4);
        assertThat(transcoder.compatibility).isSameAs(AsciiCompatibility.DECODER);
        assertThat(transcoder.transcoding(0).toString()).isEqualTo("Transcoding for transcoder UTF-16BE => UTF-8");
    }
}
