/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_core.arquillian_core_impl_base;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.jboss.arquillian.core.impl.loadable.JavaSPIExtensionLoader;
import org.junit.jupiter.api.Test;

public class JavaSPIExtensionLoaderTest {
    @Test
    void allLoadsServiceProvidersFromJavaSpiResource() {
        JavaSPIExtensionLoader loader = new JavaSPIExtensionLoader();

        Collection<JavaSpiTestService> services = loader.all(
                JavaSPIExtensionLoaderTest.class.getClassLoader(), JavaSpiTestService.class);

        assertThat(services)
                .extracting(JavaSpiTestService::name)
                .containsExactly("first", "second");
    }

    @Test
    void loadVetoedLoadsServiceAndImplementationClassesFromExclusionsResource() {
        JavaSPIExtensionLoader loader = new JavaSPIExtensionLoader();

        Map<Class<?>, Set<Class<?>>> vetoed = loader.loadVetoed(JavaSPIExtensionLoaderTest.class.getClassLoader());

        assertThat(vetoed)
                .containsKey(JavaSpiTestService.class);
        assertThat(vetoed.get(JavaSpiTestService.class))
                .containsExactly(VetoedJavaSpiTestService.class, SecondJavaSpiTestService.class);
    }
}

interface JavaSpiTestService {
    String name();
}

class FirstJavaSpiTestService implements JavaSpiTestService {
    @Override
    public String name() {
        return "first";
    }
}

class SecondJavaSpiTestService implements JavaSpiTestService {
    @Override
    public String name() {
        return "second";
    }
}

class VetoedJavaSpiTestService implements JavaSpiTestService {
    @Override
    public String name() {
        return "vetoed";
    }
}
