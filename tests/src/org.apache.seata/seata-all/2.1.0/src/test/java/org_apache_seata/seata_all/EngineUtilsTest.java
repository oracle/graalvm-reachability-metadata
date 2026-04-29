/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.apache.seata.saga.engine.pcext.utils.EngineUtils;
import org.apache.seata.saga.proctrl.impl.ProcessContextImpl;
import org.apache.seata.saga.statelang.domain.DomainConstants;
import org.apache.seata.saga.statelang.domain.TaskState;
import org.apache.seata.saga.statelang.domain.impl.AbstractTaskState.ExceptionMatchImpl;
import org.apache.seata.saga.statelang.domain.impl.ServiceTaskStateImpl;
import org.junit.jupiter.api.Test;

public class EngineUtilsTest {
    @Test
    void handleExceptionUsesContextClassLoaderFallbackForCatchClasses() {
        String fallbackOnlyExceptionName = "context.only.EngineUtilsFallbackOnlyException";
        String nextState = "handleFallbackException";
        ServiceTaskStateImpl state = new ServiceTaskStateImpl();
        ExceptionMatchImpl exceptionMatch = new ExceptionMatchImpl();
        exceptionMatch.setExceptions(Collections.singletonList(fallbackOnlyExceptionName));
        exceptionMatch.setNext(nextState);
        state.setCatches(Collections.<TaskState.ExceptionMatch>singletonList(exceptionMatch));
        ProcessContextImpl context = new ProcessContextImpl();

        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        ClassLoader fallbackClassLoader = new FallbackExceptionClassLoader(
                originalClassLoader,
                fallbackOnlyExceptionName);
        try {
            currentThread.setContextClassLoader(fallbackClassLoader);

            EngineUtils.handleException(context, state, new FallbackOnlyException("handled by fallback loader"));
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }

        assertThat(context.getVariableLocally(DomainConstants.VAR_NAME_CURRENT_EXCEPTION_ROUTE)).isEqualTo(nextState);
        assertThat(context.hasVariableLocal(DomainConstants.VAR_NAME_IS_EXCEPTION_NOT_CATCH)).isFalse();
        assertThat(exceptionMatch.getExceptionClasses()).containsExactly(FallbackOnlyException.class);
    }

    private static final class FallbackExceptionClassLoader extends ClassLoader {
        private final String fallbackOnlyExceptionName;

        private FallbackExceptionClassLoader(ClassLoader parent, String fallbackOnlyExceptionName) {
            super(parent);
            this.fallbackOnlyExceptionName = fallbackOnlyExceptionName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (fallbackOnlyExceptionName.equals(name)) {
                return FallbackOnlyException.class;
            }
            return super.loadClass(name);
        }
    }

    public static class FallbackOnlyException extends Exception {
        public FallbackOnlyException(String message) {
            super(message);
        }
    }
}
