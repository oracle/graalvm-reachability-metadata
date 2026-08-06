/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jffi;

import com.kenai.jffi.Type;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ComKenaiJffiInitTest {
    @Test
    void loadsPackagedNativeStubAndResolvesBuiltinType() {
        assertThat(Type.SINT32.size()).isEqualTo(Integer.BYTES);
        assertThat(Type.SINT32.alignment()).isPositive();
    }
}
