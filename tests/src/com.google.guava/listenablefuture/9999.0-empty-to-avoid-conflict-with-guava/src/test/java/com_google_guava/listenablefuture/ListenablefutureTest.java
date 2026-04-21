/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.listenablefuture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class ListenablefutureTest {
    private static final String LISTENABLE_FUTURE_PACKAGE_RESOURCE = "com/google/common/util/concurrent/";
    private static final String LISTENABLE_FUTURE_CLASS_RESOURCE =
            "com/google/common/util/concurrent/ListenableFuture.class";

    @Test
    void placeholderDoesNotExposeTheStandaloneListenableFuturePackageLayout() {
        assertThat(classLoader().getResource(LISTENABLE_FUTURE_PACKAGE_RESOURCE)).isNull();
        assertThat(classLoader().getResource(LISTENABLE_FUTURE_CLASS_RESOURCE)).isNull();
        assertThat(classLoader().getResourceAsStream(LISTENABLE_FUTURE_CLASS_RESOURCE)).isNull();
    }

    @Test
    void placeholderDoesNotExposeTheStandaloneListenableFutureBytecodeThroughEnumerationLookups() throws IOException {
        assertThat(Collections.list(classLoader().getResources(LISTENABLE_FUTURE_PACKAGE_RESOURCE))).isEmpty();
        assertThat(Collections.list(classLoader().getResources(LISTENABLE_FUTURE_CLASS_RESOURCE))).isEmpty();
    }

    @Test
    void threadContextClassLoaderAlsoSeesNoStandaloneListenableFutureBytecode() throws IOException {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

        assertThat(contextClassLoader).isNotNull();
        assertThat(contextClassLoader.getResource(LISTENABLE_FUTURE_CLASS_RESOURCE)).isNull();
        assertThat(contextClassLoader.getResourceAsStream(LISTENABLE_FUTURE_CLASS_RESOURCE)).isNull();
        assertThat(Collections.list(contextClassLoader.getResources(LISTENABLE_FUTURE_CLASS_RESOURCE))).isEmpty();
    }

    private static ClassLoader classLoader() {
        return ListenablefutureTest.class.getClassLoader();
    }
}
