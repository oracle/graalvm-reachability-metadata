/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.spel.spi.Function;
import org.springframework.data.util.Optionals;

public class FunctionTest {

    @Test
    void invokesMethodWhenArgumentsAlreadyMatchParameterCount() throws Exception {
        Method method = Optionals.class.getMethod("isAnyPresent", Optional[].class);
        Function function = new Function(method);

        Object result = function.invoke(new Object[] {new Optional<?>[] {Optional.empty()}});

        assertThat(result).isEqualTo(false);
    }

    @Test
    void packsSingleVarargsParameterBeforeInvokingMethod() throws Exception {
        Method method = Optionals.class.getMethod("isAnyPresent", Optional[].class);
        Function function = new Function(method);

        Object result = function.invoke(new Object[] {Optional.empty(), Optional.of("spring-data")});

        assertThat(result).isEqualTo(true);
    }
}
