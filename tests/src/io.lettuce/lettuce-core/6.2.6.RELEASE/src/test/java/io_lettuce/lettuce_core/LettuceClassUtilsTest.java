/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.internal.LettuceClassUtils;
import org.junit.jupiter.api.Test;

public class LettuceClassUtilsTest {
    @Test
    void loadsPresentClassesByName() {
        assertThat(LettuceClassUtils.isPresent("reactor.core.publisher.Mono")).isTrue();
        assertThat(LettuceClassUtils.findClass("java.lang.String")).isEqualTo(String.class);
    }
}
