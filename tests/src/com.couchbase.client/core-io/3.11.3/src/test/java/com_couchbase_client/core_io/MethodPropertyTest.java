/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class MethodPropertyTest {
    @Test
    void mapsApplicationData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Account value = mapper.readValue("""
                {"balance":42}
                """, Account.class);
        assertEquals(42, value.getBalance());
        mapper.readerForUpdating(value).readValue("""
                {"balance":81}
                """);
        assertEquals(81, value.getBalance());
    }

    @Test
    void assignsIdentityThroughFluentSetterAndResolvesReference() throws Exception {
        IdentifiedAccount account = new ObjectMapper().readValue("""
                {"id":7,"balance":25,"linked":7}
                """, IdentifiedAccount.class);
        assertEquals(7, account.getId());
        assertEquals(25, account.balance);
        assertSame(account, account.linked);
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    public static class IdentifiedAccount {
        private int id;
        public int balance;
        public IdentifiedAccount linked;

        public int getId() {
            return id;
        }

        public IdentifiedAccount setId(int id) {
            this.id = id;
            return this;
        }
    }

    public static class Account {
        private int balance;

        public int getBalance() {
            return balance;
        }

        public void setBalance(int balance) {
            this.balance = balance;
        }
    }
}
