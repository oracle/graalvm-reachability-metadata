/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import java.util.List;

import org.apache.seata.saga.engine.pcext.utils.EngineUtils;
import org.apache.seata.saga.proctrl.impl.ProcessContextImpl;
import org.apache.seata.saga.statelang.domain.DomainConstants;
import org.apache.seata.saga.statelang.domain.TaskState;
import org.apache.seata.saga.statelang.domain.impl.AbstractTaskState;
import org.apache.seata.saga.statelang.domain.impl.ScriptTaskStateImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EngineUtilsTest {
    private static final String CONTEXT_ONLY_EXCEPTION_NAME =
            "org_apache_seata.seata_all.missing.ContextOnlyException";

    private ClassLoader originalContextClassLoader;

    @BeforeEach
    void captureContextClassLoader() {
        originalContextClassLoader = Thread.currentThread().getContextClassLoader();
    }

    @AfterEach
    void restoreContextClassLoader() {
        Thread.currentThread().setContextClassLoader(originalContextClassLoader);
    }

    @Test
    void handleExceptionUsesTheScriptTaskStateHandlerClassLoaderFirst() {
        ProcessContextImpl context = new ProcessContextImpl();
        ScriptTaskStateImpl state = stateWithCatch(PrimaryLoadedException.class.getName(), "primary-route");

        EngineUtils.handleException(context, state, new PrimaryLoadedException("boom"));

        TaskState.ExceptionMatch exceptionMatch = state.getCatches().get(0);
        assertThat(exceptionMatch.getExceptionClasses()).containsExactly(PrimaryLoadedException.class);
        assertThat(context.getVariableLocally(DomainConstants.VAR_NAME_CURRENT_EXCEPTION_ROUTE))
                .isEqualTo("primary-route");
        assertThat(context.hasVariableLocal(DomainConstants.VAR_NAME_IS_EXCEPTION_NOT_CATCH)).isFalse();
    }

    @Test
    void handleExceptionUsesTheContextClassLoaderAsAFallback() {
        Thread.currentThread().setContextClassLoader(new ContextRouteClassLoader());
        ProcessContextImpl context = new ProcessContextImpl();
        ScriptTaskStateImpl state = stateWithCatch(CONTEXT_ONLY_EXCEPTION_NAME, "context-route");

        EngineUtils.handleException(context, state, new FallbackLoadedException("boom"));

        TaskState.ExceptionMatch exceptionMatch = state.getCatches().get(0);
        assertThat(exceptionMatch.getExceptionClasses()).containsExactly(FallbackLoadedException.class);
        assertThat(context.getVariableLocally(DomainConstants.VAR_NAME_CURRENT_EXCEPTION_ROUTE))
                .isEqualTo("context-route");
        assertThat(context.hasVariableLocal(DomainConstants.VAR_NAME_IS_EXCEPTION_NOT_CATCH)).isFalse();
    }

    @Test
    void handleExceptionMarksTheFailureAsUncaughtWhenNoClassLoaderCanResolveTheCatchType() {
        Thread.currentThread().setContextClassLoader(new ClassLoader(EngineUtilsTest.class.getClassLoader()) {
        });
        ProcessContextImpl context = new ProcessContextImpl();
        ScriptTaskStateImpl state = stateWithCatch(
                "org_apache_seata.seata_all.missing.UnresolvableException",
                "unused-route");

        EngineUtils.handleException(context, state, new IllegalStateException("boom"));

        TaskState.ExceptionMatch exceptionMatch = state.getCatches().get(0);
        assertThat(exceptionMatch.getExceptionClasses()).isEmpty();
        assertThat(context.getVariableLocally(DomainConstants.VAR_NAME_CURRENT_EXCEPTION_ROUTE)).isNull();
        assertThat(context.getVariableLocally(DomainConstants.VAR_NAME_IS_EXCEPTION_NOT_CATCH)).isEqualTo(true);
    }

    private static ScriptTaskStateImpl stateWithCatch(String exceptionClassName, String nextState) {
        AbstractTaskState.ExceptionMatchImpl exceptionMatch = new AbstractTaskState.ExceptionMatchImpl();
        exceptionMatch.setExceptions(List.of(exceptionClassName));
        exceptionMatch.setNext(nextState);

        ScriptTaskStateImpl state = new ScriptTaskStateImpl();
        state.setCatches(List.of(exceptionMatch));
        return state;
    }

    private static final class ContextRouteClassLoader extends ClassLoader {
        private ContextRouteClassLoader() {
            super(EngineUtilsTest.class.getClassLoader());
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (CONTEXT_ONLY_EXCEPTION_NAME.equals(name)) {
                return FallbackLoadedException.class;
            }
            return super.loadClass(name);
        }
    }

    private static final class PrimaryLoadedException extends Exception {
        private PrimaryLoadedException(String message) {
            super(message);
        }
    }

    private static final class FallbackLoadedException extends Exception {
        private FallbackLoadedException(String message) {
            super(message);
        }
    }
}
