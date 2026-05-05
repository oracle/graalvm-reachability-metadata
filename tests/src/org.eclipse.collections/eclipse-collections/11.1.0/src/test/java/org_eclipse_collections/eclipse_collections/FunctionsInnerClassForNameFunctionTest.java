/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.eclipse.collections.api.block.function.Function;
import org.eclipse.collections.impl.block.factory.Functions;
import org.junit.jupiter.api.Test;

public class FunctionsInnerClassForNameFunctionTest {
    @Test
    void classForNameLoadsNamedClass() {
        final Function<String, Class<?>> classForName = Functions.classForName();

        assertThat(classForName.valueOf("java.lang.String")).isSameAs(String.class);
    }

    @Test
    void classForNameWrapsMissingClassException() {
        final Function<String, Class<?>> classForName = Functions.classForName();

        assertThatThrownBy(() -> classForName.valueOf("example.missing.Type"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Checked exception caught in Function")
                .hasCauseInstanceOf(ClassNotFoundException.class);
    }
}
