/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.core.util.OptionHelper;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import org.junit.jupiter.api.Test;

public class OptionHelperTest {

    @Test
    void instantiatesClassWithDefaultConstructorFromItsName() throws Exception {
        Object instance = OptionHelper.instantiateByClassNameAndParameter(
                DefaultConstructorInstantiable.class.getName(),
                TestInstantiable.class,
                OptionHelperTest.class.getClassLoader(),
                null,
                null);

        assertThat(instance)
                .isInstanceOf(DefaultConstructorInstantiable.class)
                .isInstanceOf(TestInstantiable.class);
    }

    @Test
    void instantiatesClassWithMatchingConstructorParameterFromItsName() throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        ParameterizedInstantiable instance = (ParameterizedInstantiable) OptionHelper.instantiateByClassNameAndParameter(
                ParameterizedInstantiable.class.getName(),
                TestInstantiable.class,
                OptionHelperTest.class.getClassLoader(),
                OutputStream.class,
                stream);

        assertThat(instance.getOutputStream()).isSameAs(stream);
    }

    public interface TestInstantiable {
    }

    public static final class DefaultConstructorInstantiable implements TestInstantiable {

        public DefaultConstructorInstantiable() {
        }
    }

    public static final class ParameterizedInstantiable implements TestInstantiable {

        private final OutputStream outputStream;

        public ParameterizedInstantiable(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        public OutputStream getOutputStream() {
            return outputStream;
        }
    }
}
