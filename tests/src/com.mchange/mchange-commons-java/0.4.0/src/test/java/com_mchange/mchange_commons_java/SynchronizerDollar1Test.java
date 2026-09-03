/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v1.lang.Synchronizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SynchronizerDollar1Test {
    @Test
    void createSynchronizedWrapperDelegatesInterfaceMethodCallsToWrappedObject() {
        EchoServiceImpl target = new EchoServiceImpl();

        EchoService wrapper = (EchoService) Synchronizer.createSynchronizedWrapper(target);

        assertThat(wrapper).isNotSameAs(target);
        assertThat(wrapper).isInstanceOf(EchoService.class);
        assertThat(wrapper.echo("hello")).isEqualTo("hello-1");
        assertThat(target.invocations()).isEqualTo(1);
    }

    public interface EchoService {
        String echo(String value);
    }

    public static final class EchoServiceImpl implements EchoService {
        private int invocations;

        @Override
        public String echo(String value) {
            invocations++;
            return value + "-" + invocations;
        }

        public int invocations() {
            return invocations;
        }
    }
}
