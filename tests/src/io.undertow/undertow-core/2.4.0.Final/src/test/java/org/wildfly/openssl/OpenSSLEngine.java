/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.wildfly.openssl;

import java.nio.ByteBuffer;
import java.util.Arrays;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

/**
 * Minimal test implementation loaded by Undertow's OpenSSL ALPN provider.
 */
public class OpenSSLEngine extends SSLEngine {

    private String[] applicationProtocols = new String[0];
    private boolean useClientMode;
    private boolean needClientAuth;
    private boolean wantClientAuth;
    private boolean enableSessionCreation = true;
    private boolean inboundDone;
    private boolean outboundDone;

    public void setApplicationProtocols(String[] protocols) {
        this.applicationProtocols = Arrays.copyOf(protocols, protocols.length);
    }

    public String getSelectedApplicationProtocol() {
        if (applicationProtocols.length == 0) {
            return null;
        }
        return applicationProtocols[0];
    }

    public String[] applicationProtocols() {
        return Arrays.copyOf(applicationProtocols, applicationProtocols.length);
    }

    @Override
    public void beginHandshake() {
    }

    @Override
    public void closeInbound() throws SSLException {
        inboundDone = true;
    }

    @Override
    public void closeOutbound() {
        outboundDone = true;
    }

    @Override
    public Runnable getDelegatedTask() {
        return null;
    }

    @Override
    public String[] getEnabledCipherSuites() {
        return new String[0];
    }

    @Override
    public String[] getEnabledProtocols() {
        return new String[0];
    }

    @Override
    public boolean getEnableSessionCreation() {
        return enableSessionCreation;
    }

    @Override
    public SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        return SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
    }

    @Override
    public boolean getNeedClientAuth() {
        return needClientAuth;
    }

    @Override
    public SSLSession getSession() {
        return null;
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return new String[0];
    }

    @Override
    public String[] getSupportedProtocols() {
        return new String[0];
    }

    @Override
    public boolean getUseClientMode() {
        return useClientMode;
    }

    @Override
    public boolean getWantClientAuth() {
        return wantClientAuth;
    }

    @Override
    public boolean isInboundDone() {
        return inboundDone;
    }

    @Override
    public boolean isOutboundDone() {
        return outboundDone;
    }

    @Override
    public void setEnabledCipherSuites(String[] suites) {
    }

    @Override
    public void setEnabledProtocols(String[] protocols) {
    }

    @Override
    public void setEnableSessionCreation(boolean flag) {
        enableSessionCreation = flag;
    }

    @Override
    public void setNeedClientAuth(boolean need) {
        needClientAuth = need;
    }

    @Override
    public void setUseClientMode(boolean mode) {
        useClientMode = mode;
    }

    @Override
    public void setWantClientAuth(boolean want) {
        wantClientAuth = want;
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts, int offset, int length) throws SSLException {
        return new SSLEngineResult(SSLEngineResult.Status.OK, getHandshakeStatus(), 0, 0);
    }

    @Override
    public SSLEngineResult wrap(ByteBuffer[] srcs, int offset, int length, ByteBuffer dst) throws SSLException {
        return new SSLEngineResult(SSLEngineResult.Status.OK, getHandshakeStatus(), 0, 0);
    }
}
