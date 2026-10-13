/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.javacpp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import org.bytedeco.javacpp.annotation.Platform;
import org.bytedeco.javacpp.tools.BuildEnabled;
import org.bytedeco.javacpp.tools.Builder;
import org.bytedeco.javacpp.tools.Logger;

public class BuilderTest {

    @Platform(define = "JAVACPP_BUILDER_TEST")
    public static class BuildHook implements BuildEnabled {
        @Override
        public void init(Logger logger, Properties properties, String encoding) {
            properties.setProperty("test.build.hook.initialized", "true");
        }
    }

    @Test
    public void buildInstantiatesAndInitializesBuildEnabledBinding() throws Exception {
        String oldLoadLibraries = System.getProperty("org.bytedeco.javacpp.loadlibraries");
        try {
            Builder builder = new Builder()
                    .generate(false)
                    .compile(false)
                    .classesOrPackages(BuildHook.class.getName());

            File[] generated = builder.build();

            assertThat(generated).isEmpty();
            assertThat(builder.getProperty("test.build.hook.initialized")).isEqualTo("true");
        } finally {
            restoreProperty("org.bytedeco.javacpp.loadlibraries", oldLoadLibraries);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
