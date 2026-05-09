/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_jsr223

import static org.assertj.core.api.Assertions.assertThat

import groovy.lang.Script
import groovy.transform.CompileStatic
import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test

public class GroovyScriptEngineImplTest {
    private static final String GREETING_SCRIPT = '''
        String greet(String name) {
            "Hello, ${name}"
        }

        int add(int left, int right) {
            left + right
        }

        add(19, 23)
    '''

    @Test
    void defaultConstructorUsesThreadContextLoaderThatCanSeeGroovyScripts() {
        Thread currentThread = Thread.currentThread()
        ClassLoader originalLoader = currentThread.getContextClassLoader()
        ClassLoader parentLoader = originalLoader ?: GroovyScriptEngineImpl.class.getClassLoader()
        TrackingClassLoader trackingLoader = new TrackingClassLoader(parentLoader)

        try {
            currentThread.setContextClassLoader(trackingLoader)

            GroovyScriptEngineImpl engine = new GroovyScriptEngineImpl()

            assertThat(engine.getClassLoader()).isNotNull()
            assertThat(trackingLoader.isGroovyScriptClassRequested()).isTrue()
        } finally {
            currentThread.setContextClassLoader(originalLoader)
        }
    }

    @Test
    void evaluatesScriptMethodsAndExposesThemThroughInvocableProxy() throws Exception {
        GroovyScriptEngineImpl engine = new GroovyScriptEngineImpl()
        GreetingService greetingService = engine.getInterface(GreetingService)

        Object result
        try {
            result = engine.eval(GREETING_SCRIPT)
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error)
            return
        }

        assertThat(result).isEqualTo(42)
        assertThat(greetingService.greet('Ada')).isEqualTo('Hello, Ada')
        assertThat(greetingService.add(5, 7)).isEqualTo(12)
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error
        }
    }

    public static interface GreetingService {
        String greet(String name)

        int add(int left, int right)
    }

    @CompileStatic
    private static final class TrackingClassLoader extends ClassLoader {
        private boolean groovyScriptClassRequested

        TrackingClassLoader(ClassLoader parent) {
            super(parent)
        }

        @Override
        Class<?> loadClass(String name) throws ClassNotFoundException {
            if (Script.class.getName().equals(name)) {
                groovyScriptClassRequested = true
            }
            return super.loadClass(name)
        }

        boolean isGroovyScriptClassRequested() {
            return groovyScriptClassRequested
        }
    }
}
