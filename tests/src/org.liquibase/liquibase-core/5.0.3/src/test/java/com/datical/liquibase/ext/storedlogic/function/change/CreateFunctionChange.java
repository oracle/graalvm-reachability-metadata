/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.datical.liquibase.ext.storedlogic.function.change;

import liquibase.change.AbstractChange;
import liquibase.change.DatabaseChange;
import liquibase.database.Database;
import liquibase.statement.SqlStatement;

@DatabaseChange(
        name = "createFunction",
        description = "Test create function change used for checksum compatibility coverage",
        priority = 1
)
public class CreateFunctionChange extends AbstractChange {

    private boolean useChecksumV8ForLiquibase421x;

    public boolean isUseChecksumV8ForLiquibase421x() {
        return useChecksumV8ForLiquibase421x;
    }

    public void setUseChecksumV8ForLiquibase421x(boolean useChecksumV8ForLiquibase421x) {
        this.useChecksumV8ForLiquibase421x = useChecksumV8ForLiquibase421x;
    }

    @Override
    public String getConfirmationMessage() {
        return "Function created";
    }

    @Override
    public SqlStatement[] generateStatements(Database database) {
        return new SqlStatement[0];
    }
}
