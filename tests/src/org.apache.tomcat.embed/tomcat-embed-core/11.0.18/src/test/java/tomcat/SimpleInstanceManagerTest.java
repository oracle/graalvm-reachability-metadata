/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import org.apache.catalina.core.StandardWrapper;
import org.apache.tomcat.SimpleInstanceManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleInstanceManagerTest {

    @Test
    void instantiatesClassesByClassAndName() throws Exception {
        SimpleInstanceManager manager = new SimpleInstanceManager();
        String className = StandardWrapper.class.getName();

        assertThat(manager.newInstance(StandardWrapper.class)).isInstanceOf(StandardWrapper.class);
        assertThat(manager.newInstance(className)).isInstanceOf(StandardWrapper.class);
        assertThat(manager.newInstance(className, getClass().getClassLoader())).isInstanceOf(StandardWrapper.class);
    }
}
