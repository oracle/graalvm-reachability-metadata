/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.Since;
import com.google.gson.annotations.Until;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Field exclusion and API versioning with `@Expose`, `@Since`/`@Until`, modifier-based exclusion,
 * and custom `ExclusionStrategy` implementations.
 *
 * An account service publishes the same `Account` model to clients of two API versions. Only
 * fields marked `@Expose` leave the service, versioned fields appear or disappear with the
 * negotiated API version, the password is accepted on sign-up but never echoed back, fields
 * tagged with an application `@Confidential` annotation are dropped from outgoing payloads, and
 * the attached audit trail type is never serialized at all. A separate cache exporter keeps
 * `transient` session counters that the public API hides.
 */
public class AccountApiFieldExclusionTest {

    @Test
    void versionOneClientsSeeLegacyFieldsOnly() {
        Gson api = AccountApi.forVersion(1.0);

        JsonObject json = JsonParser.parseString(api.toJson(Account.sample())).getAsJsonObject();

        assertEquals(Set.of("id", "username", "nickname"), keys(json));
        assertEquals("ada", json.get("username").getAsString());
        assertEquals("Countess", json.get("nickname").getAsString());
    }

    @Test
    void versionTwoClientsSeeDisplayNameInsteadOfNickname() {
        Gson api = AccountApi.forVersion(2.0);

        JsonObject json = JsonParser.parseString(api.toJson(Account.sample())).getAsJsonObject();

        assertEquals(Set.of("id", "username", "displayName"), keys(json));
        assertEquals(42L, json.get("id").getAsLong());
        assertEquals("Ada Lovelace", json.get("displayName").getAsString());
        assertFalse(json.has("password"), "password must never be echoed back");
        assertFalse(json.has("internalNote"), "fields without @Expose stay internal");
    }

    @Test
    void signUpRequestAcceptsPasswordButIgnoresUnexposedAndFutureFields() {
        Gson api = AccountApi.forVersion(1.0);
        String request = """
                {
                  "id": 7,
                  "username": "grace",
                  "nickname": "Amazing Grace",
                  "displayName": "Grace Hopper",
                  "password": "c0b0l!",
                  "internalNote": "injected by client",
                  "sessionCount": 99
                }
                """;

        Account account = api.fromJson(request, Account.class);

        assertEquals(7L, account.id);
        assertEquals("grace", account.username);
        assertEquals("Amazing Grace", account.nickname);
        assertEquals("c0b0l!", account.password);
        assertNull(account.displayName, "displayName only exists from API version 2.0");
        assertNull(account.internalNote, "fields without @Expose are not bound");
        assertEquals(0, account.sessionCount, "transient fields are not bound");
    }

    @Test
    void confidentialFieldsAndAuditTrailAreStrippedOnlyFromOutgoingPayloads() {
        Gson partnerFeed = new GsonBuilder()
                .addSerializationExclusionStrategy(new ConfidentialDataStrategy())
                .create();
        Account account = Account.sample();

        JsonObject outgoing = JsonParser.parseString(partnerFeed.toJson(account)).getAsJsonObject();

        assertFalse(outgoing.has("email"), "@Confidential field leaked");
        assertFalse(outgoing.has("auditTrail"), "audit trail type leaked");
        assertFalse(outgoing.has("sessionCount"), "transient field leaked");
        assertEquals("ada", outgoing.get("username").getAsString());
        assertEquals("met Babbage", outgoing.get("internalNote").getAsString());

        Account incoming = partnerFeed.fromJson("""
                {"username": "linus", "email": "linus@example.org",
                 "auditTrail": {"lastLogin": "2024-01-01", "failedLogins": 3}}
                """, Account.class);

        assertEquals("linus", incoming.username);
        assertEquals("linus@example.org", incoming.email, "deserialization keeps @Confidential");
        assertEquals("2024-01-01", incoming.auditTrail.lastLogin);
        assertEquals(3, incoming.auditTrail.failedLogins);
    }

    @Test
    void cacheExporterKeepsTransientCountersButStillSkipsStatics() {
        Gson cache = new GsonBuilder().excludeFieldsWithModifiers(Modifier.STATIC).create();
        Account account = Account.sample();
        account.sessionCount = 12;

        JsonObject json = JsonParser.parseString(cache.toJson(account)).getAsJsonObject();

        assertEquals(12, json.get("sessionCount").getAsInt());
        assertFalse(json.has("SCHEMA"), "static fields must not be exported");

        Account restored = cache.fromJson(json, Account.class);
        assertEquals(12, restored.sessionCount);
        assertEquals("ada@example.org", restored.email);
        assertEquals("met Babbage", restored.internalNote);
        assertEquals(5, restored.auditTrail.failedLogins);
    }

    private static Set<String> keys(JsonObject json) {
        return new TreeSet<>(json.keySet());
    }

    /// Builds the `Gson` instance the HTTP layer uses for a negotiated API version.
    static final class AccountApi {

        private AccountApi() {
        }

        static Gson forVersion(double version) {
            return new GsonBuilder()
                    .excludeFieldsWithoutExposeAnnotation()
                    .setVersion(version)
                    .create();
        }
    }

    /// Drops fields tagged `@Confidential` and every field of type `AuditTrail`.
    static final class ConfidentialDataStrategy implements ExclusionStrategy {

        @Override
        public boolean shouldSkipField(FieldAttributes field) {
            // Checkstyle: allow direct annotation access
            FieldAttributes fieldAnnotationAccess = field;
            return fieldAnnotationAccess.getAnnotation(Confidential.class) != null;
            // Checkstyle: disallow direct annotation access
        }

        @Override
        public boolean shouldSkipClass(Class<?> type) {
            return type == AuditTrail.class;
        }
    }

    /// Marks personal data that must not leave the service in outgoing payloads.
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Confidential {
    }

    /// Security bookkeeping attached to an account.
    static final class AuditTrail {
        String lastLogin;
        int failedLogins;

        AuditTrail() {
        }

        AuditTrail(String lastLogin, int failedLogins) {
            this.lastLogin = lastLogin;
            this.failedLogins = failedLogins;
        }
    }

    /// The account model shared by every API version and the cache.
    static final class Account {
        static final String SCHEMA = "account-v2";

        @Expose
        long id;

        @Expose
        @Since(1.0)
        String username;

        @Expose
        @Until(2.0)
        String nickname;

        @Expose
        @Since(2.0)
        String displayName;

        @Expose(serialize = false)
        String password;

        @Confidential
        String email;

        String internalNote;

        AuditTrail auditTrail;

        transient int sessionCount;

        Account() {
        }

        static Account sample() {
            Account account = new Account();
            account.id = 42L;
            account.username = "ada";
            account.nickname = "Countess";
            account.displayName = "Ada Lovelace";
            account.password = "analytical-engine";
            account.email = "ada@example.org";
            account.internalNote = "met Babbage";
            account.auditTrail = new AuditTrail("1843-09-01", 5);
            return account;
        }
    }
}
