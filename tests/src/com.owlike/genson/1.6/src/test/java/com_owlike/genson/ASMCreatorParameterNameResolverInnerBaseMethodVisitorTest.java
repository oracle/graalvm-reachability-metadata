/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_owlike.genson;

import static org.assertj.core.api.Assertions.assertThat;

import com.owlike.genson.Genson;
import com.owlike.genson.GensonBuilder;
import org.junit.jupiter.api.Test;

public class ASMCreatorParameterNameResolverInnerBaseMethodVisitorTest {
    @Test
    void resolvesArrayMethodParameterWhileBuildingGensonDescriptor() {
        Genson debugInfoGenson = new GensonBuilder().useConstructorWithArguments(true).create();

        Genson deserializedGenson = debugInfoGenson.deserialize("{}", Genson.class);
        String[] values = deserializedGenson.deserialize("[\"alpha\",\"beta\"]", String[].class);

        assertThat(values).containsExactly("alpha", "beta");
    }
}
