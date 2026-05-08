/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_keyvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.keyvalue.annotation.KeySpace;
import org.springframework.data.keyvalue.core.IdentifierGenerator;
import org.springframework.data.keyvalue.core.KeyValueTemplate;
import org.springframework.data.keyvalue.core.SpelCriteria;
import org.springframework.data.keyvalue.core.event.KeyValueEvent;
import org.springframework.data.keyvalue.core.mapping.KeyValuePersistentEntity;
import org.springframework.data.keyvalue.core.mapping.context.KeyValueMappingContext;
import org.springframework.data.keyvalue.core.query.KeyValueQuery;
import org.springframework.data.keyvalue.repository.KeyValueRepository;
import org.springframework.data.keyvalue.repository.support.KeyValueRepositoryFactory;
import org.springframework.data.map.MapKeyValueAdapter;
import org.springframework.data.util.CloseableIterator;
import org.springframework.data.util.TypeInformation;
import org.springframework.expression.spel.standard.SpelExpressionParser;

public class Spring_data_keyvalueTest {

    @Test
    void keyValueTemplateStoresQueriesSortsRangesAndDeletesEntities() throws Exception {
        KeyValueTemplate template = new KeyValueTemplate(new MapKeyValueAdapter());

        try {
            Person ada = new Person("p1", "Ada", "Lovelace", 36, true);
            Person grace = new Person("p2", "Grace", "Hopper", 85, true);
            Person alan = new Person("p3", "Alan", "Turing", 41, false);
            Person barbara = new Person("p4", "Barbara", "Liskov", 27, true);

            assertThat(template.insert(ada)).isSameAs(ada);
            template.insert(grace);
            template.insert(alan);
            template.insert(barbara);

            assertThatThrownBy(() -> template.insert(new Person("p1", "Duplicate", "Person", 1, false)))
                    .isInstanceOf(DuplicateKeyException.class);

            Optional<Person> found = template.findById("p2", Person.class);
            assertThat(found).hasValueSatisfying(person -> assertThat(person.getFirstName()).isEqualTo("Grace"));
            assertThat(template.count(Person.class)).isEqualTo(4);

            KeyValueQuery<SpelCriteria> activeAdults = new KeyValueQuery<>(new SpelCriteria(
                    new SpelExpressionParser().parseRaw("age >= 30 and active == true")))
                            .orderBy(Sort.by("lastName"));
            assertThat(idsOf(template.find(activeAdults, Person.class))).containsExactly("p2", "p1");
            assertThat(template.count(activeAdults, Person.class)).isEqualTo(2);
            assertThat(template.exists(activeAdults, Person.class)).isTrue();

            assertThat(idsOf(template.findAll(Sort.by(Sort.Direction.DESC, "age"), Person.class)))
                    .containsExactly("p2", "p3", "p1", "p4");
            assertThat(idsOf(template.findInRange(1, 2, Sort.by("age"), Person.class))).containsExactly("p1", "p3");

            Person updatedAda = new Person("p1", "Augusta Ada", "Lovelace", 37, true);
            assertThat(template.update(updatedAda)).isSameAs(updatedAda);
            assertThat(template.findById("p1", Person.class)).hasValueSatisfying(person -> {
                assertThat(person.getFirstName()).isEqualTo("Augusta Ada");
                assertThat(person.getAge()).isEqualTo(37);
            });

            assertThat(template.delete("p3", Person.class)).isSameAs(alan);
            assertThat(template.findById("p3", Person.class)).isEmpty();

            template.delete(Person.class);
            assertThat(template.count(Person.class)).isZero();
        } finally {
            template.destroy();
        }
    }

    @Test
    void keyValueTemplateGeneratesIdentifiersForEntitiesWithoutIds() throws Exception {
        IdentifierGenerator identifierGenerator = new IdentifierGenerator() {
            private int nextId = 1;

            @Override
            public <T> T generateIdentifierOfType(TypeInformation<T> identifierType) {
                assertThat(identifierType.getType()).isEqualTo(String.class);
                return identifierType.getType().cast("generated-person-" + nextId++);
            }
        };
        KeyValueTemplate template = new KeyValueTemplate(new MapKeyValueAdapter(), new KeyValueMappingContext<>(),
                identifierGenerator);

        try {
            Person ada = new Person(null, "Ada", "Lovelace", 36, true);
            Person grace = new Person(null, "Grace", "Hopper", 85, true);

            assertThat(template.insert(ada)).isSameAs(ada);
            assertThat(template.insert(grace)).isSameAs(grace);

            assertThat(ada.getId()).isEqualTo("generated-person-1");
            assertThat(grace.getId()).isEqualTo("generated-person-2");
            assertThat(template.findById(ada.getId(), Person.class)).hasValueSatisfying(person -> {
                assertThat(person).isSameAs(ada);
                assertThat(person.getFirstName()).isEqualTo("Ada");
            });
            assertThat(template.findById(grace.getId(), Person.class)).hasValueSatisfying(person -> {
                assertThat(person).isSameAs(grace);
                assertThat(person.getFirstName()).isEqualTo("Grace");
            });
        } finally {
            template.destroy();
        }
    }

