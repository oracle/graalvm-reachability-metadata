/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_jasperreports.jasperreports;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapArrayDataSource;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignExpression;
import net.sf.jasperreports.engine.design.JRDesignField;
import net.sf.jasperreports.engine.design.JRDesignParameter;
import net.sf.jasperreports.engine.design.JRDesignSection;
import net.sf.jasperreports.engine.design.JRDesignStaticText;
import net.sf.jasperreports.engine.design.JRDesignStyle;
import net.sf.jasperreports.engine.design.JRDesignTextField;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.fill.NaiveTextMeasurerFactory;
import net.sf.jasperreports.engine.type.HorizontalTextAlignEnum;
import net.sf.jasperreports.engine.type.SplitTypeEnum;
import net.sf.jasperreports.engine.type.WhenNoDataTypeEnum;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.engine.util.JRSaver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the headless report lifecycle: build a report design through the public API,
 * compile it, serialize/deserialize the compiled report ({@code JRSaver}/{@code JRLoader},
 * the same mechanism behind precompiled {@code .jasper} resources), fill it and export it
 * to PDF.
 *
 * <p>The report only uses direct field/parameter expressions, so the compiled report is
 * evaluated without any runtime bytecode generation (direct expression evaluation,
 * available since JasperReports 6.20) - the only report compilation mode that can work in
 * a native image.
 */
class JasperreportsTest {

    @BeforeAll
    static void useNaiveTextMeasurer() {
        // The default text measurer renders through java.awt.font.TextLayout, which needs
        // desktop/JNI support that is out of scope for library reachability metadata. The
        // naive measurer is the supported headless alternative.
        DefaultJasperReportsContext.getInstance().setProperty(
                "net.sf.jasperreports.text.measurer.factory", NaiveTextMeasurerFactory.class.getName());
    }

    @Test
    void compiledReportSurvivesSerializationRoundTrip() throws Exception {
        JasperReport compiled = JasperCompileManager.compileReport(createDesign());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JRSaver.saveObject(compiled, out);
        JasperReport reloaded =
                (JasperReport) JRLoader.loadObject(new ByteArrayInputStream(out.toByteArray()));

        assertThat(reloaded.getName()).isEqualTo(compiled.getName());
        assertThat(reloaded.getPageWidth()).isEqualTo(compiled.getPageWidth());
        assertThat(reloaded.getCompilerClass()).isEqualTo(compiled.getCompilerClass());
    }

    @Test
    void fillsAndExportsToPdf() throws Exception {
        JasperReport compiled = JasperCompileManager.compileReport(createDesign());

        // Round-trip through serialization first, the way precompiled .jasper files are used.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JRSaver.saveObject(compiled, out);
        JasperReport report =
                (JasperReport) JRLoader.loadObject(new ByteArrayInputStream(out.toByteArray()));

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("greeting", "Hello from a native image");
        Map<String, Object> record = new HashMap<>();
        record.put("name", "GraalVM");
        JRMapArrayDataSource dataSource = new JRMapArrayDataSource(new Object[] {record});

        JasperPrint print = JasperFillManager.fillReport(report, parameters, dataSource);
        assertThat(print.getPages()).hasSize(1);

        byte[] pdf = JasperExportManager.exportReportToPdf(print);
        String content = new String(pdf, StandardCharsets.ISO_8859_1);
        assertThat(content).startsWith("%PDF");
        // The default report font maps to the built-in Type1 Helvetica - its presence in the
        // font dictionary proves the PDF producer resolved the font metrics.
        assertThat(content).contains("Helvetica");
    }

    private static JasperDesign createDesign() throws Exception {
        JasperDesign design = new JasperDesign();
        design.setName("nativeImageSmoke");
        design.setPageWidth(595);
        design.setPageHeight(842);
        design.setColumnWidth(515);
        design.setLeftMargin(40);
        design.setRightMargin(40);
        design.setTopMargin(50);
        design.setBottomMargin(50);
        design.setWhenNoDataType(WhenNoDataTypeEnum.ALL_SECTIONS_NO_DETAIL);

        JRDesignStyle baseStyle = new JRDesignStyle();
        baseStyle.setName("Base");
        baseStyle.setDefault(true);
        baseStyle.setFontSize(10f);
        baseStyle.setHorizontalTextAlign(HorizontalTextAlignEnum.LEFT);
        design.addStyle(baseStyle);

        JRDesignParameter greeting = new JRDesignParameter();
        greeting.setName("greeting");
        greeting.setValueClass(String.class);
        design.addParameter(greeting);

        JRDesignField name = new JRDesignField();
        name.setName("name");
        name.setValueClass(String.class);
        design.addField(name);

        JRDesignBand title = new JRDesignBand();
        title.setHeight(60);
        title.setSplitType(SplitTypeEnum.STRETCH);
        JRDesignStaticText staticText = new JRDesignStaticText();
        staticText.setX(0);
        staticText.setY(0);
        staticText.setWidth(515);
        staticText.setHeight(20);
        staticText.setText("JasperReports on GraalVM Native Image");
        title.addElement(staticText);
        JRDesignTextField greetingField = new JRDesignTextField();
        greetingField.setX(0);
        greetingField.setY(25);
        greetingField.setWidth(515);
        greetingField.setHeight(20);
        greetingField.setExpression(new JRDesignExpression("$P{greeting}"));
        title.addElement(greetingField);
        design.setTitle(title);

        JRDesignBand detail = new JRDesignBand();
        detail.setHeight(30);
        JRDesignTextField nameField = new JRDesignTextField();
        nameField.setX(0);
        nameField.setY(0);
        nameField.setWidth(515);
        nameField.setHeight(20);
        nameField.setExpression(new JRDesignExpression("$F{name}"));
        detail.addElement(nameField);
        ((JRDesignSection) design.getDetailSection()).addBand(detail);

        return design;
    }
}
