/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_paranamer.paranamer;

import static org.assertj.core.api.Assertions.assertThat;

import com.thoughtworks.paranamer.DefaultParanamer;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

public class DefaultParanamerTest {
    @Test
    void readsParameterNamesFromReflectionMetadata() throws Exception {
        DefaultParanamer paranamer = new DefaultParanamer();
        Constructor<ConstructorParameterFixture> constructor = ConstructorParameterFixture.class.getDeclaredConstructor(
                String.class, int.class);

        String[] parameterNames = paranamer.lookupParameterNames(constructor);

        assertThat(parameterNames).containsExactly("text", "repeatCount");
    }

    public record ConstructorParameterFixture(String text, int repeatCount) {
    }
}
