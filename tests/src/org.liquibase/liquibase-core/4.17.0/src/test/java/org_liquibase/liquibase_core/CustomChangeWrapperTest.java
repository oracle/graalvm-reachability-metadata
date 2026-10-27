/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.Scope;
import liquibase.change.custom.CustomChangeWrapper;
import liquibase.exception.CustomChangeException;
import liquibase.parser.core.ParsedNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CustomChangeWrapperTest {

    @Test
    void setClassCreatesCustomChangeWithScopedClassLoader() throws Exception {
        CustomChangeWrapper wrapper = new CustomChangeWrapper();

        CustomChangeWrapper result = wrapper.setClass(ExampleCustomTaskChange.class.getName());

        assertThat(result).isSameAs(wrapper);
        assertThat(wrapper.getClassName()).isEqualTo(ExampleCustomTaskChange.class.getName());
        assertThat(wrapper.getCustomChange()).isInstanceOf(ExampleCustomTaskChange.class);
    }

    @Test
    void nonCustomChangeClassIsRejectedWhenThreadContextClassLoaderIsUsedForFallback() {
        CustomChangeWrapper wrapper = new CustomChangeWrapper();
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();

        Thread.currentThread().setContextClassLoader(CustomChangeWrapperTest.class.getClassLoader());
        try {
            assertNonCustomChangeClassIsRejected(wrapper, String.class.getName());
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void nonCustomChangeClassIsRejectedWhenDefaultClassLoaderIsUsedAfterThreadContextFailure() throws Exception {
        CustomChangeWrapper wrapper = new CustomChangeWrapper();
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader scopedClassLoader = CustomChangeWrapperTest.class.getClassLoader();
        ClassLoader blockingContextClassLoader = new BlockingClassLoader(
                originalContextClassLoader,
                String.class.getName()
        );

        Scope.child(Scope.Attr.classLoader, scopedClassLoader, () -> {
            Thread.currentThread().setContextClassLoader(blockingContextClassLoader);
            try {
                assertNonCustomChangeClassIsRejected(wrapper, String.class.getName());
            } finally {
                Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            }
        });
    }

    @Test
    void customLoadLogicInstantiatesCustomChangeAndConvertsMatchingNodesToParams() throws Exception {
        CustomChangeWrapper wrapper = new CustomChangeWrapper()
                .setClass(ExampleCustomTaskChange.class.getName());
        ParsedNode parsedNode = new ParsedNode(null, "customChange")
                .addChild(null, "helloTo", "Liquibase")
                .addChild(new ParsedNode(null, "param")
                        .addChild(null, "name", "anotherProperty")
                        .addChild(null, "value", "anotherValue"));

        wrapper.customLoadLogic(parsedNode, null);

        assertThat(wrapper.getParamValue("helloTo")).isEqualTo("Liquibase");
        assertThat(wrapper.getParamValue("anotherProperty")).isEqualTo("anotherValue");
    }

    private static void assertNonCustomChangeClassIsRejected(CustomChangeWrapper wrapper, String className) {
        assertThatThrownBy(() -> wrapper.setClass(className))
                .isInstanceOf(CustomChangeException.class)
                .hasCauseInstanceOf(ClassCastException.class);
        assertThat(wrapper.getClassName()).isEqualTo(className);
        assertThat(wrapper.getCustomChange()).isNull();
    }
}

final class BlockingClassLoader extends ClassLoader {

    private final String blockedClassName;

    BlockingClassLoader(ClassLoader parent, String blockedClassName) {
        super(parent);
        this.blockedClassName = blockedClassName;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (blockedClassName.equals(name)) {
            throw new ClassNotFoundException(name);
        }
        return super.loadClass(name);
    }
}
