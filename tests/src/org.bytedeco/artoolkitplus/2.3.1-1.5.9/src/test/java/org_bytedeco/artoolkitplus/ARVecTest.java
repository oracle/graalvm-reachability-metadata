/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.artoolkitplus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.bytedeco.artoolkitplus.global.ARToolKitPlus._free;
import static org.bytedeco.artoolkitplus.global.ARToolKitPlus.alloc;
import static org.bytedeco.artoolkitplus.global.ARToolKitPlus.household;
import static org.bytedeco.artoolkitplus.global.ARToolKitPlus.innerproduct;

import org.bytedeco.artoolkitplus.ARVec;
import org.junit.jupiter.api.Test;

public class ARVecTest {
    @Test
    void createsNormalizedHouseholderVector() {
        ARVec vector = alloc(2);

        try {
            vector.v().put(new float[] {3.0f, 4.0f});

            assertThat(household(vector)).isCloseTo(-5.0f, within(0.0001f));

            float[] reflector = new float[2];
            vector.v().get(reflector);
            assertThat(reflector)
                    .containsExactly(new float[] {1.264911f, 0.632456f}, within(0.0001f));
            assertThat(innerproduct(vector, vector)).isCloseTo(2.0f, within(0.0001f));
        } finally {
            _free(vector);
        }
    }
}
