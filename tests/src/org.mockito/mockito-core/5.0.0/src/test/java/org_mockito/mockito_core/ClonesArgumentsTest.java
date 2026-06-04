/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.ClonesArguments;
import org.mockito.invocation.Invocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

public class ClonesArgumentsTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void clonesArrayArgumentsBeforeRecordingInvocation() {
        final ArrayScoringService service = Mockito.mock(ArrayScoringService.class);
        doAnswer(new ClonesArguments()).when(service).score(any(String[].class));
        Mockito.clearInvocations(service);

        final String[] values = new String[] {"alpha", "beta"};
        final int score = service.score(values);

        assertThat(score).isZero();
        assertThat(Mockito.mockingDetails(service).getInvocations()).hasSize(1);
        final Invocation invocation =
                Mockito.mockingDetails(service).getInvocations().iterator().next();
        final Object recordedArgument = invocation.getArguments()[0];
        assertThat(recordedArgument).isInstanceOf(String[].class);
        assertThat(recordedArgument).isNotSameAs(values);
        assertThat((String[]) recordedArgument).containsExactly("alpha", "beta");
    }

    public interface ArrayScoringService {
        int score(String[] values);
    }
}
