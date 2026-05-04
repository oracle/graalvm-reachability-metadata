/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_paranamer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.module.paranamer.shaded.AdaptiveParanamer;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

public class AdaptiveParanamerTest {
    @Test
    void defaultConstructorIncludesReflectionBasedParameterNameLookup() throws Exception {
        AdaptiveParanamer paranamer = new AdaptiveParanamer();
        Constructor<ConstructorParameterFixture> constructor = ConstructorParameterFixture.class.getDeclaredConstructor(
                String.class, int.class);

        String[] parameterNames = paranamer.lookupParameterNames(constructor, false);

        assertThat(parameterNames).containsExactly("text", "repeatCount");
    }

    public record ConstructorParameterFixture(String text, int repeatCount) {
    }
}