    @Test
    void keyValueTemplateUsesFallbackKeyspaceResolverForUnannotatedEntities() throws Exception {
        Map<String, Map<Object, Object>> backingStore = new ConcurrentHashMap<>();
        KeyValueMappingContext<?, ?> mappingContext = new KeyValueMappingContext<>();
        mappingContext.setFallbackKeySpaceResolver(type -> "books");
        KeyValueTemplate template = new KeyValueTemplate(new MapKeyValueAdapter(backingStore), mappingContext);

        try {
            KeyValuePersistentEntity<?, ?> bookEntity = mappingContext.getRequiredPersistentEntity(Book.class);
            assertThat(bookEntity.getKeySpace()).isEqualTo("books");

            Book dune = new Book("b1", "Dune", "Frank Herbert");
            assertThat(template.insert(dune)).isSameAs(dune);

            assertThat(backingStore).containsOnlyKeys("books");
            assertThat(backingStore.get("books")).containsEntry("b1", dune);
            assertThat(template.findById("b1", Book.class)).hasValueSatisfying(book -> {
                assertThat(book).isSameAs(dune);
                assertThat(book.getTitle()).isEqualTo("Dune");
            });
        } finally {
            template.destroy();
        }
    }

    @Test
    void keyValueTemplatePublishesFilteredLifecycleEvents() throws Exception {
        KeyValueTemplate template = new KeyValueTemplate(new MapKeyValueAdapter());
        List<KeyValueEvent<?>> events = new ArrayList<>();
        Set<Class<? extends KeyValueEvent>> publishedTypes = new LinkedHashSet<>();
        publishedTypes.add(KeyValueEvent.BeforeInsertEvent.class);
        publishedTypes.add(KeyValueEvent.AfterUpdateEvent.class);
        publishedTypes.add(KeyValueEvent.AfterDeleteEvent.class);

        template.setApplicationEventPublisher(new ApplicationEventPublisher() {
            @Override
            public void publishEvent(Object event) {
                events.add((KeyValueEvent<?>) event);
            }
        });
        template.setEventTypesToPublish(publishedTypes);

        try {
            Person original = new Person("p10", "Katherine", "Johnson", 101, true);
            Person replacement = new Person("p10", "Katherine", "Goble Johnson", 101, true);

            template.insert(original);
            template.findById("p10", Person.class);
            template.update(replacement);
            template.delete("p10", Person.class);
            template.delete(Person.class);

            assertThat(events).extracting(Object::getClass).containsExactly(
                    KeyValueEvent.BeforeInsertEvent.class,
                    KeyValueEvent.AfterUpdateEvent.class,
                    KeyValueEvent.AfterDeleteEvent.class);

            KeyValueEvent.BeforeInsertEvent<?> beforeInsert = (KeyValueEvent.BeforeInsertEvent<?>) events.get(0);
            assertThat(beforeInsert.getKey()).isEqualTo("p10");
            assertThat(beforeInsert.getKeyspace()).isEqualTo("people");
            assertThat(beforeInsert.getType()).isEqualTo(Person.class);
            assertThat(beforeInsert.getPayload()).isSameAs(original);

            KeyValueEvent.AfterUpdateEvent<?> afterUpdate = (KeyValueEvent.AfterUpdateEvent<?>) events.get(1);
            assertThat(afterUpdate.before()).isSameAs(original);
            assertThat(afterUpdate.after()).isSameAs(replacement);

            KeyValueEvent.AfterDeleteEvent<?> afterDelete = (KeyValueEvent.AfterDeleteEvent<?>) events.get(2);
            assertThat(afterDelete.getPayload()).isSameAs(replacement);
        } finally {
            template.destroy();
        }
    }

