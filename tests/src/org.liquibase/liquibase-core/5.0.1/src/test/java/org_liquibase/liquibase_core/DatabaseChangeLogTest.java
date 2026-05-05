/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.changelog.IncludeAllFilter;
import liquibase.database.core.H2Database;
import liquibase.resource.DirectoryResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DatabaseChangeLogTest {

    @TempDir
    Path changelogDirectory;

    @Test
    void includeAllInstantiatesConfiguredFilterAndComparator() throws Exception {
        TrackingIncludeAllFilter.reset();
        writeChangelog("root.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog
                        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                                            http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                    <includeAll path="includes"
                                filter="%s"
                                resourceComparator="%s"/>
                </databaseChangeLog>
                """.formatted(TrackingIncludeAllFilter.class.getName(), ReverseResourceComparator.class.getName()));
        writeChangelog("includes/01-first.xml", includedChangeLog("first"));
        writeChangelog("includes/02-second.xml", includedChangeLog("second"));

        DatabaseChangeLog changeLog;
        try (DirectoryResourceAccessor resourceAccessor = new DirectoryResourceAccessor(changelogDirectory)) {
            Liquibase liquibase = new Liquibase("root.xml", resourceAccessor, new H2Database());
            changeLog = liquibase.getDatabaseChangeLog();
        }

        assertThat(TrackingIncludeAllFilter.instances).isEqualTo(1);
        assertThat(TrackingIncludeAllFilter.includedPaths)
                .containsExactlyInAnyOrder("includes/01-first.xml", "includes/02-second.xml");
        assertThat(changeLog.getChangeSets())
                .extracting(ChangeSet::getId)
                .containsExactly("second", "first");
    }

    @Test
    void determineResourceComparatorInstantiatesConfiguredComparator() {
        DatabaseChangeLog changeLog = new DatabaseChangeLog();

        Comparator<String> comparator = changeLog.determineResourceComparator(
                ReverseResourceComparator.class.getName());

        assertThat(comparator.compare("b", "a")).isLessThan(0);
    }

    private void writeChangelog(String relativePath, String content) throws Exception {
        Path path = changelogDirectory.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private static String includedChangeLog(String id) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog
                        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                                            http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                    <changeSet id="%s" author="test">
                        <comment>included by includeAll</comment>
                    </changeSet>
                </databaseChangeLog>
                """.formatted(id);
    }

    public static final class TrackingIncludeAllFilter implements IncludeAllFilter {
        private static int instances;
        private static List<String> includedPaths = new ArrayList<>();

        public TrackingIncludeAllFilter() {
            instances++;
        }

        @Override
        public boolean include(String changeLogPath) {
            includedPaths.add(changeLogPath);
            return true;
        }

        private static void reset() {
            instances = 0;
            includedPaths = new ArrayList<>();
        }
    }

    public static final class ReverseResourceComparator implements Comparator<String> {
        @Override
        public int compare(String firstPath, String secondPath) {
            return secondPath.compareTo(firstPath);
        }
    }
}
