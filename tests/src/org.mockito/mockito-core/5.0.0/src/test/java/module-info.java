/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
module org.mockito.mockito_core.tests {
    requires org.assertj.core;
    requires junit;
    requires org.junit.jupiter.api;
    requires org.mockito;

    opens org_mockito.mockito_core to junit, org.junit.platform.commons, org.mockito;
}
