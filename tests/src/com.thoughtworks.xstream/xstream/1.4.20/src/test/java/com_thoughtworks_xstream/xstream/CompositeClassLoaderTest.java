/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import com.thoughtworks.xstream.core.util.CompositeClassLoader;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CompositeClassLoaderTest {
    @Test
    void loadsClassThroughAddedClassLoader() throws ClassNotFoundException {
        CompositeClassLoader loader = new CompositeClassLoader();
        loader.add(CompositeClassLoaderTest.class.getClassLoader());

        Class<?> loadedClass = loader.loadClass(LoadableFixture.class.getName());

        assertThat(loadedClass).isSameAs(LoadableFixture.class);
    }

    public static final class LoadableFixture {
    }
}
