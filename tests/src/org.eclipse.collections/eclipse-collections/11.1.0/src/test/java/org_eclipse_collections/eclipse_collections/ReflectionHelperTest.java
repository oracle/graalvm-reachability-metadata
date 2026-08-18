/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import java.lang.reflect.Constructor;

import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.utility.internal.ReflectionHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionHelperTest {

    @Test
    void createsFastListWithDefaultConstructor() {
        assertThat(ReflectionHelper.hasDefaultConstructor(FastList.class)).isTrue();

        FastList<?> list = ReflectionHelper.newInstance(FastList.class);

        assertThat(list).isEmpty();
    }

    @Test
    void createsFastListWithExactConstructorMatch() {
        Constructor<FastList> constructor = ReflectionHelper.getConstructor(FastList.class, int.class);

        FastList<?> list = ReflectionHelper.newInstance(constructor, 4);

        assertThat(list).isEmpty();
    }

    @Test
    void createsFastListWithCompatibleConstructorMatch() {
        Constructor<FastList> constructor = ReflectionHelper.getConstructor(FastList.class, Integer.class);

        FastList<?> list = ReflectionHelper.newInstance(constructor, Integer.valueOf(4));

        assertThat(list).isEmpty();
    }
}
