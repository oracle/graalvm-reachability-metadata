/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.cxf.catalog.OASISCatalogManager;
import org.junit.jupiter.api.Test;

public class OASISCatalogManagerTest {
    @Test
    void discoversCatalogResourcesFromTheApplicationClassLoader() throws Exception {
        OASISCatalogManager manager = new OASISCatalogManager();

        manager.loadCatalogs(getClass().getClassLoader(), OASISCatalogManager.DEFAULT_CATALOG_NAME);

        assertThat(manager.getEntityResolver()).isNotNull();
    }
}
