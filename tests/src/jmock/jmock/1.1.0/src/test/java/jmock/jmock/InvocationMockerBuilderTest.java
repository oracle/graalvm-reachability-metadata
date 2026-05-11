/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jmock.jmock;

import junit.framework.AssertionFailedError;
import org.jmock.builder.ArgumentsMatchBuilder;
import org.jmock.builder.BuilderNamespace;
import org.jmock.builder.InvocationMockerBuilder;
import org.jmock.builder.MatchBuilder;
import org.jmock.core.InvocationMatcher;
import org.jmock.core.Stub;
import org.jmock.core.StubMatchersCollection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InvocationMockerBuilderTest {
    @Test
    void methodAcceptsObjectMethodNamesForInterfaceMocks() {
        RecordingStubMatchersCollection mocker = new RecordingStubMatchersCollection();
        RecordingBuilderNamespace namespace = new RecordingBuilderNamespace();
        InvocationMockerBuilder builder = new InvocationMockerBuilder(
                mocker, namespace, NoDeclaredMethods.class);

        ArgumentsMatchBuilder returnedBuilder = builder.method("toString");

        assertThat(returnedBuilder).isSameAs(builder);
        assertThat(mocker.matcherCount()).isEqualTo(1);
        assertThat(namespace.methodName()).isEqualTo("toString");
        assertThat(namespace.methodBuilder()).isSameAs(builder);
    }

    @Test
    void methodRejectsNamesNotDeclaredByMockedTypeOrObject() {
        RecordingStubMatchersCollection mocker = new RecordingStubMatchersCollection();
        RecordingBuilderNamespace namespace = new RecordingBuilderNamespace();
        InvocationMockerBuilder builder = new InvocationMockerBuilder(
                mocker, namespace, NoDeclaredMethods.class);

        assertThatThrownBy(() -> builder.method("missingMethod"))
                .isInstanceOf(AssertionFailedError.class)
                .hasMessageContaining("no method named missingMethod");

        assertThat(mocker.matcherCount()).isZero();
        assertThat(namespace.methodName()).isNull();
    }

    private interface NoDeclaredMethods {
    }

    private static final class RecordingStubMatchersCollection implements StubMatchersCollection {
        private int matcherCount;

        public void setName(String name) {
        }

        public void addMatcher(InvocationMatcher matcher) {
            matcherCount++;
        }

        public void setStub(Stub stub) {
        }

        int matcherCount() {
            return matcherCount;
        }
    }

    private static final class RecordingBuilderNamespace implements BuilderNamespace {
        private String methodName;
        private MatchBuilder methodBuilder;

        public MatchBuilder lookupID(String id) {
            throw new AssertionError("lookupID is not used by method name registration");
        }

        public void registerMethodName(String id, MatchBuilder invocation) {
            methodName = id;
            methodBuilder = invocation;
        }

        public void registerUniqueID(String id, MatchBuilder invocation) {
            throw new AssertionError("registerUniqueID is not used by method name registration");
        }

        String methodName() {
            return methodName;
        }

        MatchBuilder methodBuilder() {
            return methodBuilder;
        }
    }
}
