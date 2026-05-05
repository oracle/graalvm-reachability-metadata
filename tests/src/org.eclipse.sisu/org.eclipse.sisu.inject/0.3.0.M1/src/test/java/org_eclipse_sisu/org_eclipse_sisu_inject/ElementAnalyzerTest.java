/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_inject;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.sisu.wire.WireModule;
import org.junit.jupiter.api.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;

public class ElementAnalyzerTest {
    @Test
    void wireModuleRoutesUnresolvedConstructorDependenciesToCustomWiring() {
        Collaborator collaborator = new WiredCollaborator();
        List<Key<?>> wiredKeys = new ArrayList<>();
        Module applicationModule = new AbstractModule() {
            @Override
            protected void configure() {
                bind(NeedsCollaborator.class);
            }
        };

        Module wireModule = new WireModule(applicationModule).with((Binder binder) -> (Key<?> key) -> {
            wiredKeys.add(key);
            if (Key.get(Collaborator.class).equals(key)) {
                binder.bind(Collaborator.class).toInstance(collaborator);
            }
            return true;
        });

        Injector injector = Guice.createInjector(wireModule);

        assertThat(injector.getInstance(NeedsCollaborator.class).collaborator()).isSameAs(collaborator);
        assertThat(wiredKeys).containsExactly(Key.get(Collaborator.class));
    }

    private interface Collaborator {
    }

    private static final class WiredCollaborator implements Collaborator {
    }

    private static final class NeedsCollaborator {
        private final Collaborator collaborator;

        @Inject
        private NeedsCollaborator(Collaborator collaborator) {
            this.collaborator = collaborator;
        }

        private Collaborator collaborator() {
            return collaborator;
        }
    }
}
