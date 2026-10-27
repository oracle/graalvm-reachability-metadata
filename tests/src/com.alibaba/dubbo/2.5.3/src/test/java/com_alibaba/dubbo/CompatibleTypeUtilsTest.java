/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.common.utils.CompatibleTypeUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import org.junit.jupiter.api.Test;

public class CompatibleTypeUtilsTest {
    @Test
    void convertsBetweenArraysAndConcreteCollections() {
        Object array =
                CompatibleTypeUtils.compatibleTypeConvert(
                        Arrays.asList("a", "b"), String[].class);
        Object list =
                CompatibleTypeUtils.compatibleTypeConvert(
                        new String[] {"a", "b"}, ArrayList.class);
        Object set =
                CompatibleTypeUtils.compatibleTypeConvert(
                        Arrays.asList("a", "b"), LinkedHashSet.class);

        assertThat((String[]) array).containsExactly("a", "b");
        assertThat(list).isEqualTo(Arrays.asList("a", "b"));
        assertThat(set).isEqualTo(new LinkedHashSet<String>(Arrays.asList("a", "b")));
    }
}
