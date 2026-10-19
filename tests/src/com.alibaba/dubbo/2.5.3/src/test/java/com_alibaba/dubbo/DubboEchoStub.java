/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

public class DubboEchoStub implements AbstractInterfaceConfigTest.Echo {
    private final AbstractInterfaceConfigTest.Echo delegate;

    public DubboEchoStub(AbstractInterfaceConfigTest.Echo delegate) {
        this.delegate = delegate;
    }

    @Override
    public String echo(String value) {
        return delegate.echo(value);
    }
}
