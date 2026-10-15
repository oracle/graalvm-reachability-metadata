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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.Since;
import com.google.gson.annotations.Until;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Field exclusion and API versioning with @Expose, @Since/@Until, exclusion strategies, and
 * modifier-based exclusion.
 *
 * An account service publishes versioned public views of its user accounts, accepts signup
 * requests without letting clients set server-owned fields, hides internal audit data, and
 * persists a session cache that keeps transient fields.
 */
public class AccountApiExclusionTest {

    @Test
    void publishesVersionedPublicViewsOfAnAccount() {
        Gson v1 = publicApi(1.0);
        Gson v2 = publicApi(2.0);
        UserAccount account = sampleAccount();

        JsonObject legacy = v1.toJsonTree(account).getAsJsonObject();
        assertEquals(Set.of("username", "accountId", "legacyNickname", "auditNote"), legacy.keySet());
        assertEquals("ada", legacy.get("username").getAsString());
        assertEquals("acc-42", legacy.get("accountId").getAsString());
        assertEquals("countess", legacy.get("legacyNickname").getAsString());

        JsonObject current = v2.toJsonTree(account).getAsJsonObject();
        assertEquals(Set.of("username", "accountId", "avatarUrl", "auditNote"), current.keySet());
        assertEquals("https://cdn.example.com/ada.png", current.get("avatarUrl").getAsString());

        String json = v2.toJson(account);
        assertFalse(json.contains("s3cret"));
        assertFalse(json.contains("bcrypt"));
    }

    @Test
    void acceptsSignupRequestsWithoutServerOwnedFields() {
        String request = """
                {
                  "username": "grace",
                  "password": "hunter2",
                  "accountId": "acc-forged",
                  "passwordHash": "forged-hash",
                  "avatarUrl": "https://cdn.example.com/grace.png",
                  "legacyNickname": "amazing"
                }
                """;

        UserAccount signup = publicApi(2.0).fromJson(request, UserAccount.class);

        assertEquals("grace", signup.username);
        assertEquals("hunter2", signup.password);
        assertNull(signup.accountId);
        assertNull(signup.passwordHash);
        assertEquals("https://cdn.example.com/grace.png", signup.avatarUrl);
        assertNull(signup.legacyNickname);
    }

    @Test
    void hidesInternalDataWithApplicationExclusionStrategies() {
        Gson partnerFeed = new GsonBuilder()
                .setExclusionStrategies(new InternalDataStrategy())
                .addSerializationExclusionStrategy(new DraftStrategy())
                .serializeNulls()
                .create();
        UserAccount lead = sampleAccount();
        lead.password = null;
        Team team = new Team("platform", new ArrayList<>(List.of(lead)));
        team.audit = new AuditTrail("created by admin");
        team.draftCharter = "work in progress";

        JsonObject document = partnerFeed.toJsonTree(team).getAsJsonObject();
        assertEquals(Set.of("name", "members", "draftCharterVersion"), document.keySet());
        JsonObject member = document.getAsJsonArray("members").get(0).getAsJsonObject();
        assertFalse(member.has("auditNote"));
        assertFalse(member.has("passwordHash"));
        assertTrue(member.has("legacyNickname"));
        assertTrue(member.get("password").isJsonNull());

        String incoming = """
                {"name": "infra", "draftCharter": "kept on read", "audit": {"note": "ignored"},
                 "members": [{"username": "linus", "auditNote": "ignored", "passwordHash": "h"}]}
                """;
        Team restored = partnerFeed.fromJson(incoming, Team.class);
        assertEquals("infra", restored.name);
        assertEquals("kept on read", restored.draftCharter);
        assertNull(restored.audit);
        assertEquals("linus", restored.members.get(0).username);
        assertNull(restored.members.get(0).auditNote);
        assertNull(restored.members.get(0).passwordHash);

        Set<String> skippedFields = new TreeSet<>();
        Gson recording = new GsonBuilder().setExclusionStrategies(new RecordingStrategy(skippedFields)).create();
        recording.toJson(team);
        assertEquals(Set.of("Team.audit:AuditTrail", "UserAccount.auditNote:String",
                "UserAccount.passwordHash:String"), skippedFields);
    }

