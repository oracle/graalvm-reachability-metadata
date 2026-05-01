/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_razorvine.pyrolite.objects;

import net.razorvine.pickle.objects.ExceptionConstructor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExceptionConstructorTest {
    @Test
    void constructsExceptionAndStoresPythonExceptionType() {
        ExceptionConstructor constructor = new ExceptionConstructor(RemoteProblem.class, "pickle", "RemoteProblem");

        Object value = constructor.construct(new Object[]{"boom"});

        assertThat(value).isInstanceOfSatisfying(RemoteProblem.class, problem -> {
            assertThat(problem).hasMessage("[pickle.RemoteProblem] boom");
            assertThat(problem.pythonExceptionType).isEqualTo("pickle.RemoteProblem");
        });
    }

    public static class RemoteProblem extends RuntimeException {
        public String pythonExceptionType;

        public RemoteProblem(String message) {
            super(message);
        }
    }
}
