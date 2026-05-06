/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.sql.visitor.AppendSqlVisitor;
import liquibase.sql.visitor.PrependSqlVisitor;
import liquibase.sql.visitor.RegExpReplaceSqlVisitor;
import liquibase.sql.visitor.ReplaceSqlVisitor;
import liquibase.sql.visitor.SqlVisitor;
import liquibase.sql.visitor.SqlVisitorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SqlVisitorFactoryTest {

    @Test
    void createsRegisteredSqlVisitorInstances() {
        SqlVisitorFactory factory = SqlVisitorFactory.getInstance();

        SqlVisitor appendVisitor = factory.create("append");
        SqlVisitor secondAppendVisitor = factory.create("append");
        SqlVisitor prependVisitor = factory.create("prepend");
        SqlVisitor replaceVisitor = factory.create("replace");
        SqlVisitor regExpReplaceVisitor = factory.create("regExpReplace");
        SqlVisitor unknownVisitor = factory.create("unknownSqlVisitor");

        assertThat(appendVisitor).isInstanceOf(AppendSqlVisitor.class);
        assertThat(secondAppendVisitor).isInstanceOf(AppendSqlVisitor.class);
        assertThat(secondAppendVisitor).isNotSameAs(appendVisitor);
        assertThat(prependVisitor).isInstanceOf(PrependSqlVisitor.class);
        assertThat(replaceVisitor).isInstanceOf(ReplaceSqlVisitor.class);
        assertThat(regExpReplaceVisitor).isInstanceOf(RegExpReplaceSqlVisitor.class);
        assertThat(unknownVisitor).isNull();

        AppendSqlVisitor appendSqlVisitor = (AppendSqlVisitor) appendVisitor;
        appendSqlVisitor.setValue(";");
        assertThat(appendSqlVisitor.modifySql("select 1", null)).isEqualTo("select 1;");

        PrependSqlVisitor prependSqlVisitor = (PrependSqlVisitor) prependVisitor;
        prependSqlVisitor.setValue("/* query */ ");
        assertThat(prependSqlVisitor.modifySql("select 1", null)).isEqualTo("/* query */ select 1");

        ReplaceSqlVisitor replaceSqlVisitor = (ReplaceSqlVisitor) replaceVisitor;
        replaceSqlVisitor.setReplace("table_a");
        replaceSqlVisitor.setWith("table_b");
        assertThat(replaceSqlVisitor.modifySql("select * from table_a", null)).isEqualTo("select * from table_b");

        RegExpReplaceSqlVisitor regExpReplaceSqlVisitor = (RegExpReplaceSqlVisitor) regExpReplaceVisitor;
        regExpReplaceSqlVisitor.setReplace("\\s+");
        regExpReplaceSqlVisitor.setWith(" ");
        assertThat(regExpReplaceSqlVisitor.modifySql("select    *   from table_b", null))
                .isEqualTo("select * from table_b");
    }
}
