/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.AclMethod;
import org.apache.jackrabbit.webdav.security.AclProperty;
import org.apache.jackrabbit.webdav.security.Principal;
import org.apache.jackrabbit.webdav.security.Privilege;
import org.apache.jackrabbit.webdav.security.SecurityConstants;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class AclMethodTest {
    @Test
    void createsAclRequestWithSerializedAccessControlList() throws Exception {
        AclProperty.Ace ace = AclProperty.createGrantAce(
                Principal.getHrefPrincipal("/principals/users/alice"),
                new Privilege[] {Privilege.PRIVILEGE_READ},
                false,
                false,
                null);
        AclProperty aclProperty = new AclProperty(new AclProperty.Ace[] {ace});

        AclMethod method = new AclMethod("/repository/document.txt", aclProperty);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_ACL);

        Document requestDocument = parseRequestEntity(method.getRequestEntity());
        Element aclElement = requestDocument.getDocumentElement();
        assertThat(DomUtil.matches(aclElement, SecurityConstants.ACL.getName(), SecurityConstants.NAMESPACE)).isTrue();

        Element aceElement = DomUtil.getChildElement(aclElement, "ace", SecurityConstants.NAMESPACE);
        assertThat(aceElement).isNotNull();
        Element principalElement = DomUtil.getChildElement(
                aceElement,
                Principal.XML_PRINCIPAL,
                SecurityConstants.NAMESPACE);
        assertThat(DomUtil.getChildText(principalElement, DavConstants.XML_HREF, DavConstants.NAMESPACE))
                .isEqualTo("/principals/users/alice");

        Element grantElement = DomUtil.getChildElement(aceElement, "grant", SecurityConstants.NAMESPACE);
        Element privilegeElement = DomUtil.getChildElement(
                grantElement,
                Privilege.XML_PRIVILEGE,
                SecurityConstants.NAMESPACE);
        assertThat(DomUtil.hasChildElement(
                privilegeElement,
                Privilege.PRIVILEGE_READ.getName(),
                SecurityConstants.NAMESPACE)).isTrue();
    }

    private static Document parseRequestEntity(RequestEntity requestEntity) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        requestEntity.writeRequest(output);
        DocumentBuilder builder = newDocumentBuilder();
        return builder.parse(new InputSource(new ByteArrayInputStream(output.toByteArray())));
    }

    private static DocumentBuilder newDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder();
    }
}
