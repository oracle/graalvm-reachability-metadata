/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_core;

import com.diffplug.common.base.FinalizableReferenceQueue;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.io.Closeable;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;

import static org.assertj.core.api.Assertions.assertThat;

public class FinalizableReferenceQueueInnerDecoupledLoaderTest {
    private static final String FINALIZABLE_REFERENCE_QUEUE =
            "com.diffplug.common.base.FinalizableReferenceQueue";
    private static final String SYSTEM_LOADER = FINALIZABLE_REFERENCE_QUEUE + "$SystemLoader";

    @Test
    public void loadsFinalizerFromIsolatedLibraryClassLoader() throws Exception {
        try {
            URL libraryLocation = libraryLocation();

            try (URLClassLoader isolatedLibraryLoader = new URLClassLoader(new URL[] {libraryLocation}, null)) {
                disableSystemLoaderFor(isolatedLibraryLoader);

                Class<?> queueType = Class.forName(FINALIZABLE_REFERENCE_QUEUE, true, isolatedLibraryLoader);
                Object queue = queueType.getConstructor().newInstance();
                try {
                    assertThat(queueType.getClassLoader()).isSameAs(isolatedLibraryLoader);
                } finally {
                    ((Closeable) queue).close();
                }
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static URL libraryLocation() {
        CodeSource codeSource = FinalizableReferenceQueue.class.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();

        URL location = codeSource.getLocation();
        assertThat(location).isNotNull();
        return location;
    }

    private static void disableSystemLoaderFor(ClassLoader libraryLoader) throws ReflectiveOperationException {
        Class<?> systemLoaderType = Class.forName(SYSTEM_LOADER, false, libraryLoader);
        Field disabled = systemLoaderType.getDeclaredField("disabled");
        disabled.setAccessible(true);
        disabled.setBoolean(null, true);
    }
}
