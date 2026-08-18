/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import java.util.ArrayList;

import org.eclipse.collections.api.block.function.Function;
import org.eclipse.collections.impl.block.factory.Functions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionsInnerClassForNameFunctionTest {

    @Test
    void resolvesClassNamesThroughFactoryFunction() {
        Function<String, Class<?>> classForName = Functions.classForName();

        Class<?> resolvedClass = classForName.valueOf("java.util.ArrayList");

        assertThat(resolvedClass).isEqualTo(ArrayList.class);
    }
}
