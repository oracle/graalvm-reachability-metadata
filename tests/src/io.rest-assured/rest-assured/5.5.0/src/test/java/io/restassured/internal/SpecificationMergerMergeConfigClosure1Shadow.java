/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io.restassured.internal;

import java.util.Map;

import groovy.lang.Closure;
import groovy.lang.Reference;
import io.restassured.config.Config;
import io.restassured.config.ConnectionConfig;
import io.restassured.config.CsrfConfig;
import io.restassured.config.DecoderConfig;
import io.restassured.config.EncoderConfig;
import io.restassured.config.FailureConfig;
import io.restassured.config.HeaderConfig;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.JsonConfig;
import io.restassured.config.LogConfig;
import io.restassured.config.MatcherConfig;
import io.restassured.config.MultiPartConfig;
import io.restassured.config.OAuthConfig;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.ParamConfig;
import io.restassured.config.RedirectConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.config.SessionConfig;
import io.restassured.config.XmlConfig;

// CheckStyle: start generated
class SpecificationMerger$_mergeConfig_closure1 extends Closure<Object> {
    private final Reference<Object> otherConfig;
    private final Reference<Map<Class<? extends Config>, Config>> configsToUse;

    SpecificationMerger$_mergeConfig_closure1(
            Object owner,
            Object thisObject,
            Reference<Map<Class<? extends Config>, Config>> configsToUse,
            Reference<Object> otherConfig) {
        super(owner, thisObject);
        this.otherConfig = otherConfig;
        this.configsToUse = configsToUse;
    }

    public Object doCall(Object configType, Object thisTempConfig) {
        RestAssuredConfig config = RestAssuredConfig.class.cast(otherConfig.get());
        Class<? extends Config> type = configClass(configType);
        Config otherTempConfig = configFor(config, type);
        if (otherTempConfig.isUserConfigured()) {
            configsToUse.get().put(type, otherTempConfig);
        } else {
            configsToUse.get().put(type, Config.class.cast(thisTempConfig));
        }
        return null;
    }

    static Class<?> class$(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new NoClassDefFoundError(exception.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Config> configClass(Object configType) {
        Class<?> restAssuredConfigClass = class$("io.restassured.config.RestAssuredConfig");
        if (!RestAssuredConfig.class.equals(restAssuredConfigClass)) {
            throw new IllegalStateException(restAssuredConfigClass.getName());
        }
        return (Class<? extends Config>) configType;
    }

    private static Config configFor(RestAssuredConfig config, Class<? extends Config> type) {
        if (type.equals(HttpClientConfig.class)) {
            return config.getHttpClientConfig();
        }
        if (type.equals(RedirectConfig.class)) {
            return config.getRedirectConfig();
        }
        if (type.equals(LogConfig.class)) {
            return config.getLogConfig();
        }
        if (type.equals(EncoderConfig.class)) {
            return config.getEncoderConfig();
        }
        if (type.equals(DecoderConfig.class)) {
            return config.getDecoderConfig();
        }
        if (type.equals(SessionConfig.class)) {
            return config.getSessionConfig();
        }
        if (type.equals(ObjectMapperConfig.class)) {
            return config.getObjectMapperConfig();
        }
        if (type.equals(ConnectionConfig.class)) {
            return config.getConnectionConfig();
        }
        if (type.equals(JsonConfig.class)) {
            return config.getJsonConfig();
        }
        if (type.equals(XmlConfig.class)) {
            return config.getXmlConfig();
        }
        if (type.equals(SSLConfig.class)) {
            return config.getSSLConfig();
        }
        if (type.equals(MatcherConfig.class)) {
            return config.getMatcherConfig();
        }
        if (type.equals(HeaderConfig.class)) {
            return config.getHeaderConfig();
        }
        if (type.equals(MultiPartConfig.class)) {
            return config.getMultiPartConfig();
        }
        if (type.equals(ParamConfig.class)) {
            return config.getParamConfig();
        }
        if (type.equals(OAuthConfig.class)) {
            return config.getOAuthConfig();
        }
        if (type.equals(FailureConfig.class)) {
            return config.getFailureConfig();
        }
        if (type.equals(CsrfConfig.class)) {
            return config.getCsrfConfig();
        }
        throw new IllegalArgumentException(type.getName());
    }
}
// CheckStyle: stop generated
