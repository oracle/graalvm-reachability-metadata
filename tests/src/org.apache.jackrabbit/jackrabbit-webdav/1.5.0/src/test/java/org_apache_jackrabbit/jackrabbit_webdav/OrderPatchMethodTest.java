/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.OrderPatchMethod;
import org.apache.jackrabbit.webdav.ordering.OrderPatch;
import org.apache.jackrabbit.webdav.ordering.OrderingConstants;
import org.apache.jackrabbit.webdav.ordering.Position;
import org.junit.jupiter.api.Test;

public class OrderPatchMethodTest {
    @Test
    public void constructorCreatesOrderPatchRequestWithOrderPatchBody() throws Exception {
        OrderPatch.Member member = new OrderPatch.Member(
                "chapter-two",
                new Position(OrderingConstants.XML_BEFORE, "chapter-one"));
        OrderPatch orderPatch = new OrderPatch("/repository/orderings/manual", member);

        ExposedOrderPatchMethod method = new ExposedOrderPatchMethod(
                "http://localhost:8080/repository/book",
                orderPatch);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_ORDERPATCH);
        assertThat(method.getPath()).isEqualTo("/repository/book");
        assertThat(method.isSuccessfulStatus(200)).isTrue();
        assertThat(method.isSuccessfulStatus(201)).isFalse();
        assertThat(method.isSuccessfulStatus(207)).isFalse();

        String xml = requestBody(method);
        assertThat(xml).contains("orderpatch");
        assertThat(xml).contains("ordering-type");
        assertThat(xml).contains("/repository/orderings/manual");
        assertThat(xml).contains("order-member");
        assertThat(xml).contains("chapter-two");
        assertThat(xml).contains("before");
        assertThat(xml).contains("chapter-one");
    }

    @Test
    public void firstAndLastConstructorsCreateOrderPatchBodies() throws Exception {
        OrderPatchMethod firstMethod = new OrderPatchMethod(
                "http://localhost:8080/repository/book",
                "/repository/orderings/manual",
                "preface",
                true);
        OrderPatchMethod lastMethod = new OrderPatchMethod(
                "http://localhost:8080/repository/book",
                "/repository/orderings/manual",
                "appendix",
                false);

        assertThat(firstMethod.getName()).isEqualTo(DavMethods.METHOD_ORDERPATCH);
        assertThat(lastMethod.getName()).isEqualTo(DavMethods.METHOD_ORDERPATCH);
        assertThat(requestBody(firstMethod)).contains("preface").contains("first");
        assertThat(requestBody(lastMethod)).contains("appendix").contains("last");
    }

    @Test
    public void beforeAndAfterConstructorsCreateOrderPatchBodies() throws Exception {
        OrderPatchMethod beforeMethod = new OrderPatchMethod(
                "http://localhost:8080/repository/book",
                "/repository/orderings/manual",
                "chapter-two",
                "chapter-one",
                true);
        OrderPatchMethod afterMethod = new OrderPatchMethod(
                "http://localhost:8080/repository/book",
                "/repository/orderings/manual",
                "chapter-one",
                "preface",
                false);

        assertThat(beforeMethod.getName()).isEqualTo(DavMethods.METHOD_ORDERPATCH);
        assertThat(afterMethod.getName()).isEqualTo(DavMethods.METHOD_ORDERPATCH);
        assertThat(requestBody(beforeMethod))
                .contains("chapter-two")
                .contains("before")
                .contains("chapter-one");
        assertThat(requestBody(afterMethod))
                .contains("chapter-one")
                .contains("after")
                .contains("preface");
    }

    private static String requestBody(OrderPatchMethod method) throws IOException {
        RequestEntity requestEntity = method.getRequestEntity();
        assertThat(requestEntity).isNotNull();
        assertThat(requestEntity.isRepeatable()).isTrue();
        assertThat(requestEntity.getContentType()).startsWith("text/xml; charset=");

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        requestEntity.writeRequest(body);
        return body.toString(StandardCharsets.UTF_8.name());
    }

    private static final class ExposedOrderPatchMethod extends OrderPatchMethod {
        private ExposedOrderPatchMethod(String uri, OrderPatch orderPatch) throws IOException {
            super(uri, orderPatch);
        }

        private boolean isSuccessfulStatus(int statusCode) {
            return isSuccess(statusCode);
        }
    }
}
