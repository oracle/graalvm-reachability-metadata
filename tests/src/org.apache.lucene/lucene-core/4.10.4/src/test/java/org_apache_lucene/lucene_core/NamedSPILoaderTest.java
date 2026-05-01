/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene.lucene_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.util.NamedSPILoader;
import org.junit.jupiter.api.Test;

public class NamedSPILoaderTest implements NamedSPILoader.NamedSPI {
    private static final String SERVICE_NAME = "NamedSPITest";

    @Test
    public void reloadInstantiatesProviderDeclaredInSpiResource() {
        ClassLoader classLoader = NamedSPILoaderTest.class.getClassLoader();
        assertThat(classLoader).isNotNull();

        NamedSPILoader<NamedSPILoaderTest> loader = new NamedSPILoader<>(NamedSPILoaderTest.class, classLoader);
        loader.reload(classLoader);

        NamedSPILoaderTest service = loader.lookup(SERVICE_NAME);

        assertThat(service).isNotSameAs(this);
        assertThat(service.getName()).isEqualTo(SERVICE_NAME);
        assertThat(loader.availableServices()).containsExactly(SERVICE_NAME);
        assertThat(discoveredServiceNames(loader)).containsExactly(SERVICE_NAME);
    }

    @Override
    public String getName() {
        return SERVICE_NAME;
    }

    private static List<String> discoveredServiceNames(NamedSPILoader<NamedSPILoaderTest> loader) {
        List<String> names = new ArrayList<>();
        for (NamedSPILoaderTest service : loader) {
            names.add(service.getName());
        }
        return names;
    }
}
