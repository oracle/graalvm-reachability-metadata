/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_inject;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.sisu.space.QualifiedTypeBinder;
import org.junit.jupiter.api.Test;

import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;

public class QualifiedTypeBinderTest {
    private static final Object SOURCE = "qualified-module-source";

    @Test
    void hearInstallsConcreteModuleByInvokingItsNoArgConstructor() {
        InstalledPrivateConstructorModule.created().set(0);

        List<Element> elements = Elements.getElements(new ScanningModule());

        boolean installedBinding = elements.stream()
            .filter(Binding.class::isInstance)
            .map(Binding.class::cast)
            .anyMatch(binding -> Key.get(ModuleBoundService.class).equals(binding.getKey()));
        assertThat(InstalledPrivateConstructorModule.created().get()).isEqualTo(1);
        assertThat(installedBinding).isTrue();
    }

    private static final class ScanningModule implements Module {
        @Override
        public void configure(Binder binder) {
            new QualifiedTypeBinder(binder).hear(InstalledPrivateConstructorModule.class, SOURCE);
        }
    }

    private static final class InstalledPrivateConstructorModule implements Module {
        private static final AtomicInteger CREATED = new AtomicInteger();

        private InstalledPrivateConstructorModule() {
            CREATED.incrementAndGet();
        }

        @Override
        public void configure(Binder binder) {
            binder.bind(ModuleBoundService.class).toInstance(new ModuleBoundService());
        }

        private static AtomicInteger created() {
            return CREATED;
        }
    }

    private static final class ModuleBoundService {
    }
}
