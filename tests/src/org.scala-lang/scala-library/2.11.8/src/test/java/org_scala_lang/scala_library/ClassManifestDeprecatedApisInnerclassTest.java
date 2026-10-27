/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library;

import org.junit.jupiter.api.Test;

import scala.reflect.ClassTag;
import scala.reflect.ClassTag$;

import static org.assertj.core.api.Assertions.assertThat;
import static scala.reflect.ClassManifestDeprecatedApis$class.newArray;

/** Exercises the deprecated manifest array APIs. \u00A7FS-repository-functional-spec.5.2 */
public class ClassManifestDeprecatedApisInnerclassTest {

    @Test
    void createsArraysWithTheManifestRuntimeClass() {
        ClassTag<String> manifest = ClassTag$.MODULE$.apply(String.class);

        assertThat(manifest.arrayManifest().runtimeClass()).isEqualTo(String[].class);

        String[] oneDimension = (String[]) newArray(manifest, 1);
        String[][] twoDimensions = (String[][]) manifest.newArray2(2);
        String[][][] threeDimensions = (String[][][]) manifest.newArray3(3);
        String[][][][] fourDimensions = (String[][][][]) manifest.newArray4(4);
        String[][][][][] fiveDimensions = (String[][][][][]) manifest.newArray5(5);

        assertAllocatedOuterDimension(oneDimension, 1);
        assertAllocatedOuterDimension(twoDimensions, 2);
        assertAllocatedOuterDimension(threeDimensions, 3);
        assertAllocatedOuterDimension(fourDimensions, 4);
        assertAllocatedOuterDimension(fiveDimensions, 5);
    }

    private static void assertAllocatedOuterDimension(Object[] array, int expectedLength) {
        assertThat(array).hasSize(expectedLength).containsOnlyNulls();
    }
}
