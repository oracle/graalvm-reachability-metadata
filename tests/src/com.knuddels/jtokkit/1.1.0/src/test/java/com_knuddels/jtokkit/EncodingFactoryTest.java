/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_knuddels.jtokkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import org.junit.jupiter.api.Test;

public class EncodingFactoryTest {
    @Test
    void defaultRegistryLoadsEveryBuiltInEncodingResource() {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();

        for (EncodingType encodingType : EncodingType.values()) {
            Encoding encoding = registry.getEncoding(encodingType);
            IntArrayList tokens = encoding.encodeOrdinary("Hello, JTokkit!");

            assertThat(encoding.getName()).isEqualTo(encodingType.getName());
            assertThat(tokens.size()).isPositive();
            assertThat(encoding.decode(tokens)).isEqualTo("Hello, JTokkit!");
        }
    }
}
