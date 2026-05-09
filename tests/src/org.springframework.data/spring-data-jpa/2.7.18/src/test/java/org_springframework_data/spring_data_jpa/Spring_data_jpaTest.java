/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_jpa;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.convert.threeten.Jsr310JpaConverters;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.query.Param;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.Database;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_data_jpaTest {

    @Test
    void repositoryProxySupportsCrudQueriesSpecificationsExamplesPagingAndProjections() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(JpaTestConfiguration.class)) {
            JpaSampleUserRepository repository = context.getBean(JpaSampleUserRepository.class);

            JpaSampleUser ada = user("ada", "ada@example.org", 36, true, JpaSampleRole.ADMIN);
            ada.addPurchase(new JpaSamplePurchase("compiler", 120));
            ada.addPurchase(new JpaSamplePurchase("notebook", 15));
            JpaSampleUser grace = user("grace", "grace@example.org", 17, true, JpaSampleRole.USER);
            grace.addPurchase(new JpaSamplePurchase("manual", 25));
            JpaSampleUser linus = user("linus", "linus@example.org", 54, true, JpaSampleRole.USER);
            JpaSampleUser inactive = user("inactive", "inactive@example.org", 28, false, JpaSampleRole.USER);

            repository.saveAll(Arrays.asList(ada, grace, linus, inactive));
            repository.flush();

            assertThat(repository.findByActiveTrueOrderByUsernameAsc())
                    .extracting(JpaSampleUser::getUsername)
                    .containsExactly("ada", "grace", "linus");
            assertThat(repository.findFirstByEmail("ada@example.org"))
                    .get()
                    .extracting(JpaSampleUser::getAge)
                    .isEqualTo(36);
            assertThat(repository.findUsernamesAtLeastAge(18)).containsExactly("ada", "inactive", "linus");

            Specification<JpaSampleUser> activeAdults = (root, query, criteriaBuilder) -> criteriaBuilder.and(
                    criteriaBuilder.isTrue(root.get("active")),
                    criteriaBuilder.greaterThanOrEqualTo(root.get("age"), 18));
            assertThat(repository.findAll(activeAdults, Sort.by("username")))
                    .extracting(JpaSampleUser::getUsername)
                    .containsExactly("ada", "linus");

            JpaSampleUser probe = new JpaSampleUser();
            probe.setEmail("grace@example.org");
            ExampleMatcher matcher = ExampleMatcher.matching()
                    .withIgnorePaths("id", "username", "age", "active", "role", "purchases");
            assertThat(repository.findAll(Example.of(probe, matcher)))
                    .extracting(JpaSampleUser::getUsername)
                    .containsExactly("grace");

            Page<JpaSampleUser> userPage = repository.findByRole(
                    JpaSampleRole.USER, PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "age")));
            assertThat(userPage.getTotalElements()).isEqualTo(3);
            assertThat(userPage.getContent())
                    .extracting(JpaSampleUser::getUsername)
                    .containsExactly("linus", "inactive");

            assertThat(repository.findByRoleOrderByUsernameAsc(JpaSampleRole.USER))
                    .extracting(UsernameOnly::getUsername)
                    .containsExactly("grace", "inactive", "linus");
            assertThat(repository.findWithPurchasesByUsername("ada"))
                    .get()
                    .extracting(user -> user.getPurchases().size())
                    .isEqualTo(2);

            int updatedRows = repository.deactivateYoungerThan(18);
            assertThat(updatedRows).isEqualTo(1);
            assertThat(repository.findFirstByEmail("grace@example.org"))
                    .get()
                    .extracting(JpaSampleUser::isActive)
                    .isEqualTo(false);
        }
    }

    @Test
    void nativeSqlQueryMethodsMapScalarResults() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(JpaTestConfiguration.class)) {
            JpaSampleUserRepository repository = context.getBean(JpaSampleUserRepository.class);

            repository.saveAll(Arrays.asList(
                    user("ada", "ada-native@example.org", 36, true, JpaSampleRole.ADMIN),
                    user("grace", "grace-native@example.org", 17, false, JpaSampleRole.USER),
                    user("linus", "linus-native@example.org", 54, true, JpaSampleRole.USER)));
            repository.flush();

            assertThat(repository.findActiveUsernamesWithNativeQuery()).containsExactly("ada", "linus");
        }
    }

    @Test
    void domainSortHelpersAndJavaTimeConvertersExposeStablePublicBehavior() {
        JpaSort unsafeSort = JpaSort.unsafe(Sort.Direction.DESC, "LENGTH(username)")
                .andUnsafe(Sort.Direction.ASC, "LOWER(email)");

        assertThat(unsafeSort).extracting(Sort.Order::getProperty)
                .containsExactly("LENGTH(username)", "LOWER(email)");
        assertThat(unsafeSort.getOrderFor("LENGTH(username)").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(unsafeSort.getOrderFor("LOWER(email)").getDirection()).isEqualTo(Sort.Direction.ASC);

        Jsr310JpaConverters.LocalDateConverter localDateConverter = new Jsr310JpaConverters.LocalDateConverter();
        LocalDate localDate = LocalDate.of(2024, 2, 29);
        Date dateColumn = localDateConverter.convertToDatabaseColumn(localDate);
        assertThat(localDateConverter.convertToEntityAttribute(dateColumn)).isEqualTo(localDate);

        Jsr310JpaConverters.LocalDateTimeConverter localDateTimeConverter =
                new Jsr310JpaConverters.LocalDateTimeConverter();
        LocalDateTime localDateTime = LocalDateTime.of(2024, 2, 29, 8, 15, 30);
        Date dateTimeColumn = localDateTimeConverter.convertToDatabaseColumn(localDateTime);
        assertThat(localDateTimeConverter.convertToEntityAttribute(dateTimeColumn)).isEqualTo(localDateTime);

        Jsr310JpaConverters.LocalTimeConverter localTimeConverter = new Jsr310JpaConverters.LocalTimeConverter();
        LocalTime localTime = LocalTime.of(8, 15, 30);
        Date timeColumn = localTimeConverter.convertToDatabaseColumn(localTime);
        assertThat(localTimeConverter.convertToEntityAttribute(timeColumn)).isEqualTo(localTime);

        Jsr310JpaConverters.InstantConverter instantConverter = new Jsr310JpaConverters.InstantConverter();
        Instant instant = Instant.parse("2024-02-29T08:15:30Z");
        Date instantColumn = instantConverter.convertToDatabaseColumn(instant);
        assertThat(instantConverter.convertToEntityAttribute(instantColumn)).isEqualTo(instant);

        Jsr310JpaConverters.ZoneIdConverter zoneIdConverter = new Jsr310JpaConverters.ZoneIdConverter();
        ZoneId zoneId = ZoneId.of("Europe/Prague");
        String zoneIdColumn = zoneIdConverter.convertToDatabaseColumn(zoneId);
        assertThat(zoneIdConverter.convertToEntityAttribute(zoneIdColumn)).isEqualTo(zoneId);
    }

    private static JpaSampleUser user(
            String username, String email, int age, boolean active, JpaSampleRole role) {
        JpaSampleUser user = new JpaSampleUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setAge(age);
        user.setActive(active);
        user.setRole(role);
        return user;
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = Spring_data_jpaTest.class)
    public static class JpaTestConfiguration {

        @Bean
        public DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:spring-data-jpa-" + System.nanoTime()
                    + ";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
            adapter.setDatabase(Database.H2);
            adapter.setGenerateDdl(true);
            adapter.setShowSql(false);

            Map<String, Object> properties = new HashMap<>();
            properties.put("hibernate.hbm2ddl.auto", "create-drop");
            properties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
            properties.put("hibernate.format_sql", "false");
            properties.put("hibernate.show_sql", "false");

            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setJpaVendorAdapter(adapter);
            factory.setJpaPropertyMap(properties);
            factory.setPackagesToScan(Spring_data_jpaTest.class.getPackageName());
            return factory;
        }

        @Bean
        public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }
    }
}

