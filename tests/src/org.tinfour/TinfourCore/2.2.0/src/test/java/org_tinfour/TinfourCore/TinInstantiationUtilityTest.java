/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tinfour.TinfourCore;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.Vertex;
import org.tinfour.semivirtual.SemiVirtualIncrementalTin;
import org.tinfour.standard.IncrementalTin;
import org.tinfour.utils.TinInstantiationUtility;

public class TinInstantiationUtilityTest {
    @Test
    void constructsStandardTinThroughPublicApi() {
        assertTriangulates(IncrementalTin.class);
    }

    @Test
    void constructsSemiVirtualTinThroughPublicApi() {
        assertTriangulates(SemiVirtualIncrementalTin.class);
    }

    private static void assertTriangulates(Class<? extends IIncrementalTin> tinClass) {
        TinInstantiationUtility utility = new TinInstantiationUtility(0.5, 5);
        IIncrementalTin tin = utility.constructInstance(tinClass, 0.25);
        try {
            Vertex[] vertices = {
                new Vertex(0.0, 0.0, 2.0),
                new Vertex(2.0, 0.0, 3.0),
                new Vertex(0.0, 2.0, 4.0),
                new Vertex(2.0, 2.0, 5.0),
                new Vertex(1.0, 1.0, 6.0)
            };
            for (Vertex vertex : vertices) {
                tin.add(vertex);
            }

            assertThat(tin.isBootstrapped()).isTrue();
            assertThat(tin.getVertices()).hasSize(vertices.length);
            assertThat(tin.countTriangles().getCount()).isGreaterThan(0);
            assertThat(tin.getIntegrityCheck().inspect()).isTrue();
        } finally {
            tin.dispose();
        }
    }
}
