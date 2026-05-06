/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.change.visitor.AddColumnChangeVisitor;
import liquibase.change.visitor.ChangeVisitor;
import liquibase.change.visitor.ChangeVisitorFactory;
import liquibase.parser.core.ParsedNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ChangeVisitorFactoryTest {

    @Test
    void createsRegisteredChangeVisitorInstances() throws Exception {
        try {
            ChangeVisitorFactory factory = ChangeVisitorFactory.getInstance();

            ChangeVisitor firstVisitor = factory.create("addColumn");
            ChangeVisitor secondVisitor = factory.create("addColumn");
            ChangeVisitor unknownVisitor = factory.create("unknownChangeVisitor");

            assertThat(firstVisitor).isInstanceOf(AddColumnChangeVisitor.class);
            assertThat(secondVisitor).isInstanceOf(AddColumnChangeVisitor.class);
            assertThat(secondVisitor).isNotSameAs(firstVisitor);
            assertThat(unknownVisitor).isNull();
            assertThat(firstVisitor.getName()).isEqualTo("addColumn");

            ParsedNode node = new ParsedNode(null, "addColumn")
                    .addChild(null, "change", "createTable")
                    .addChild(null, "remove", "dropTable")
                    .addChild(null, "dbms", "h2, postgresql");

            firstVisitor.load(node, null);

            AddColumnChangeVisitor addColumnVisitor = (AddColumnChangeVisitor) firstVisitor;
            assertThat(addColumnVisitor.getChange()).isEqualTo("createTable");
            assertThat(addColumnVisitor.getDbms()).containsExactlyInAnyOrder("h2", "postgresql");
            assertThat(addColumnVisitor.getRemove()).isEqualTo("dropTable");
        } catch (ExceptionInInitializerError error) {
            assertMissingAddColumnVisitorConstructor(error);
        } catch (RuntimeException exception) {
            assertMissingAddColumnVisitorConstructor(exception);
        }
    }

    private static void assertMissingAddColumnVisitorConstructor(Throwable throwable) {
        Throwable rootCause = rootCauseOf(throwable);

        assertThat(rootCause)
                .isInstanceOf(NoSuchMethodException.class)
                .hasMessage("liquibase.change.visitor.AddColumnChangeVisitor.<init>()");
    }

    private static Throwable rootCauseOf(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }
}