interface JpaSampleUserRepository extends JpaRepository<JpaSampleUser, Long>, JpaSpecificationExecutor<JpaSampleUser> {

    List<JpaSampleUser> findByActiveTrueOrderByUsernameAsc();

    Optional<JpaSampleUser> findFirstByEmail(String email);

    Page<JpaSampleUser> findByRole(JpaSampleRole role, org.springframework.data.domain.Pageable pageable);

    List<UsernameOnly> findByRoleOrderByUsernameAsc(JpaSampleRole role);

    @Query("select u.username from JpaSampleUser u where u.age >= :minimumAge order by u.username")
    List<String> findUsernamesAtLeastAge(@Param("minimumAge") int minimumAge);

    @Query(value = "select username from jpa_sample_users where active = true order by username", nativeQuery = true)
    List<String> findActiveUsernamesWithNativeQuery();

    @EntityGraph(attributePaths = "purchases")
    Optional<JpaSampleUser> findWithPurchasesByUsername(String username);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update JpaSampleUser u set u.active = false where u.age < :minimumAge")
    int deactivateYoungerThan(@Param("minimumAge") int minimumAge);
}

interface UsernameOnly {

    String getUsername();
}

enum JpaSampleRole {
    ADMIN,
    USER
}

@Entity(name = "JpaSampleUser")
@Table(name = "jpa_sample_users")
class JpaSampleUser {

    protected JpaSampleUser() {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    private String email;

    private int age;

    private boolean active;

    @Enumerated(EnumType.STRING)
    private JpaSampleRole role;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JpaSamplePurchase> purchases = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public JpaSampleRole getRole() {
        return role;
    }

    public void setRole(JpaSampleRole role) {
        this.role = role;
    }

    public List<JpaSamplePurchase> getPurchases() {
        return purchases;
    }

    public void addPurchase(JpaSamplePurchase purchase) {
        purchases.add(purchase);
        purchase.setUser(this);
    }
}

@Entity(name = "JpaSamplePurchase")
@Table(name = "jpa_sample_purchases")
class JpaSamplePurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String itemName;

    private int amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private JpaSampleUser user;

    protected JpaSamplePurchase() {
    }

    JpaSamplePurchase(String itemName, int amount) {
        this.itemName = itemName;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public String getItemName() {
        return itemName;
    }

    public int getAmount() {
        return amount;
    }

    public JpaSampleUser getUser() {
        return user;
    }

    public void setUser(JpaSampleUser user) {
        this.user = user;
    }
}
