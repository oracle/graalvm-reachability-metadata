/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.thymeleaf.extras;

import java.security.Principal;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.extras.springsecurity6.util.SpringVersionSpecificUtils;
import org.thymeleaf.extras.springsecurity6.util.SpringVersionUtils;
import org.thymeleaf.spring6.web.webflux.ISpringWebFluxWebApplication;
import org.thymeleaf.spring6.web.webflux.ISpringWebFluxWebExchange;
import org.thymeleaf.spring6.web.webflux.ISpringWebFluxWebRequest;
import org.thymeleaf.spring6.web.webflux.ISpringWebFluxWebSession;
import org.thymeleaf.web.servlet.IServletWebApplication;
import org.thymeleaf.web.servlet.IServletWebExchange;
import org.thymeleaf.web.servlet.IServletWebRequest;
import org.thymeleaf.web.servlet.IServletWebSession;

import org.springframework.core.SpringVersion;

public class ThymeleafSpringSecurityTest {

    @Test
    void springVersion() {
        Assertions.assertThat(SpringVersion.getVersion()).isNotNull();
        Assertions.assertThat(SpringVersionUtils.isSpring60AtLeast()).isTrue();
    }

    @Test
    void springVersionSpecificUtilsWebMvc() {
        WebContext webContext = new WebContext(new IServletWebExchange() {

            @Override
            public IServletWebRequest getRequest() {
                return null;
            }

            @Override
            public IServletWebSession getSession() {
                return null;
            }

            @Override
            public IServletWebApplication getApplication() {
                return null;
            }

            @Override
            public Principal getPrincipal() {
                return null;
            }

            @Override
            public Locale getLocale() {
                return null;
            }

            @Override
            public String getContentType() {
                return null;
            }

            @Override
            public String getCharacterEncoding() {
                return null;
            }

            @Override
            public Object getAttributeValue(String name) {
                return null;
            }

            @Override
            public void setAttributeValue(String name, Object value) {

            }

            @Override
            public String transformURL(String url) {
                return null;
            }

            @Override
            public Enumeration<String> getAttributeNames() {
                return null;
            }

            @Override
            public Object getNativeRequestObject() {
                return null;
            }

            @Override
            public Object getNativeResponseObject() {
                return null;
            }
        });
        Assertions.assertThat(SpringVersionSpecificUtils.isWebMvcContext(webContext)).isTrue();
        Assertions.assertThat(SpringVersionSpecificUtils.isWebContext(webContext)).isTrue();
    }

    @Test
    void springVersionSpecificUtilsWebFlux() {
        WebContext webContext = new WebContext(new ISpringWebFluxWebExchange() {
            @Override
            public ISpringWebFluxWebRequest getRequest() {
                return null;
            }

            @Override
            public ISpringWebFluxWebSession getSession() {
                return null;
            }

            @Override
            public ISpringWebFluxWebApplication getApplication() {
                return null;
            }

            @Override
            public Map<String, Object> getAttributes() {
                return null;
            }

            @Override
            public Object getNativeExchangeObject() {
                return null;
            }

            @Override
            public Principal getPrincipal() {
                return null;
            }

            @Override
            public Locale getLocale() {
                return null;
            }

            @Override
            public String getContentType() {
                return null;
            }

            @Override
            public String getCharacterEncoding() {
                return null;
            }

            @Override
            public void setAttributeValue(String name, Object value) {

            }

            @Override
            public void removeAttribute(String name) {

            }

            @Override
            public String transformURL(String url) {
                return null;
            }
        });
        Assertions.assertThat(SpringVersionSpecificUtils.isWebFluxContext(webContext)).isTrue();
        Assertions.assertThat(SpringVersionSpecificUtils.isWebContext(webContext)).isTrue();
    }
}