    @Test
    void mapKeyValueAdapterExposesKeyspaceOperationsAndCloseableEntries() throws Exception {
        Map<String, Map<Object, Object>> backingStore = new ConcurrentHashMap<>();
        MapKeyValueAdapter adapter = new MapKeyValueAdapter(backingStore);
        Person ada = new Person("p1", "Ada", "Lovelace", 36, true);
        Person grace = new Person("p2", "Grace", "Hopper", 85, true);
        Person olderAda = new Person("p1", "Ada", "Byron", 37, true);

        try {
            assertThat(adapter.put(ada.getId(), ada, "people")).isNull();
            assertThat(adapter.put(grace.getId(), grace, "people")).isNull();
            assertThat(adapter.put(olderAda.getId(), olderAda, "people")).isSameAs(ada);

            assertThat(adapter.contains("p1", "people")).isTrue();
            assertThat(adapter.get("p1", "people", Person.class)).isSameAs(olderAda);
            assertThat(adapter.count("people")).isEqualTo(2);
            assertThat(adapter.getAllOf("people")).containsExactlyInAnyOrder(olderAda, grace);

            CloseableIterator<Map.Entry<Object, Object>> entries = adapter.entries("people");
            try {
                List<Object> keys = new ArrayList<>();
                while (entries.hasNext()) {
                    keys.add(entries.next().getKey());
                }
                assertThat(keys).containsExactlyInAnyOrder("p1", "p2");
            } finally {
                entries.close();
            }

            assertThat(adapter.delete("p2", "people", Person.class)).isSameAs(grace);
            assertThat(adapter.contains("p2", "people")).isFalse();

            adapter.deleteAllOf("people");
            assertThat(adapter.count("people")).isZero();

            adapter.put("other", "value", "other-space");
            adapter.clear();
            assertThat(backingStore).isEmpty();
        } finally {
            adapter.destroy();
        }
    }

    @Test
    void repositoryFactoryCreatesRepositoryWithCrudSortingPagingAndDerivedQueries() throws Exception {
        KeyValueTemplate template = new KeyValueTemplate(new MapKeyValueAdapter());
        PersonRepository repository = new KeyValueRepositoryFactory(template).getRepository(PersonRepository.class);

        try {
            Person ada = new Person("p1", "Ada", "Lovelace", 36, true);
            Person grace = new Person("p2", "Grace", "Hopper", 85, true);
            Person alan = new Person("p3", "Alan", "Turing", 41, false);
            Person barbara = new Person("p4", "Barbara", "Liskov", 27, true);

            Iterable<Person> saved = repository.saveAll(Arrays.asList(ada, grace, alan, barbara));
            assertThat(idsOf(saved)).containsExactly("p1", "p2", "p3", "p4");
            assertThat(repository.count()).isEqualTo(4);
            assertThat(repository.existsById("p2")).isTrue();
            assertThat(repository.findById("p3")).hasValueSatisfying(person -> assertThat(person).isSameAs(alan));

            assertThat(idsOf(repository.findAll(Sort.by(Sort.Direction.ASC, "age"))))
                    .containsExactly("p4", "p1", "p3", "p2");

            Page<Person> oldestTwo = repository.findAll(PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "age")));
            assertThat(oldestTwo.getTotalElements()).isEqualTo(4);
            assertThat(oldestTwo.getTotalPages()).isEqualTo(2);
            assertThat(idsOf(oldestTwo.getContent())).containsExactly("p2", "p3");

            assertThat(idsOf(repository.findByLastName("Lovelace"))).containsExactly("p1");
            assertThat(idsOf(repository.findByActiveTrueOrderByAgeDesc())).containsExactly("p2", "p1", "p4");
            assertThat(idsOf(repository.findByAgeBetween(30, 90, Sort.by("firstName"))))
                    .containsExactly("p1", "p3", "p2");
            assertThat(repository.existsByLastName("Hopper")).isTrue();

            repository.deleteById("p3");
            assertThat(repository.findById("p3")).isEmpty();

            repository.deleteAllById(Arrays.asList("p1", "p2"));
            assertThat(idsOf(repository.findAll())).containsExactly("p4");

            repository.deleteAll();
            assertThat(repository.count()).isZero();
        } finally {
            template.destroy();
        }
    }

    private static List<String> idsOf(Iterable<Person> people) {
        List<String> ids = new ArrayList<>();
        for (Person person : people) {
            ids.add(person.getId());
        }
        return ids;
    }

    public interface PersonRepository extends KeyValueRepository<Person, String> {
        List<Person> findByLastName(String lastName);

        List<Person> findByActiveTrueOrderByAgeDesc();

        List<Person> findByAgeBetween(int lowerBound, int upperBound, Sort sort);

        boolean existsByLastName(String lastName);
    }

    public static class Book {
        @Id
        private String id;
        private String title;
        private String author;

        public Book() {
        }

        Book(String id, String title, String author) {
            this.id = id;
            this.title = title;
            this.author = author;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }
    }

    @KeySpace("people")
    public static class Person {
        @Id
        private String id;
        private String firstName;
        private String lastName;
        private int age;
        private boolean active;

        public Person() {
        }

        Person(String id, String firstName, String lastName, int age, boolean active) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
            this.age = age;
            this.active = active;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}
