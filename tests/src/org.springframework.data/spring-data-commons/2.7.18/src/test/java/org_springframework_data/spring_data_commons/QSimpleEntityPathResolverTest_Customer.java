/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.core.types.dsl.StringPath;
import org_springframework_data.spring_data_commons.SimpleEntityPathResolverTest.Customer;

public class QSimpleEntityPathResolverTest_Customer extends EntityPathBase<Customer> {

    public static final QSimpleEntityPathResolverTest_Customer CUSTOMER =
            new QSimpleEntityPathResolverTest_Customer("customer");

    public final StringPath name = createString("name");

    public QSimpleEntityPathResolverTest_Customer(String variable) {
        super(Customer.class, variable);
    }
}
