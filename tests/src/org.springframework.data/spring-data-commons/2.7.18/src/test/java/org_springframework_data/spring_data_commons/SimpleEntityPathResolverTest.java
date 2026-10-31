/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.types.EntityPath;
import org.junit.jupiter.api.Test;
import org.springframework.data.querydsl.SimpleEntityPathResolver;

public class SimpleEntityPathResolverTest {

    @Test
    void resolvesQuerydslMetamodelSingletonForDomainClass() {
        EntityPath<Customer> path = SimpleEntityPathResolver.INSTANCE.createPath(Customer.class);

        assertThat(path).isSameAs(QSimpleEntityPathResolverTest_Customer.CUSTOMER);
        assertThat(path.getType()).isEqualTo(Customer.class);
        assertThat(path.getMetadata().getName()).isEqualTo("customer");
        assertThat(QSimpleEntityPathResolverTest_Customer.CUSTOMER.name.eq("Ada").toString())
                .isEqualTo("customer.name = Ada");
    }

    public static class Customer {

        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
