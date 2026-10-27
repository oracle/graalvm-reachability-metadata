/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.List;
import java.util.function.Consumer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.annotations.TenantId;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Hibernate discriminator-based multitenancy to isolate projects belonging to different workspaces.
@Timeout(60)
public class WorkspaceProjectTenancyTest {

    @Test
    public void assignsWorkspaceOwnershipAndKeepsProjectListsAndEditsTenantScoped() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(Project.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:workspace_project_tenancy;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.cache.use_second_level_cache", "false")
                .setProperty("jakarta.persistence.validation.mode", "none");

        try (SessionFactory factory = configuration.buildSessionFactory()) {
            inWorkspace(factory, "alpine", session -> {
                session.persist(new Project(1L, "ROADMAP", "Alpine launch"));
                session.persist(new Project(2L, "DOCS", "Alpine handbook"));
            });
            inWorkspace(factory, "harbor", session -> {
                // Project codes are unique within a workspace, not across the application.
                session.persist(new Project(3L, "ROADMAP", "Harbor launch"));
            });

            inWorkspace(factory, "alpine", session -> {
                List<Project> projects = session.createQuery(
                        "from WorkspaceProject p order by p.code", Project.class).getResultList();
                assertEquals(List.of("DOCS", "ROADMAP"), projects.stream().map(project -> project.code).toList());
                assertEquals(List.of("alpine", "alpine"),
                        projects.stream().map(project -> project.workspace).toList());
                assertEquals(2L, session.createQuery(
                        "select count(p) from WorkspaceProject p", Long.class).getSingleResult());

                Project roadmap = session.createQuery(
                        "from WorkspaceProject p where p.code = :code", Project.class)
                        .setParameter("code", "ROADMAP").getSingleResult();
                assertEquals("Alpine launch", roadmap.title);
                roadmap.title = "Alpine launch approved";
            });

            inWorkspace(factory, "harbor", session -> {
                List<Project> projects = session.createQuery(
                        "from WorkspaceProject p order by p.code", Project.class).getResultList();
                assertEquals(1, projects.size());
                assertEquals("harbor", projects.get(0).workspace);
                assertEquals("ROADMAP", projects.get(0).code);
                assertEquals("Harbor launch", projects.get(0).title);
                assertEquals(List.of(), session.createQuery(
                        "from WorkspaceProject p where p.id = :id", Project.class)
                        .setParameter("id", 1L).getResultList());
                assertEquals(1L, session.createQuery(
                        "select count(p) from WorkspaceProject p", Long.class).getSingleResult());
            });

            inWorkspace(factory, "alpine", session -> assertEquals(
                    List.of("Alpine handbook", "Alpine launch approved"), session.createQuery(
                            "select p.title from WorkspaceProject p order by p.code", String.class)
                            .getResultList()));
            inWorkspace(factory, "new-workspace", session -> assertEquals(List.of(), session.createQuery(
                    "from WorkspaceProject p", Project.class).getResultList()));
        }
    }

    private static void inWorkspace(SessionFactory factory, Object workspace, Consumer<Session> work) {
        try (Session session = factory.withOptions().tenantIdentifier(workspace).openSession()) {
            Transaction transaction = session.beginTransaction();
            try {
                work.accept(session);
                transaction.commit();
            } finally {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
            }
        }
    }

    @Entity(name = "WorkspaceProject")
    @Table(name = "workspace_project", uniqueConstraints = @UniqueConstraint(
            columnNames = {"workspace_key", "project_code"}))
    public static final class Project {
        @Id
        private Long id;

        @TenantId
        @Column(name = "workspace_key", nullable = false, updatable = false)
        private String workspace;

        @Column(name = "project_code", nullable = false)
        private String code;

        @Column(nullable = false)
        private String title;

        public Project() {
        }

        public Project(Long id, String code, String title) {
            this.id = id;
            this.code = code;
            this.title = title;
        }
    }
}
