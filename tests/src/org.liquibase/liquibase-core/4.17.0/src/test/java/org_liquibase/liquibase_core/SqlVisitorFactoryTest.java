/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.database.Database;
import liquibase.database.core.H2Database;
import liquibase.sql.visitor.AppendSqlVisitor;
import liquibase.sql.visitor.PrependSqlVisitor;
import liquibase.sql.visitor.RegExpReplaceSqlVisitor;
import liquibase.sql.visitor.ReplaceSqlVisitor;
import liquibase.sql.visitor.SqlVisitorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SqlVisitorFactoryTest {

    @Test
    void createsBuiltInSqlVisitorsByTagName() {
        final SqlVisitorFactory factory = SqlVisitorFactory.getInstance();
        final Database database = new H2Database();

        final AppendSqlVisitor append = (AppendSqlVisitor) factory.create("append");
        append.setValue(";");
        assertThat(append.modifySql("select 1", database)).isEqualTo("select 1;");

        final PrependSqlVisitor prepend = (PrependSqlVisitor) factory.create("prepend");
        prepend.setValue("-- generated\n");
        assertThat(prepend.modifySql("select 1", database)).isEqualTo("-- generated\nselect 1");

        final ReplaceSqlVisitor replace = (ReplaceSqlVisitor) factory.create("replace");
        replace.setReplace("old_table");
        replace.setWith("new_table");
        assertThat(replace.modifySql("select * from old_table", database)).isEqualTo("select * from new_table");

        final RegExpReplaceSqlVisitor regExpReplace = (RegExpReplaceSqlVisitor) factory.create("regExpReplace");
        regExpReplace.setReplace("\\s+");
        regExpReplace.setWith(" ");
        assertThat(regExpReplace.modifySql("select   *\nfrom test_table", database))
                .isEqualTo("select * from test_table");
    }

    @Test
    void returnsNullForUnknownSqlVisitorTag() {
        final SqlVisitorFactory factory = SqlVisitorFactory.getInstance();

        assertThat(factory.create("unknownVisitor")).isNull();
    }
}
