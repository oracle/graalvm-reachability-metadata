/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_inject;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.sisu.launch.SisuExtensions;
import org.eclipse.sisu.space.URLClassSpace;
import org.junit.jupiter.api.Test;

import com.google.inject.Binder;
import com.google.inject.Module;

public class SisuExtensionsTest {
    @Test
    void createInstantiatesIndexedModulesWithContextConstructorAndNoArgFallback() {
        URLClassSpace classSpace = new URLClassSpace(SisuExtensionsTest.class.getClassLoader());
        SisuExtensions extensions = SisuExtensions.global(classSpace);
        ExtensionContext context = new ExtensionContext("sisu-context");

        List<Module> modules = extensions.create(Module.class, ExtensionContext.class, context);
        List<RecordingModule> recordingModules = modules.stream()
            .filter(RecordingModule.class::isInstance)
            .map(RecordingModule.class::cast)
            .toList();

        assertThat(recordingModules).extracting(RecordingModule::message)
            .containsExactly("context:sisu-context", "default");
    }

    public interface RecordingModule extends Module {
        String message();
    }

    public static final class ExtensionContext {
        private final String value;

        private ExtensionContext(String value) {
            this.value = value;
        }
    }

    public static final class ContextualModule implements RecordingModule {
        private final ExtensionContext context;

        public ContextualModule(ExtensionContext context) {
            this.context = context;
        }

        @Override
        public void configure(Binder binder) {
        }

        @Override
        public String message() {
            return "context:" + context.value;
        }
    }

    public static final class DefaultModule implements RecordingModule {
        public DefaultModule() {
        }

        @Override
        public void configure(Binder binder) {
        }

        @Override
        public String message() {
            return "default";
        }
    }
}
