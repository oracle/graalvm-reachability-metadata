/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.util.MethodInvocationRecorder;
import org.springframework.data.util.MethodInvocationRecorder.Recorded;

public class MethodInvocationRecorderInnerRecordingMethodInterceptorTest {

    @Test
    void invokesObjectMethodOnRecordingInterceptorWithoutRecordingPropertyPath() {
        Recorded<Person> recorded = MethodInvocationRecorder.forProxyOf(Person.class);

        Recorded<String> objectMethodResult = recorded.record(Object::toString);

        assertThat(objectMethodResult.getPropertyPath()).isEmpty();
    }

    public interface Person {

        String getName();
    }
}
