/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import com.datical.liquibase.ext.storedlogic.function.change.CreateFunctionChange;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.changelog.RanChangeSet;
import liquibase.util.ValidatingVisitorUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ValidatingVisitorUtilTest {

    @Test
    void appliesLiquibase421CreateFunctionChecksumCompatibilityFlag() {
        DatabaseChangeLog databaseChangeLog = new DatabaseChangeLog("db/changelog.xml");
        ChangeSet changeSet = new ChangeSet("create-function", "test", false, false,
                databaseChangeLog.getFilePath(), null, null, databaseChangeLog);
        CreateFunctionChange change = new CreateFunctionChange();
        changeSet.addChange(change);

        RanChangeSet ranChangeSet = new RanChangeSet();
        ranChangeSet.setLiquibaseVersion("4.21.1");

        boolean checksumIssue = ValidatingVisitorUtil.isChecksumIssue(changeSet, ranChangeSet,
                databaseChangeLog, null);

        assertThat(checksumIssue).isTrue();
        assertThat(change.isUseChecksumV8ForLiquibase421x()).isTrue();
    }
}
