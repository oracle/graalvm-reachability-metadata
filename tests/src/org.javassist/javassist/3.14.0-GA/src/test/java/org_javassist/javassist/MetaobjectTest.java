/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import javassist.tools.reflect.Metaobject;
import org.junit.jupiter.api.Test;
import org_javassist.javassist.TestSupport.Account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class MetaobjectTest {
    @Test
    void readsWritesAndInvokesTheAssociatedObject() throws Throwable {
        Account account = new Account(10);
        Metaobject metaobject = new Metaobject(account, new Object[]{10});
        metaobject.trapFieldWrite("balance", 40);
        assertEquals(40, metaobject.trapFieldRead("balance"));
        assertEquals(40, account.balance);
        int deposit = metaobject.getClassMetaobject().getMethodIndex("deposit", new Class<?>[]{int.class});
        assertEquals(45, metaobject.trapMethodcall(deposit, new Object[]{5}));
        assertEquals(45, account.balance);
        assertEquals("deposit", metaobject.getMethodName(deposit));
        Account replacement = new Account(90);
        metaobject.setObject(replacement);
        assertSame(metaobject, replacement._getMetaobject());
        assertEquals(92, metaobject.trapMethodcall(deposit, new Object[]{2}));
        assertEquals(45, account.balance);
    }

    @Test
    void serializesTheBaseObjectAndRestoresDispatch() throws Throwable {
        Account account = new Account(14);
        Metaobject original = new Metaobject(account, new Object[]{14});
        Metaobject restored = (Metaobject) TestSupport.roundTrip(original);
        assertEquals(14, restored.trapFieldRead("balance"));
        int deposit = restored.getClassMetaobject().getMethodIndex("deposit", new Class<?>[]{int.class});
        assertEquals(20, restored.trapMethodcall(deposit, new Object[]{6}));
        assertEquals(14, account.balance);
    }
}