    @Test
    void persistsTransientSessionStateWhenOnlyStaticFieldsAreExcluded() {
        Session session = new Session("ada", "token-abc");
        session.lastPage = "/settings";

        JsonObject wire = new Gson().toJsonTree(session).getAsJsonObject();
        assertEquals(Set.of("user", "lastPage"), wire.keySet());

        Gson cache = new GsonBuilder().excludeFieldsWithModifiers(Modifier.STATIC).create();
        String snapshot = cache.toJson(session);
        JsonObject stored = cache.fromJson(snapshot, JsonObject.class);
        assertEquals(Set.of("user", "lastPage", "token"), stored.keySet());
        assertFalse(stored.has("sessionsCreated"));

        Session resumed = cache.fromJson(snapshot, Session.class);
        assertEquals("ada", resumed.user);
        assertEquals("token-abc", resumed.token);
        assertEquals("/settings", resumed.lastPage);

        Session fromWire = new Gson().fromJson(snapshot, Session.class);
        assertNull(fromWire.token);
    }

    private static Gson publicApi(double version) {
        return new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .setVersion(version)
                .create();
    }

    private static UserAccount sampleAccount() {
        UserAccount account = new UserAccount();
        account.username = "ada";
        account.accountId = "acc-42";
        account.password = "s3cret";
        account.passwordHash = "bcrypt$2a$10$abc";
        account.avatarUrl = "https://cdn.example.com/ada.png";
        account.legacyNickname = "countess";
        account.auditNote = "flagged for review";
        return account;
    }

    /// Marks fields that never leave the service boundary.
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Internal {
    }

    static final class UserAccount {
        @Expose
        String username;

        @Expose(deserialize = false)
        String accountId;

        @Expose(serialize = false)
        String password;

        @Internal
        String passwordHash;

        @Expose
        @Since(2.0)
        String avatarUrl;

        @Expose
        @Until(2.0)
        String legacyNickname;

        @Expose
        @Internal
        String auditNote;
    }

    static final class AuditTrail {
        String note;

        AuditTrail(String note) {
            this.note = note;
        }
    }

    static final class Team {
        String name;
        List<UserAccount> members;
        AuditTrail audit;
        String draftCharter;
        @Since(1.0)
        int draftCharterVersion = 3;

        Team(String name, List<UserAccount> members) {
            this.name = name;
            this.members = members;
        }
    }

    static final class Session {
        static int sessionsCreated;

        String user;
        transient String token;
        String lastPage;

        Session(String user, String token) {
            this.user = user;
            this.token = token;
            sessionsCreated++;
        }
    }

    /// Skips fields annotated with [Internal] and every [AuditTrail] value in both directions.
    static final class InternalDataStrategy implements ExclusionStrategy {
        @Override
        public boolean shouldSkipField(FieldAttributes field) {
            FieldAttributes fieldAnnotationAccess = field;
            return fieldAnnotationAccess.getAnnotation(Internal.class) != null;
        }

        @Override
        public boolean shouldSkipClass(Class<?> type) {
            return type == AuditTrail.class;
        }
    }

    /// Keeps draft content out of outgoing documents while still accepting it on input.
    static final class DraftStrategy implements ExclusionStrategy {
        @Override
        public boolean shouldSkipField(FieldAttributes field) {
            return field.getName().equals("draftCharter") && field.getDeclaredClass() == String.class;
        }

        @Override
        public boolean shouldSkipClass(Class<?> type) {
            return false;
        }
    }

    /// Records which fields an exclusion decision applied to, keyed by declaring class and type.
    static final class RecordingStrategy implements ExclusionStrategy {
        private final InternalDataStrategy delegate = new InternalDataStrategy();
        private final Set<String> skipped;

        RecordingStrategy(Set<String> skipped) {
            this.skipped = skipped;
        }

        @Override
        public boolean shouldSkipField(FieldAttributes field) {
            boolean skip = delegate.shouldSkipField(field) || delegate.shouldSkipClass(field.getDeclaredClass());
            if (skip) {
                skipped.add(field.getDeclaringClass().getSimpleName() + "." + field.getName() + ":"
                        + field.getDeclaredClass().getSimpleName());
            }
            return skip;
        }

        @Override
        public boolean shouldSkipClass(Class<?> type) {
            return false;
        }
    }
}
