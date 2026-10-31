/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.springframework.data.util.MethodInvocationRecorder;
import org.springframework.data.util.MethodInvocationRecorder.Recorded;

public class MethodInvocationRecorderInnerRecordingMethodInterceptorTest {

    @Test
    void objectMethodDoesNotReplaceRecordedPropertyPath() {
        Recorded<Person> person = MethodInvocationRecorder.forProxyOf(Person.class);
        Recorded<String> name = person.record((Function<Person, String>) value -> {
            assertThat(value.toString()).isNotBlank();
            return value.getName();
        });

        assertThat(name.getPropertyPath()).contains("name");
        assertThat(person.toString()).contains("Recorded");
        assertThat(name.getPropertyPath()).contains("name");
    }

    public interface Person {

        String getName();
    }
}
