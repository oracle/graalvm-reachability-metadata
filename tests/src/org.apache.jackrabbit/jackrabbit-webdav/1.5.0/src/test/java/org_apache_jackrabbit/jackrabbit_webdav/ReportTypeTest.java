/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.version.report.Report;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.apache.jackrabbit.webdav.version.report.ReportType;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class ReportTypeTest {
    @Test
    public void registerAndCreateReportInstantiateReportClass() throws Exception {
        CountingReport.constructorCalls = 0;
        CountingReport.lastInstance = null;
        CountingReport.registeredType = null;

        Namespace namespace = Namespace.getNamespace("coverage", "http://example.com/jackrabbit-webdav/coverage");
        String localName = "coverage-report-" + System.nanoTime();

        ReportType reportType = ReportType.register(localName, namespace, CountingReport.class);
        CountingReport.registeredType = reportType;

        assertThat(reportType.getLocalName()).isEqualTo(localName);
        assertThat(reportType.getNamespace()).isSameAs(namespace);
        assertThat(CountingReport.constructorCalls).isEqualTo(1);

        ReportInfo reportInfo = new ReportInfo(reportType);
        Report report = reportType.createReport(null, reportInfo);

        assertThat(report).isInstanceOf(CountingReport.class);
        assertThat(CountingReport.constructorCalls).isEqualTo(2);
        assertThat(CountingReport.lastInstance).isSameAs(report);
        assertThat(CountingReport.lastInstance.resource).isNull();
        assertThat(CountingReport.lastInstance.info).isSameAs(reportInfo);
        assertThat(report.getType()).isSameAs(reportType);
        assertThat(reportType.isRequestedReportType(reportInfo)).isTrue();
        assertThat(ReportType.getType(reportInfo)).isSameAs(reportType);
    }

    public static final class CountingReport implements Report {
        private static int constructorCalls;
        private static CountingReport lastInstance;
        private static ReportType registeredType;

        private DavResource resource;
        private ReportInfo info;

        public CountingReport() {
            constructorCalls++;
        }

        public ReportType getType() {
            return registeredType;
        }

        public boolean isMultiStatusReport() {
            return false;
        }

        public void init(DavResource resource, ReportInfo info) throws DavException {
            this.resource = resource;
            this.info = info;
            lastInstance = this;
        }

        public Element toXml(Document document) {
            return document.createElement("coverage-report");
        }
    }
}
