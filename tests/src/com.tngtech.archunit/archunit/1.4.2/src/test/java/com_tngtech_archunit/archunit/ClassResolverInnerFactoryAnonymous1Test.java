/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.resolvers.ClassResolver;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class ClassResolverInnerFactoryAnonymous1Test {
    private static final AtomicReference<List<String>> RECEIVED_ARGUMENTS = new AtomicReference<>();

    @Test
    void importerInstantiatesConfiguredResolverThroughListConstructor() {
        ArchConfiguration.withThreadLocalScope(configuration -> {
            configuration.setClassResolver(ListConstructorResolver.class);
            configuration.setClassResolverArguments("first", "second");

            JavaClass imported =
                    TestClassImporter.importClassWithConfiguredResolver(AnnotatedFixture.class);

            assertThat(imported.getName()).isEqualTo(AnnotatedFixture.class.getName());
            assertThat(RECEIVED_ARGUMENTS.get()).containsExactly("first", "second");
        });
    }

    public static final class ListConstructorResolver implements ClassResolver {
        public ListConstructorResolver(List<String> arguments) {
            RECEIVED_ARGUMENTS.set(List.copyOf(arguments));
        }

        @Override
        public void setClassUriImporter(ClassUriImporter classUriImporter) { }

        @Override
        public Optional<JavaClass> tryResolve(String typeName) {
            return Optional.empty();
        }
    }
}
