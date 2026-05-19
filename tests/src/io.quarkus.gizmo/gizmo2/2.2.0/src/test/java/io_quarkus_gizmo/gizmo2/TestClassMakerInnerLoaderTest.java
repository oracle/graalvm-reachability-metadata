/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus_gizmo.gizmo2;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import io.quarkus.gizmo2.testing.TestClassMaker;

public class TestClassMakerInnerLoaderTest {
    private static final String BROKEN_CLASS_NAME = "io_quarkus_gizmo.gizmo2.generated.BrokenLoaderTarget";
    private static final String BROKEN_CLASS_RESOURCE = "io_quarkus_gizmo/gizmo2/generated/BrokenLoaderTarget.class";

    @Test
    void retriesLoadedClassLookupAfterClassDefinitionFailsWithLinkageError() throws ClassNotFoundException {
        try {
            exerciseBrokenLocalClassLoading();
            fail("Expected malformed class bytes to fail during class definition");
        } catch (LinkageError expected) {
            assertNotNull(expected);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static void exerciseBrokenLocalClassLoading() throws ClassNotFoundException {
        TestClassMaker maker = TestClassMaker.create();
        maker.registerResource(BROKEN_CLASS_RESOURCE, new byte[] {0, 1, 2, 3});

        maker.classLoader().loadClass(BROKEN_CLASS_NAME);
    }
}
