/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryComposition.RepositoryFragments;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

public class DefaultRepositoryInformationTest {

    private final ExposingRepositoryFactory factory = new ExposingRepositoryFactory();

    @Test
    void discoversQueryMethodsDeclaredOnRepositoryInterface() {
        RepositoryInformation information = factory.informationFor(PersonRepository.class, RepositoryFragments.empty());

        assertThat(information.getQueryMethods())
                .extracting(Method::getName)
                .contains("findByName");
    }

    @Test
    void detectsCustomMethodsBackedByRepositoryFragments() {
        RepositoryInformation information = factory.informationFor(CustomPersonRepository.class,
                RepositoryFragments.just(new CustomPersonRepositoryImpl()));

        assertThat(information.hasCustomMethod()).isTrue();
    }

    public interface PersonRepository extends Repository<Person, Long> {

        Person findByName(String name);
    }

    public interface CustomPersonRepository extends Repository<Person, Long>, CustomPersonOperations {
    }

    public interface CustomPersonOperations {

        long customCount();
    }

    public static class CustomPersonRepositoryImpl implements CustomPersonOperations {

        @Override
        public long customCount() {
            return 1L;
        }
    }

    public static class Person {

        private Long id;
        private String name;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    private static class ExposingRepositoryFactory extends RepositoryFactorySupport {

        RepositoryInformation informationFor(Class<?> repositoryInterface, RepositoryFragments fragments) {
            return getRepositoryInformation(getRepositoryMetadata(repositoryInterface), fragments);
        }

        @Override
        public <T, ID> EntityInformation<T, ID> getEntityInformation(Class<T> domainClass) {
            throw new UnsupportedOperationException("Entity information is not required for repository metadata tests");
        }

        @Override
        protected Object getTargetRepository(RepositoryInformation metadata) {
            throw new UnsupportedOperationException("Repository proxy creation is not required for metadata tests");
        }

        @Override
        protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
            return Object.class;
        }
    }
}
