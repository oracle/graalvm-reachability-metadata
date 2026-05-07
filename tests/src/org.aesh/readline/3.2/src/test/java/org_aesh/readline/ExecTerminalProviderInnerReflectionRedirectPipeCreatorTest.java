/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.readline;

import org.jline.terminal.impl.exec.ExecTerminalProvider;
import org.junit.jupiter.api.Test;

import java.io.FileDescriptor;

import static org.assertj.core.api.Assertions.assertThat;

public class ExecTerminalProviderInnerReflectionRedirectPipeCreatorTest {
    private static final String REDIRECT_PIPE_CREATION_MODE_PROPERTY =
            "org.jline.terminal.exec.redirectPipeCreationMode";

    @Test
    void descriptorRedirectUsesConfiguredExecProviderCreationModes() {
        String originalCreationMode = System.getProperty(REDIRECT_PIPE_CREATION_MODE_PROPERTY);
        try {
            System.setProperty(REDIRECT_PIPE_CREATION_MODE_PROPERTY, "reflection");

            ProcessBuilder.Redirect redirect = new TestExecTerminalProvider().redirectFor(FileDescriptor.out);

            assertThat(redirect).isNotNull();
        } catch (IllegalStateException e) {
            assertThat(e).hasMessage("Unable to create RedirectPipe");
            assertThat(e.getSuppressed())
                    .anySatisfy(suppressed -> assertThat(isExpectedNativeImageReflectionFailure(suppressed)).isTrue());
        } finally {
            restoreProperty(REDIRECT_PIPE_CREATION_MODE_PROPERTY, originalCreationMode);
        }
    }

    private static boolean isExpectedNativeImageReflectionFailure(Throwable failure) {
        String failureClassName = failure.getClass().getName();
        return failure instanceof ClassNotFoundException
                || failure instanceof NoSuchFieldException
                || "java.lang.reflect.InaccessibleObjectException".equals(failureClassName);
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static final class TestExecTerminalProvider extends ExecTerminalProvider {
        private ProcessBuilder.Redirect redirectFor(FileDescriptor descriptor) {
            return newDescriptor(descriptor);
        }
    }
}
