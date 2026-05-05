/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.GlobalConfiguration;
import liquibase.Scope;
import liquibase.SupportsMethodValidationLevelsEnum;
import liquibase.change.AbstractChange;
import liquibase.change.Change;
import liquibase.change.ChangeFactory;
import liquibase.change.DatabaseChange;
import liquibase.database.Database;
import liquibase.statement.SqlStatement;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ChangeFactoryTest {

    private static final String CHANGE_NAME = "changeFactorySupportsValidationProbe";

    @Test
    void createValidatesSupportsMethodWhenMultipleExternalChangesUseSameName() {
        ChangeFactory changeFactory = Scope.getCurrentScope().getSingleton(ChangeFactory.class);
        String validationLevelKey = GlobalConfiguration.SUPPORTS_METHOD_VALIDATION_LEVEL.getKey();
        String originalValidationLevel = System.getProperty(validationLevelKey);
        System.setProperty(validationLevelKey, SupportsMethodValidationLevelsEnum.WARN.name());

        try {
            changeFactory.register(new LowerPriorityProbeChange());
            changeFactory.register(new HigherPriorityProbeChange());

            Change change = changeFactory.create(CHANGE_NAME);

            assertThat(change).isInstanceOf(HigherPriorityProbeChange.class);
        } finally {
            restoreValidationLevel(originalValidationLevel);
            changeFactory.unregister(CHANGE_NAME);
        }
    }

    private static void restoreValidationLevel(String originalValidationLevel) {
        if (originalValidationLevel == null) {
            System.clearProperty(GlobalConfiguration.SUPPORTS_METHOD_VALIDATION_LEVEL.getKey());
        } else {
            System.setProperty(GlobalConfiguration.SUPPORTS_METHOD_VALIDATION_LEVEL.getKey(), originalValidationLevel);
        }
    }

    @DatabaseChange(name = CHANGE_NAME, description = "lower priority probe", priority = 1)
    public static class LowerPriorityProbeChange extends AbstractChange {
        @Override
        public String getConfirmationMessage() {
            return "OK";
        }

        @Override
        public SqlStatement[] generateStatements(Database database) {
            return new SqlStatement[0];
        }
    }

    @DatabaseChange(name = CHANGE_NAME, description = "higher priority probe", priority = 2)
    public static class HigherPriorityProbeChange extends AbstractChange {
        @Override
        public String getConfirmationMessage() {
            return "OK";
        }

        @Override
        public SqlStatement[] generateStatements(Database database) {
            return new SqlStatement[0];
        }
    }
}
