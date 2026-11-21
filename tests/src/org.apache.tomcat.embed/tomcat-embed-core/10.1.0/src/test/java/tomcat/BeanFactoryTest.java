/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import javax.naming.StringRefAddr;

import org.apache.naming.ResourceRef;
import org.apache.naming.factory.BeanFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeanFactoryTest {
    @Test
    void constructsAndConfiguresResourceBean() throws Exception {
        ResourceRef reference = new ResourceRef(Service.class.getName(), null, null, null, false);
        reference.add(new StringRefAddr("name", "catalog"));
        reference.add(new StringRefAddr("workers", "8"));
        Service service = (Service) new BeanFactory().getObjectInstance(reference, null, null, null);
        assertEquals("catalog", service.getName());
        assertEquals(8, service.getWorkers());
    }

    public static class Service {
        private String name;
        private int workers;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getWorkers() {
            return workers;
        }

        public void setWorkers(int workers) {
            this.workers = workers;
        }
    }
}
