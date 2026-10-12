/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_events_jpa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.EventPublicationRepository.FailedCriteria;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.modulith.events.jpa.archiving.ArchivedJpaEventPublication;
import org.springframework.modulith.events.jpa.updating.DefaultJpaEventPublication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_modulith_events_jpaTest {

    @Test
    void autoConfigurationPersistsAndUpdatesEventPublications() {
        contextRunner("updating")
                .run((context) -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EntityManagerFactory.class);
                    assertThat(context).hasSingleBean(EventPublicationRepository.class);

                    EventPublicationRepository repository = context.getBean(EventPublicationRepository.class);
                    EntityManagerFactory entityManagerFactory = context.getBean(EntityManagerFactory.class);
                    TransactionTemplate transactions = new TransactionTemplate(
                            context.getBean(PlatformTransactionManager.class));
                    PublicationTargetIdentifier target = PublicationTargetIdentifier.of("inventory-listener");
                    SampleEvent event = new SampleEvent("item-42");
                    Instant publishedAt = Instant.parse("2025-01-02T10:15:30Z");
                    TargetEventPublication publication = TargetEventPublication.of(event, target, publishedAt);

                    transactions.executeWithoutResult((status) -> repository.create(publication));

                    try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
                        assertThat(entityManager.find(DefaultJpaEventPublication.class, publication.getIdentifier()))
                                .isNotNull();
                    }

                    List<TargetEventPublication> incomplete = transactions
                            .execute((status) -> repository.findIncompletePublications());
                    assertThat(incomplete).hasSize(1);

                    TargetEventPublication restored = incomplete.getFirst();
                    assertThat(restored.getIdentifier()).isEqualTo(publication.getIdentifier());
                    assertThat(restored.getEvent()).isEqualTo(event);
                    assertThat(restored.getTargetIdentifier()).isEqualTo(target);
                    assertThat(restored.getPublicationDate()).isEqualTo(publishedAt);
                    assertThat(restored.getStatus()).isEqualTo(Status.PUBLISHED);
                    assertThat(restored.getCompletionAttempts()).isEqualTo(1);
                    assertThat(restored.getLastResubmissionDate()).isEqualTo(publishedAt);
                    assertThat(restored.getCompletionDate()).isEmpty();

                    Optional<TargetEventPublication> matchingPublication = transactions.execute((status) -> repository
                            .findIncompletePublicationsByEventAndTargetIdentifier(event, target));
                    assertThat(matchingPublication).contains(restored);

                    List<TargetEventPublication> olderPublications = transactions.execute((status) -> repository
                            .findIncompletePublicationsPublishedBefore(publishedAt.plusSeconds(1)));
                    assertThat(olderPublications)
                            .extracting(TargetEventPublication::getIdentifier)
                            .containsExactly(publication.getIdentifier());

                    Integer publishedCount = transactions
                            .execute((status) -> repository.countByStatus(Status.PUBLISHED));
                    assertThat(publishedCount).isEqualTo(1);

                    transactions.executeWithoutResult(
                            (status) -> repository.markProcessing(publication.getIdentifier()));
                    List<TargetEventPublication> processingPublications = transactions
                            .execute((status) -> repository.findByStatus(Status.PROCESSING));
                    assertThat(processingPublications)
                            .extracting(TargetEventPublication::getIdentifier)
                            .containsExactly(publication.getIdentifier());

                    transactions.executeWithoutResult((status) -> repository.markFailed(publication.getIdentifier()));
                    List<TargetEventPublication> failedPublications = transactions.execute(
                            (status) -> repository.findFailedPublications(FailedCriteria.ALL
                                    .withPublicationsPublishedBefore(publishedAt.plusSeconds(1))
                                    .withItemsToRead(1)));
                    assertThat(failedPublications)
                            .extracting(TargetEventPublication::getIdentifier)
                            .containsExactly(publication.getIdentifier());

                    Instant resubmittedAt = publishedAt.plusSeconds(30);
                    Boolean resubmittedFirst = transactions.execute((status) -> repository
                            .markResubmitted(publication.getIdentifier(), resubmittedAt));
                    Boolean resubmittedAgain = transactions.execute((status) -> repository
                            .markResubmitted(publication.getIdentifier(), resubmittedAt.plusSeconds(1)));
                    assertThat(resubmittedFirst).isTrue();
                    assertThat(resubmittedAgain).isFalse();

                    TargetEventPublication resubmitted = transactions.execute(
                            (status) -> repository.findByStatus(Status.RESUBMITTED).getFirst());
                    assertThat(resubmitted.getStatus()).isEqualTo(Status.RESUBMITTED);
                    assertThat(resubmitted.getCompletionAttempts()).isEqualTo(2);
                    assertThat(resubmitted.getLastResubmissionDate()).isEqualTo(resubmittedAt);

                    Instant completedAt = publishedAt.plusSeconds(60);
                    transactions.executeWithoutResult(
                            (status) -> repository.markCompleted(restored, completedAt));

                    List<TargetEventPublication> completed = transactions
                            .execute((status) -> repository.findCompletedPublications());
                    assertThat(completed).hasSize(1);
                    assertThat(completed.getFirst().getEvent()).isEqualTo(event);
                    assertThat(completed.getFirst().getStatus()).isEqualTo(Status.COMPLETED);
                    assertThat(completed.getFirst().getCompletionDate()).contains(completedAt);
                    Integer completedCount = transactions
                            .execute((status) -> repository.countByStatus(Status.COMPLETED));
                    assertThat(completedCount).isEqualTo(1);

                    transactions.executeWithoutResult(
                            (status) -> repository.deleteCompletedPublicationsBefore(completedAt.plusSeconds(1)));
                    List<TargetEventPublication> completedAfterDelete = transactions
                            .execute((status) -> repository.findCompletedPublications());
                    assertThat(completedAfterDelete).isEmpty();

                    TargetEventPublication removable = TargetEventPublication.of(
                            new SampleEvent("item-99"), target, publishedAt.plusSeconds(120));
                    transactions.executeWithoutResult((status) -> repository.create(removable));
                    transactions.executeWithoutResult(
                            (status) -> repository.deletePublications(List.of(removable.getIdentifier())));
                    List<TargetEventPublication> incompleteAfterDelete = transactions
                            .execute((status) -> repository.findIncompletePublications());
                    assertThat(incompleteAfterDelete).isEmpty();
                });
    }

    @Test
    void deletesPublicationsAcrossMultipleBatches() {
        contextRunner("batch-deletion")
                .run((context) -> {
                    assertThat(context).hasNotFailed();

                    EventPublicationRepository repository = context.getBean(EventPublicationRepository.class);
                    TransactionTemplate transactions = new TransactionTemplate(
                            context.getBean(PlatformTransactionManager.class));
                    PublicationTargetIdentifier target = PublicationTargetIdentifier.of("batch-listener");
                    Instant publishedAt = Instant.parse("2025-04-05T13:40:00Z");
                    int publicationCount = 101;
                    List<TargetEventPublication> publications = IntStream.range(0, publicationCount)
                            .mapToObj((index) -> TargetEventPublication.of(
                                    new SampleEvent("batch-" + index), target, publishedAt.plusSeconds(index)))
                            .toList();

                    transactions.executeWithoutResult(
                            (status) -> publications.forEach(repository::create));
                    List<TargetEventPublication> stored = transactions
                            .execute((status) -> repository.findIncompletePublications());
                    assertThat(stored).hasSize(publicationCount);

                    List<UUID> identifiers = publications.stream()
                            .map(TargetEventPublication::getIdentifier)
                            .toList();
                    transactions.executeWithoutResult(
                            (status) -> repository.deletePublications(identifiers));

                    List<TargetEventPublication> remaining = transactions
                            .execute((status) -> repository.findIncompletePublications());
                    assertThat(remaining).isEmpty();
                });
    }

    @Test
    void deleteCompletionRemovesPublication() {
        contextRunner("deleting")
                .withPropertyValues("spring.modulith.events.completion-mode=DELETE")
                .run((context) -> {
                    assertThat(context).hasNotFailed();

                    EventPublicationRepository repository = context.getBean(EventPublicationRepository.class);
                    TransactionTemplate transactions = new TransactionTemplate(
                            context.getBean(PlatformTransactionManager.class));
                    PublicationTargetIdentifier target = PublicationTargetIdentifier.of("shipping-listener");
                    SampleEvent event = new SampleEvent("shipment-3");
                    Instant publishedAt = Instant.parse("2025-03-04T12:30:00Z");
                    TargetEventPublication publication = TargetEventPublication.of(event, target, publishedAt);

                    transactions.executeWithoutResult((status) -> repository.create(publication));
                    Optional<TargetEventPublication> stored = transactions.execute((status) -> repository
                            .findIncompletePublicationsByEventAndTargetIdentifier(event, target));
                    assertThat(stored).isPresent();

                    transactions.executeWithoutResult((status) -> repository.markCompleted(
                            publication.getIdentifier(), publishedAt.plusSeconds(15)));

                    List<TargetEventPublication> incompleteAfterCompletion = transactions
                            .execute((status) -> repository.findIncompletePublications());
                    List<TargetEventPublication> completedAfterCompletion = transactions
                            .execute((status) -> repository.findCompletedPublications());
                    assertThat(incompleteAfterCompletion).isEmpty();
                    assertThat(completedAfterCompletion).isEmpty();
                });
    }

    @Test
    void archiveCompletionMovesPublicationToArchivedEntity() {
        contextRunner("archiving")
                .withPropertyValues("spring.modulith.events.completion-mode=ARCHIVE")
                .run((context) -> {
                    assertThat(context).hasNotFailed();

                    EventPublicationRepository repository = context.getBean(EventPublicationRepository.class);
                    EntityManagerFactory entityManagerFactory = context.getBean(EntityManagerFactory.class);
                    TransactionTemplate transactions = new TransactionTemplate(
                            context.getBean(PlatformTransactionManager.class));
                    PublicationTargetIdentifier target = PublicationTargetIdentifier.of("billing-listener");
                    SampleEvent event = new SampleEvent("invoice-7");
                    Instant publishedAt = Instant.parse("2025-02-03T11:20:00Z");
                    Instant completedAt = publishedAt.plusSeconds(45);
                    TargetEventPublication publication = TargetEventPublication.of(event, target, publishedAt);

                    transactions.executeWithoutResult((status) -> repository.create(publication));
                    transactions.executeWithoutResult(
                            (status) -> repository.markCompleted(event, target, completedAt));

                    List<TargetEventPublication> incomplete = transactions
                            .execute((status) -> repository.findIncompletePublications());
                    assertThat(incomplete).isEmpty();
                    List<TargetEventPublication> archived = transactions
                            .execute((status) -> repository.findCompletedPublications());
                    assertThat(archived).hasSize(1);

                    TargetEventPublication restored = archived.getFirst();
                    assertThat(restored.getIdentifier()).isEqualTo(publication.getIdentifier());
                    assertThat(restored.getEvent()).isEqualTo(event);
                    assertThat(restored.getTargetIdentifier()).isEqualTo(target);
                    assertThat(restored.getStatus()).isEqualTo(Status.COMPLETED);
                    assertThat(restored.getCompletionDate()).contains(completedAt);
                    List<TargetEventPublication> completedByStatus = transactions
                            .execute((status) -> repository.findByStatus(Status.COMPLETED));
                    assertThat(completedByStatus)
                            .extracting(TargetEventPublication::getIdentifier)
                            .containsExactly(publication.getIdentifier());

                    try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
                        assertThat(entityManager.find(DefaultJpaEventPublication.class, publication.getIdentifier()))
                                .isNull();
                        assertThat(entityManager.find(ArchivedJpaEventPublication.class, publication.getIdentifier()))
                                .isNotNull();
                    }

                    transactions.executeWithoutResult((status) -> repository.deleteCompletedPublications());
                    List<TargetEventPublication> archivedAfterDelete = transactions
                            .execute((status) -> repository.findCompletedPublications());
                    assertThat(archivedAfterDelete).isEmpty();
                });
    }

    private static ApplicationContextRunner contextRunner(String databaseName) {
        return new ApplicationContextRunner()
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues(
                        "spring.datasource.url=jdbc:hsqldb:mem:" + databaseName + ";shutdown=true",
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        "spring.datasource.driver-class-name=org.hsqldb.jdbc.JDBCDriver",
                        "spring.jpa.hibernate.ddl-auto=create-drop",
                        "spring.jpa.open-in-view=false");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    public static class TestConfiguration {

        @Bean
        EventSerializer eventSerializer() {
            return new SampleEventSerializer();
        }
    }

    public record SampleEvent(String value) {}

    private static final class SampleEventSerializer implements EventSerializer {

        @Override
        public Object serialize(Object event) {
            return ((SampleEvent) event).value();
        }

        @Override
        public <T> T deserialize(Object serialized, Class<T> type) {
            return type.cast(new SampleEvent(serialized.toString()));
        }
    }
}
