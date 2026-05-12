/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryComposition.RepositoryFragments;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.core.support.RepositoryFragment;

public class DefaultRepositoryInformationTest {

    @Test
    void discoversQueryAndCustomRepositoryMethodsFromRepositoryInterface() {
        TestRepositoryFactory factory = new TestRepositoryFactory();
        RepositoryFragments fragments = RepositoryFragments.of(
                RepositoryFragment.implemented(SampleRepositoryCustom.class, new SampleRepositoryCustomImpl()));

        RepositoryInformation information = factory.getRepositoryInformation(SampleRepository.class, fragments);

        Set<String> queryMethodNames = information.getQueryMethods().stream()
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(queryMethodNames).contains("findByName");
        assertThat(queryMethodNames).doesNotContain("customGreeting");
        assertThat(information.hasCustomMethod()).isTrue();
    }

    interface SampleRepository extends Repository<SampleEntity, Long>, SampleRepositoryCustom {

        SampleEntity findByName(String name);
    }

    interface SampleRepositoryCustom {

        String customGreeting(String name);
    }

    static final class SampleRepositoryCustomImpl implements SampleRepositoryCustom {

        @Override
        public String customGreeting(String name) {
            return "Hello " + name;
        }
    }

    static final class SampleEntity {

        private final Long id;
        private final String name;

        SampleEntity(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        Long getId() {
            return id;
        }

        String getName() {
            return name;
        }
    }

    static class SampleRepositoryBase {
    }

    static final class TestRepositoryFactory extends RepositoryFactorySupport {

        RepositoryInformation getRepositoryInformation(Class<?> repositoryInterface, RepositoryFragments fragments) {
            RepositoryMetadata metadata = getRepositoryMetadata(repositoryInterface);
            return getRepositoryInformation(metadata, fragments);
        }

        @Override
        public <T, ID> EntityInformation<T, ID> getEntityInformation(Class<T> domainClass) {
            throw new UnsupportedOperationException("EntityInformation is not required for this test");
        }

        @Override
        protected Object getTargetRepository(RepositoryInformation metadata) {
            return new SampleRepositoryBase();
        }

        @Override
        protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
            return SampleRepositoryBase.class;
        }
    }
}
