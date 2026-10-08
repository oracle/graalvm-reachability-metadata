/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

public final class RecordingSslSocket extends SSLSocket {
    private boolean useSessionTickets;
    private String hostname;
    private byte[] alpnProtocols;
    private byte[] selectedAlpnProtocol = "h2".getBytes(StandardCharsets.UTF_8);

    public boolean useSessionTickets() {
        return useSessionTickets;
    }

    public String hostname() {
        return hostname;
    }

    public byte[] alpnProtocols() {
        return alpnProtocols;
    }

    public void setUseSessionTickets(boolean useSessionTickets) {
        this.useSessionTickets = useSessionTickets;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public void setAlpnProtocols(byte[] alpnProtocols) {
        this.alpnProtocols = alpnProtocols;
    }

    public byte[] getAlpnSelectedProtocol() {
        return selectedAlpnProtocol;
    }

    public void setSelectedAlpnProtocol(String selectedAlpnProtocol) {
        this.selectedAlpnProtocol = selectedAlpnProtocol.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return new String[0];
    }

    @Override
    public String[] getEnabledCipherSuites() {
        return new String[0];
    }

    @Override
    public void setEnabledCipherSuites(String[] suites) {
    }

    @Override
    public String[] getSupportedProtocols() {
        return new String[0];
    }

    @Override
    public String[] getEnabledProtocols() {
        return new String[0];
    }

    @Override
    public void setEnabledProtocols(String[] protocols) {
    }

    @Override
    public SSLSession getSession() {
        return null;
    }

    @Override
    public void addHandshakeCompletedListener(HandshakeCompletedListener listener) {
    }

    @Override
    public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) {
    }

    @Override
    public void startHandshake() throws IOException {
    }

    @Override
    public void setUseClientMode(boolean mode) {
    }

    @Override
    public boolean getUseClientMode() {
        return true;
    }

    @Override
    public void setNeedClientAuth(boolean need) {
    }

    @Override
    public boolean getNeedClientAuth() {
        return false;
    }

    @Override
    public void setWantClientAuth(boolean want) {
    }

    @Override
    public boolean getWantClientAuth() {
        return false;
    }

    @Override
    public void setEnableSessionCreation(boolean flag) {
    }

    @Override
    public boolean getEnableSessionCreation() {
        return true;
    }
}
