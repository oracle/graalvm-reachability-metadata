/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ibatis.ibatis_sqlmap;

import com.ibatis.common.beans.ClassInfo;
import com.ibatis.common.beans.Probe;
import com.ibatis.common.beans.ProbeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class ClassInfoTest {
    @Test
    void discoversInheritedBeanPropertiesAndCreatesAnInstance() {
        ClassInfo info = ClassInfo.getInstance(Customer.class);
        assertThat(info.getReadablePropertyNames()).contains("name", "active", "region");
        assertThat(info.getWriteablePropertyNames()).contains("name", "active", "region");
        assertThat(info.getGetterType("name")).isEqualTo(String.class);
        assertThat(info.getSetterType("active")).isEqualTo(boolean.class);
        assertThat(info.hasReadableProperty("region")).isTrue();
        assertThat(info.hasWritableProperty("region")).isTrue();
        Customer customer = (Customer) info.instantiateClass();
        Probe probe = ProbeFactory.getProbe();
        probe.setObject(customer, "name", "Ada");
        probe.setObject(customer, "active", true);
        probe.setObject(customer, "region", "north");
        assertThat(customer.getName()).isEqualTo("Ada");
        assertThat(customer.isActive()).isTrue();
        assertThat(customer.region).isEqualTo("north");
        assertThat(probe.getObject(customer, "name")).isEqualTo("Ada");
        assertThat(probe.getObject(customer, "region")).isEqualTo("north");
    }

    public interface Named {
        String getName();

        void setName(String name);
    }

    public static class Regional {
        public String region;
    }

    public static class Customer extends Regional implements Named {
        private String name;
        private boolean active;

        public Customer() {
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void setName(String name) {
            this.name = name;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}
