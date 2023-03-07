/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

class AdditionalMetadataTest {

    @Test
    public void testLoggers() throws Exception {
        Class c = Class.forName("org.hibernate.internal.log.UrlMessageBundle_$logger");
        Constructor constructor = c.getConstructor(Logger.class);
        assertThat(constructor).isNotNull();
    }

    @Test
    public void testQueryParsingSupport() throws Exception {
        for (Class c : typesNeedingDefaultConstructorAccessible()) {
            Constructor constructor = c.getConstructor();
            Object instance = constructor.newInstance();
            assertThat(instance).isNotNull();
        }
    }

    /* Method typesNeedingDefaultConstructorAccessible has been copied from
       org.hibernate.graalvm.internal.QueryParsingSupport class
       https://github.com/hibernate/hibernate-orm/tree/5.6.14/hibernate-graalvm */

    public static Class[] typesNeedingDefaultConstructorAccessible() {
        return new Class[] {
                //Support for @OrderBy
                org.hibernate.sql.ordering.antlr.NodeSupport.class,
                org.hibernate.sql.ordering.antlr.OrderByFragment.class,
                org.hibernate.sql.ordering.antlr.SortSpecification.class,
                org.hibernate.sql.ordering.antlr.OrderingSpecification.class,
                org.hibernate.sql.ordering.antlr.CollationSpecification.class,
                org.hibernate.sql.ordering.antlr.SortKey.class,

                //ANTLR tokens:
                antlr.CommonToken.class,
                org.hibernate.hql.internal.ast.tree.SelectClause.class,
                org.hibernate.hql.internal.ast.tree.HqlSqlWalkerNode.class,
                org.hibernate.hql.internal.ast.tree.MethodNode.class,
                org.hibernate.hql.internal.ast.tree.UnaryLogicOperatorNode.class,
                org.hibernate.hql.internal.ast.tree.NullNode.class,
                org.hibernate.hql.internal.ast.tree.IntoClause.class,
                org.hibernate.hql.internal.ast.tree.UpdateStatement.class,
                org.hibernate.hql.internal.ast.tree.SelectExpressionImpl.class,
                org.hibernate.hql.internal.ast.tree.CastFunctionNode.class,
                org.hibernate.hql.internal.ast.tree.DeleteStatement.class,
                org.hibernate.hql.internal.ast.tree.SqlNode.class,
                org.hibernate.hql.internal.ast.tree.SearchedCaseNode.class,
                org.hibernate.hql.internal.ast.tree.FromElement.class,
                org.hibernate.hql.internal.ast.tree.JavaConstantNode.class,
                org.hibernate.hql.internal.ast.tree.SqlFragment.class,
                org.hibernate.hql.internal.ast.tree.MapKeyNode.class,
                org.hibernate.hql.internal.ast.tree.ImpliedFromElement.class,
                org.hibernate.hql.internal.ast.tree.IsNotNullLogicOperatorNode.class,
                org.hibernate.hql.internal.ast.tree.InsertStatement.class,
                org.hibernate.hql.internal.ast.tree.UnaryArithmeticNode.class,
                org.hibernate.hql.internal.ast.tree.CollectionFunction.class,
                org.hibernate.hql.internal.ast.tree.BinaryLogicOperatorNode.class,
                org.hibernate.hql.internal.ast.tree.CountNode.class,
                org.hibernate.hql.internal.ast.tree.IsNullLogicOperatorNode.class,
                org.hibernate.hql.internal.ast.tree.IdentNode.class,
                org.hibernate.hql.internal.ast.tree.ParameterNode.class,
                org.hibernate.hql.internal.ast.tree.MapEntryNode.class,
                org.hibernate.hql.internal.ast.tree.MapValueNode.class,
                org.hibernate.hql.internal.ast.tree.InLogicOperatorNode.class,
                org.hibernate.hql.internal.ast.tree.IndexNode.class,
                org.hibernate.hql.internal.ast.tree.DotNode.class,
                org.hibernate.hql.internal.ast.tree.ResultVariableRefNode.class,
                org.hibernate.hql.internal.ast.tree.BetweenOperatorNode.class,
                org.hibernate.hql.internal.ast.tree.AggregateNode.class,
                org.hibernate.hql.internal.ast.tree.QueryNode.class,
                org.hibernate.hql.internal.ast.tree.BooleanLiteralNode.class,
                org.hibernate.hql.internal.ast.tree.SimpleCaseNode.class,
                org.hibernate.hql.internal.ast.tree.OrderByClause.class,
                org.hibernate.hql.internal.ast.tree.FromClause.class,
                org.hibernate.hql.internal.ast.tree.ConstructorNode.class,
                org.hibernate.hql.internal.ast.tree.LiteralNode.class,
                org.hibernate.hql.internal.ast.tree.BinaryArithmeticOperatorNode.class,

                //Special tokens:
                org.hibernate.hql.internal.ast.HqlToken.class,
                org.hibernate.hql.internal.ast.tree.Node.class,
        };
    }
}
