/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_spatial;

import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.spatial.integration.SpatialInitializer;
import org.junit.jupiter.api.Test;

class HibernateSpatialTest {

    @Test
    void test() throws Exception {
        new SpatialInitializer().contribute(new StandardServiceRegistryBuilder());
    }
}
