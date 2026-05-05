/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_test.arquillian_test_spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.jboss.arquillian.test.spi.ExceptionProxy;
import org.junit.jupiter.api.Test;

public class ExceptionProxyTest {
    @Test
    void serializesAndRestoresOriginalThrowable() throws Exception {
        IllegalArgumentException original = new IllegalArgumentException("invalid deployment archive");
        original.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("DeploymentVerifier", "verify", "DeploymentVerifier.java", 42)
        });
        ExceptionProxy proxy = ExceptionProxy.createForException(original);

        ExceptionProxy restoredProxy = serializeAndDeserialize(proxy);
        Throwable restored = restoredProxy.createException();

        assertThat(restored).isExactlyInstanceOf(IllegalArgumentException.class);
        assertThat(restored).hasMessage("invalid deployment archive");
        assertThat(restored.getStackTrace()).containsExactly(original.getStackTrace());
    }

    private static ExceptionProxy serializeAndDeserialize(ExceptionProxy proxy) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(proxy);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (ExceptionProxy) input.readObject();
        }
    }
}
