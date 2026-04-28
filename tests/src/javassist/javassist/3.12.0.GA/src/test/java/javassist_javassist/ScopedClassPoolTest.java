/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javassist.ClassPool;
import javassist.NotFoundException;
import javassist.scopedpool.ScopedClassPoolRepository;
import javassist.scopedpool.ScopedClassPoolRepositoryImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class ScopedClassPoolTest {
    @Test
    void scopedLookupChecksOwningClassLoaderResourceBeforeOtherPools() {
        ScopedClassPoolRepository repository = ScopedClassPoolRepositoryImpl.getInstance();
        RecordingClassLoader classLoader = new RecordingClassLoader();
        ClassPool classPool = repository.registerClassLoader(classLoader);

        try {
            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> classPool.get("example.scopedpool.MissingType"));

            assertThat(classLoader.requestedResources())
                    .contains("example/scopedpool/MissingType.class");
        } finally {
            repository.unregisterClassLoader(classLoader);
        }
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private final List<String> requestedResources = new ArrayList<>();

        private RecordingClassLoader() {
            super(null);
        }

        @Override
        public URL getResource(String name) {
            requestedResources.add(name);
            return null;
        }

        List<String> requestedResources() {
            return requestedResources;
        }
    }
}
