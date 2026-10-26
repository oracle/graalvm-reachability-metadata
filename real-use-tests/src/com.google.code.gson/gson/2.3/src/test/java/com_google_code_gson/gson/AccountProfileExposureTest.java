/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.Expose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Exercises Gson's field-exclusion subsystem to import an account signup and export a public profile.
public class AccountProfileExposureTest {

    @Test
    @Timeout(60)
    public void acceptsSignupFieldsWithoutAcceptingPrivilegesOrPublishingCredentials() {
        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
        Account account = gson.fromJson("""
                {
                  "displayName": "River Books",
                  "email": "river@example.invalid",
                  "password": "signup-only-secret",
                  "role": "administrator",
                  "accountId": "client-chosen-id",
                  "moderationNotes": "client-chosen-notes"
                }
                """, Account.class);

        assertEquals("River Books", account.displayName);
        assertEquals("river@example.invalid", account.email);
        assertEquals("signup-only-secret", account.password);
        assertEquals("member", account.role);
        assertEquals("pending", account.accountId);
        assertEquals("awaiting-review", account.moderationNotes);

        account.accountId = "account-104";
        account.moderationNotes = "verified-by-support";
        String response = gson.toJson(account);
        JsonObject profile = new JsonParser().parse(response).getAsJsonObject();

        assertEquals(new JsonParser().parse("""
                {
                  "displayName": "River Books",
                  "email": "river@example.invalid",
                  "role": "member",
                  "accountId": "account-104"
                }
                """), profile);
        assertFalse(profile.has("password"));
        assertFalse(profile.has("moderationNotes"));
        assertFalse(response.contains("signup-only-secret"));
        assertFalse(response.contains("verified-by-support"));
    }

    public static class Account {
        @Expose
        private String displayName;

        @Expose
        private String email;

        @Expose(serialize = false)
        private String password;

        @Expose(deserialize = false)
        private String role = "member";

        @Expose(deserialize = false)
        private String accountId = "pending";

        private String moderationNotes = "awaiting-review";
    }
}
