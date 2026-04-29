/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.modelmapper.internal.bytebuddy.build.Plugin;
import org.modelmapper.internal.bytebuddy.description.type.TypeDescription;
import org.modelmapper.internal.bytebuddy.dynamic.ClassFileLocator;
import org.modelmapper.internal.bytebuddy.dynamic.DynamicType;

public class PluginInnerEngineInnerDefaultTest {
    @TempDir
    Path tempDirectory;

    @Test
    void scansPluginNamesFromBuildPluginResources() throws Exception {
        Set<String> plugins = Plugin.Engine.Default.scan(PluginInnerEngineInnerDefaultTest.class.getClassLoader());

        assertThat(plugins).contains(NoOpPlugin.class.getName());
    }


    public static class NoOpPlugin implements Plugin {
        @Override
        public DynamicType.Builder<?> apply(DynamicType.Builder<?> builder,
                                            TypeDescription typeDescription,
                                            ClassFileLocator classFileLocator) {
            return builder;
        }

        @Override
        public boolean matches(TypeDescription target) {
            return false;
        }

        @Override
        public void close() {
        }
    }
}
