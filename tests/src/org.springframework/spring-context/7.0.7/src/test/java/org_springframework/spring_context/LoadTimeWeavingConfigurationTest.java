/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.EnableLoadTimeWeaving;
import org.springframework.context.annotation.LoadTimeWeavingConfiguration;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.instrument.classloading.SimpleLoadTimeWeaver;

import static org.junit.jupiter.api.Assertions.assertSame;

public class LoadTimeWeavingConfigurationTest {
    @Test
    void autodetectsAbsentAspectConfigurationAndKeepsConfiguredWeaver() {
        SimpleLoadTimeWeaver weaver = new SimpleLoadTimeWeaver();
        LoadTimeWeavingConfiguration configuration = new LoadTimeWeavingConfiguration();
        configuration.setImportMetadata(AnnotationMetadata.introspect(WeavingSettings.class));
        configuration.setBeanClassLoader(getClass().getClassLoader());
        configuration.setLoadTimeWeavingConfigurer(() -> weaver);
        assertSame(weaver, configuration.loadTimeWeaver());
        assertSame(weaver, configuration.loadTimeWeaver());
    }

    @EnableLoadTimeWeaving
    public static class WeavingSettings {
    }
}
