/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.apache.tools.ant.taskdefs.Rmic;
import org.apache.tools.ant.taskdefs.rmic.RmicAdapter;
import org.apache.tools.ant.taskdefs.rmic.RmicAdapterFactory;
import org.junit.jupiter.api.Test;

public class RmicAdapterFactoryTest {
    @Test
    void resolvesNamedAdapterAndMapsStubOutput() {
        Rmic rmic = new Rmic();
        rmic.setStubVersion("1.2");
        RmicAdapter adapter = RmicAdapterFactory.getRmic(
                "org.apache.tools.ant.taskdefs.rmic.SunRmic", rmic);
        adapter.setRmic(rmic);
        assertArrayEquals(new String[] {"Service_Stub.class"}, adapter.getMapper().mapFileName("Service.class"));
    }
}
