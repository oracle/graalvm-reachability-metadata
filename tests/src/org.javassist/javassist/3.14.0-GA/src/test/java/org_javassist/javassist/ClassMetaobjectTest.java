/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import javassist.tools.reflect.ClassMetaobject;
import org.junit.jupiter.api.Test;
import org_javassist.javassist.TestSupport.Account;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassMetaobjectTest {
    @Test
    void constructsInstancesAndDispatchesStaticMembers() throws Throwable {
        ClassMetaobject type = new ClassMetaobject(new String[]{Account.class.getName()});
        Account account = (Account) type.newInstance(new Object[]{25});
        assertEquals(25, account.balance);
        assertTrue(type.isInstance(account));
        assertEquals(Account.class, type.getJavaClass());
        String previous = Account.currency;
        try {
            type.trapFieldWrite("currency", "EUR");
            assertEquals("EUR", type.trapFieldRead("currency"));
            assertEquals("EUR", Account.currency);
            int quote = type.getMethodIndex("quote", new Class<?>[]{String.class});
            assertEquals("EUR 12", type.trapMethodcall(quote, new Object[]{"12"}));
            assertArrayEquals(new Class<?>[]{String.class}, type.getParameterTypes(quote));
            assertEquals(String.class, type.getReturnType(quote));
            int deposit = type.getMethodIndex("deposit", new Class<?>[]{int.class});
            assertEquals(30, ClassMetaobject.invoke(account, deposit, new Object[]{5}));
            assertEquals(30, account.balance);
        } finally {
            Account.currency = previous;
        }
    }

    @Test
    void restoresConstructorInformationAfterSerialization() throws Exception {
        ClassMetaobject original = new ClassMetaobject(new String[]{Account.class.getName()});
        ClassMetaobject restored = (ClassMetaobject) TestSupport.roundTrip(original);
        Account account = (Account) restored.newInstance(new Object[]{18});
        assertEquals(18, account.balance);
        assertEquals(Account.class.getName(), restored.getName());
    }

    @Test
    void resolvesExistingTypesWithTheContextClassLoader() throws Exception {
        boolean previous = ClassMetaobject.useContextClassLoader;
        try {
            ClassMetaobject.useContextClassLoader = true;
            ClassMetaobject type = new ClassMetaobject(new String[]{Account.class.getName()});
            assertEquals(7, ((Account) type.newInstance(new Object[]{7})).balance);
        } finally {
            ClassMetaobject.useContextClassLoader = previous;
        }
    }
}
