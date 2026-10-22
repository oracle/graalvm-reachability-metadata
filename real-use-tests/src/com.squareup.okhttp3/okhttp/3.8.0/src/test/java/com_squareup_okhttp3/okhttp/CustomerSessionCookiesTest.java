/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Cookie sessions retain and rotate a customer's login while respecting cookie path scope.
 * A customer portal client signs in, fetches the account and orders, and visits a public
 * page without disclosing its account-only session cookie.
 */
public class CustomerSessionCookiesTest {

    @Test
    @Timeout(60)
    public void retainsRotatedSessionOnlyForAccountRequests() throws Exception {
        try (MockWebServer portal = new MockWebServer()) {
            portal.enqueue(new MockResponse()
                    .addHeader("Set-Cookie", "session=login-token; Path=/account; HttpOnly")
                    .setBody("Signed in"));
            portal.enqueue(new MockResponse()
                    .addHeader("Set-Cookie", "session=rotated-token; Path=/account; HttpOnly")
                    .setBody("Customer: Ada"));
            portal.enqueue(new MockResponse().setBody("Order ORD-42: shipped"));
            portal.enqueue(new MockResponse().setBody("Welcome to the customer portal"));
            portal.start();

            OkHttpClient client = new OkHttpClient.Builder()
                    .cookieJar(new SessionCookies())
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
            try {
                Request login = new Request.Builder()
                        .url(portal.url("/login"))
                        .post(new FormBody.Builder()
                                .add("username", "ada")
                                .add("password", "customer-secret")
                                .build())
                        .build();
                try (Response response = client.newCall(login).execute()) {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.body().string()).isEqualTo("Signed in");
                }

                Request account = new Request.Builder().url(portal.url("/account/profile")).build();
                try (Response response = client.newCall(account).execute()) {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.body().string()).isEqualTo("Customer: Ada");
                }

                Request orders = new Request.Builder().url(portal.url("/account/orders")).build();
                try (Response response = client.newCall(orders).execute()) {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.body().string()).isEqualTo("Order ORD-42: shipped");
                }

                Request publicPage = new Request.Builder().url(portal.url("/accounting-help")).build();
                try (Response response = client.newCall(publicPage).execute()) {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.body().string()).isEqualTo("Welcome to the customer portal");
                }

                RecordedRequest signIn = portal.takeRequest(10, TimeUnit.SECONDS);
                assertThat(signIn).isNotNull();
                assertThat(signIn.getMethod()).isEqualTo("POST");
                assertThat(signIn.getPath()).isEqualTo("/login");
                assertThat(signIn.getHeader("Cookie")).isNull();
                assertThat(signIn.getBody().readUtf8()).isEqualTo("username=ada&password=customer-secret");

                RecordedRequest profile = portal.takeRequest(10, TimeUnit.SECONDS);
                assertThat(profile).isNotNull();
                assertThat(profile.getPath()).isEqualTo("/account/profile");
                assertThat(profile.getHeader("Cookie")).isEqualTo("session=login-token");

                RecordedRequest history = portal.takeRequest(10, TimeUnit.SECONDS);
                assertThat(history).isNotNull();
                assertThat(history.getPath()).isEqualTo("/account/orders");
                assertThat(history.getHeader("Cookie")).isEqualTo("session=rotated-token");

                RecordedRequest publicVisit = portal.takeRequest(10, TimeUnit.SECONDS);
                assertThat(publicVisit).isNotNull();
                assertThat(publicVisit.getPath()).isEqualTo("/accounting-help");
                assertThat(publicVisit.getHeader("Cookie")).isNull();
                assertThat(portal.getRequestCount()).isEqualTo(4);
            } finally {
                client.connectionPool().evictAll();
                client.dispatcher().executorService().shutdownNow();
            }
        }
    }

    private static final class SessionCookies implements CookieJar {
        private final List<Cookie> stored = new ArrayList<>();

        @Override
        public synchronized void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            for (Cookie cookie : cookies) {
                stored.removeIf(previous -> previous.name().equals(cookie.name())
                        && previous.domain().equals(cookie.domain())
                        && previous.path().equals(cookie.path()));
                stored.add(cookie);
            }
        }

        @Override
        public synchronized List<Cookie> loadForRequest(HttpUrl url) {
            long now = System.currentTimeMillis();
            stored.removeIf(cookie -> cookie.expiresAt() <= now);
            List<Cookie> matching = new ArrayList<>();
            for (Cookie cookie : stored) {
                if (cookie.matches(url)) {
                    matching.add(cookie);
                }
            }
            return matching;
        }
    }
}
