/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_poi.poi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;

import org.apache.poi.POIDocument;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.extractor.POITextExtractor;
import org.apache.poi.hpsf.CustomProperties;
import org.apache.poi.hpsf.DocumentSummaryInformation;
import org.apache.poi.hpsf.SummaryInformation;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.PaneInformation;
import org.apache.logging.log4j.message.DefaultFlowMessageFactory;
import org.apache.logging.log4j.message.FlowMessageFactory;
import org.apache.logging.log4j.message.MessageFactory2;
import org.apache.logging.log4j.message.ReusableMessageFactory;
import org.junit.jupiter.api.Test;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

class PoiTest {
    @Test
    void binaryWorkbookRoundTripsAcrossWorkbookAndExtractorApis() throws Exception {
        byte[] workbookBytes = createWorkbookBytes();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(workbookBytes))) {
            assertThat(workbook).isInstanceOf(HSSFWorkbook.class);
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            assertThat(workbook.getSheetName(0)).isEqualTo("Data");
            assertThat(workbook.getActiveSheetIndex()).isEqualTo(0);
            assertThat(workbook.getPrintArea(0)).isEqualTo("Data!$A$1:$D$4");

            Name totalsRange = workbook.getName("Totals");
            assertThat(totalsRange).isNotNull();
            assertThat(totalsRange.getRefersToFormula()).isEqualTo("Data!$B$2:$B$3");

            Sheet sheet = workbook.getSheetAt(0);
            PaneInformation paneInformation = sheet.getPaneInformation();
            assertThat(paneInformation).isNotNull();
            assertThat(paneInformation.isFreezePane()).isTrue();
            assertThat(paneInformation.getVerticalSplitLeftColumn()).isEqualTo((short) 1);
            assertThat(paneInformation.getHorizontalSplitTopRow()).isEqualTo((short) 1);
            assertThat(sheet.getActiveCell()).isEqualTo(new CellAddress(1, 0));
            assertThat(sheet.getNumMergedRegions()).isEqualTo(1);
            assertThat(sheet.getMergedRegion(0).formatAsString()).isEqualTo("A1:D1");
            assertThat(sheet.getHyperlinkList()).hasSize(1);
            assertThat(sheet.getCellComments()).hasSize(1);

            Row firstDataRow = sheet.getRow(1);
            assertThat(firstDataRow.getCell(0).getStringCellValue()).isEqualTo("North");
            assertThat(firstDataRow.getCell(1).getNumericCellValue()).isEqualTo(12.5d);

            Cell dateCell = firstDataRow.getCell(2);
            assertThat(DateUtil.isCellDateFormatted(dateCell)).isTrue();

            DataFormatter formatter = new DataFormatter(Locale.US);
            assertThat(formatter.formatCellValue(dateCell)).isEqualTo("2024-02-03");

            Cell linkCell = firstDataRow.getCell(3);
            Hyperlink hyperlink = linkCell.getHyperlink();
            assertThat(hyperlink).isNotNull();
            assertThat(hyperlink.getAddress()).isEqualTo("https://poi.apache.org/");
            assertThat(linkCell.getStringCellValue()).isEqualTo("POI");

            Comment comment = linkCell.getCellComment();
            assertThat(comment).isNotNull();
            assertThat(comment.getAuthor()).isEqualTo("metadata-forge");
            assertThat(comment.getString().getString()).isEqualTo("Verified");

            Cell formulaCell = sheet.getRow(3).getCell(1);
            assertThat(formulaCell.getCellType()).isEqualTo(CellType.FORMULA);
            assertThat(formulaCell.getCellFormula()).isEqualTo("SUM(B2:B3)");

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            CellValue evaluatedValue = evaluator.evaluate(formulaCell);
            assertThat(evaluatedValue.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(evaluatedValue.getNumberValue()).isEqualTo(42.5d);
            assertThat(formatter.formatCellValue(formulaCell, evaluator)).isEqualTo("42.5");

            HSSFWorkbook hssfWorkbook = (HSSFWorkbook) workbook;
            SummaryInformation summaryInformation = hssfWorkbook.getSummaryInformation();
            DocumentSummaryInformation documentSummaryInformation = hssfWorkbook.getDocumentSummaryInformation();
            assertThat(summaryInformation).isNotNull();
            assertThat(summaryInformation.getTitle()).isEqualTo("Quarterly totals");
            assertThat(summaryInformation.getAuthor()).isEqualTo("metadata-forge");
            assertThat(summaryInformation.getApplicationName()).isEqualTo("Apache POI");
            assertThat(documentSummaryInformation).isNotNull();
            assertThat(documentSummaryInformation.getCategory()).isEqualTo("integration-test");
            assertThat(documentSummaryInformation.getCompany()).isEqualTo("GraalVM");
            assertThat(documentSummaryInformation.getManager()).isEqualTo("native-image");

            CustomProperties customProperties = documentSummaryInformation.getCustomProperties();
            assertThat(customProperties).isNotNull();
            assertThat(customProperties.get("Region")).isEqualTo("EMEA");
            assertThat(customProperties.get("Approved")).isEqualTo(Boolean.TRUE);
        }

        try (POIFSFileSystem fileSystem = new POIFSFileSystem(new ByteArrayInputStream(workbookBytes))) {
            assertThat(fileSystem.getRoot().getEntryNames()).contains(
                    HSSFWorkbook.getWorkbookDirEntryName(fileSystem.getRoot()),
                    SummaryInformation.DEFAULT_STREAM_NAME,
                    DocumentSummaryInformation.DEFAULT_STREAM_NAME);
        }

        try (POITextExtractor extractor = ExtractorFactory.createExtractor(new ByteArrayInputStream(workbookBytes))) {
            String extractedText = extractor.getText();
            assertThat(extractedText).contains("Quarterly totals", "North", "South", "POI", "42.5");
        }
    }

    @Test
    void poifsFileSystemStoresNestedDocumentsAndReadsThemBack() throws Exception {
        byte[] readmeBytes = "Apache POI OLE2".getBytes(StandardCharsets.UTF_8);
        byte[] payloadBytes = new byte[] {0, 1, 2, 3, 4, 5};
        byte[] filesystemBytes;

        try (POIFSFileSystem fileSystem = new POIFSFileSystem()) {
            DirectoryEntry root = fileSystem.getRoot();
            DirectoryEntry attachmentsDirectory = root.createDirectory("Attachments");
            DirectoryEntry yearlyDirectory = attachmentsDirectory.createDirectory("2024");
            DocumentEntry readmeEntry = attachmentsDirectory.createDocument("readme.txt", new ByteArrayInputStream(readmeBytes));
            DocumentEntry payloadEntry = yearlyDirectory.createDocument("payload.bin", new ByteArrayInputStream(payloadBytes));

            assertThat(root.getEntryNames()).contains("Attachments");
            assertThat(attachmentsDirectory.getEntryNames()).contains("2024", "readme.txt");
            assertThat(readmeEntry.getSize()).isEqualTo(readmeBytes.length);
            assertThat(payloadEntry.getSize()).isEqualTo(payloadBytes.length);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            fileSystem.writeFilesystem(outputStream);
            filesystemBytes = outputStream.toByteArray();
        }

        try (POIFSFileSystem fileSystem = new POIFSFileSystem(new ByteArrayInputStream(filesystemBytes))) {
            DirectoryEntry root = fileSystem.getRoot();
            DirectoryEntry attachmentsDirectory = (DirectoryEntry) root.getEntry("Attachments");
            DirectoryEntry yearlyDirectory = (DirectoryEntry) attachmentsDirectory.getEntry("2024");

            assertThat(root.getEntryNames()).contains("Attachments");
            assertThat(attachmentsDirectory.getEntryNames()).contains("2024", "readme.txt");
            assertThat(readDocumentBytes(attachmentsDirectory, "readme.txt"))
                    .isEqualTo(readmeBytes);
            assertThat(readDocumentBytes(yearlyDirectory, "payload.bin"))
                    .containsExactly(payloadBytes);
            assertThat(new String(readDocumentBytes(attachmentsDirectory, "readme.txt"), StandardCharsets.UTF_8))
                    .isEqualTo("Apache POI OLE2");
        }
    }

    @Test
    void workbookRoundTripsSheetDataValidations() throws Exception {
        byte[] workbookBytes = createWorkbookWithDataValidationsBytes();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(workbookBytes))) {
            Sheet sheet = workbook.getSheet("Validation");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getDataValidations()).hasSize(2);

            DataValidation listValidation = findValidationByRegion(sheet, "A2:A4");
            DataValidationConstraint listConstraint = listValidation.getValidationConstraint();
            assertThat(listConstraint.getValidationType()).isEqualTo(DataValidationConstraint.ValidationType.LIST);
            assertThat(listConstraint.getExplicitListValues()).containsExactly("North", "South", "West");
            assertThat(listValidation.getEmptyCellAllowed()).isFalse();
            assertThat(listValidation.getShowPromptBox()).isTrue();
            assertThat(listValidation.getPromptBoxTitle()).isEqualTo("Region");
            assertThat(listValidation.getPromptBoxText()).isEqualTo("Choose a supported region");
            assertThat(listValidation.getShowErrorBox()).isTrue();
            assertThat(listValidation.getErrorBoxTitle()).isEqualTo("Invalid region");
            assertThat(listValidation.getErrorBoxText()).isEqualTo("Use one of the supported regions");
            assertThat(listValidation.getRegions().countRanges()).isEqualTo(1);
            assertThat(listValidation.getRegions().getCellRangeAddress(0).formatAsString()).isEqualTo("A2:A4");

            DataValidation quantityValidation = findValidationByRegion(sheet, "B2:B4");
            DataValidationConstraint quantityConstraint = quantityValidation.getValidationConstraint();
            assertThat(quantityConstraint.getValidationType()).isEqualTo(DataValidationConstraint.ValidationType.FORMULA);
            assertThat(quantityConstraint.getFormula1()).isEqualTo("AND(B2>=1,B2<=100)");
            assertThat(quantityValidation.getShowPromptBox()).isTrue();
            assertThat(quantityValidation.getPromptBoxTitle()).isEqualTo("Quantity");
            assertThat(quantityValidation.getPromptBoxText()).isEqualTo("Enter a value between 1 and 100");
            assertThat(quantityValidation.getShowErrorBox()).isTrue();
            assertThat(quantityValidation.getErrorBoxTitle()).isEqualTo("Invalid quantity");
            assertThat(quantityValidation.getErrorBoxText()).isEqualTo("Quantity must stay between 1 and 100");
            assertThat(quantityValidation.getRegions().countRanges()).isEqualTo(1);
            assertThat(quantityValidation.getRegions().getCellRangeAddress(0).formatAsString()).isEqualTo("B2:B4");
        }
    }

    private static byte[] createWorkbookBytes() throws Exception {
        try (HSSFWorkbook workbook = new HSSFWorkbook()) {
            workbook.createInformationProperties();
            SummaryInformation summaryInformation = workbook.getSummaryInformation();
            DocumentSummaryInformation documentSummaryInformation = workbook.getDocumentSummaryInformation();
            assertThat(summaryInformation).isNotNull();
            assertThat(documentSummaryInformation).isNotNull();

            summaryInformation.setTitle("Quarterly totals");
            summaryInformation.setAuthor("metadata-forge");
            summaryInformation.setApplicationName("Apache POI");
            documentSummaryInformation.setCategory("integration-test");
            documentSummaryInformation.setCompany("GraalVM");
            documentSummaryInformation.setManager("native-image");

            CustomProperties customProperties = new CustomProperties();
            customProperties.put("Region", "EMEA");
            customProperties.put("Approved", Boolean.TRUE);
            documentSummaryInformation.setCustomProperties(customProperties);

            Sheet sheet = workbook.createSheet("Data");
            workbook.setActiveSheet(0);
            workbook.setPrintArea(0, 0, 3, 0, 3);

            CellStyle dateStyle = workbook.createCellStyle();
            short dateFormat = workbook.createDataFormat().getFormat("yyyy-mm-dd");
            dateStyle.setDataFormat(dateFormat);

            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue("Quarterly totals");
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

            Row firstDataRow = sheet.createRow(1);
            firstDataRow.createCell(0).setCellValue("North");
            firstDataRow.createCell(1).setCellValue(12.5d);
            Cell northDateCell = firstDataRow.createCell(2);
            northDateCell.setCellValue(Date.from(Instant.parse("2024-02-03T00:00:00Z")));
            northDateCell.setCellStyle(dateStyle);
            Cell northLinkCell = firstDataRow.createCell(3);
            northLinkCell.setCellValue("POI");

            Row secondDataRow = sheet.createRow(2);
            secondDataRow.createCell(0).setCellValue("South");
            secondDataRow.createCell(1).setCellValue(30.0d);
            Cell southDateCell = secondDataRow.createCell(2);
            southDateCell.setCellValue(Date.from(Instant.parse("2024-02-04T00:00:00Z")));
            southDateCell.setCellStyle(dateStyle);

            Row totalRow = sheet.createRow(3);
            totalRow.createCell(0).setCellValue("Total");
            totalRow.createCell(1).setCellFormula("SUM(B2:B3)");

            sheet.createFreezePane(1, 1);
            sheet.setActiveCell(new CellAddress(1, 0));

            Name totalsRange = workbook.createName();
            totalsRange.setNameName("Totals");
            totalsRange.setRefersToFormula("Data!$B$2:$B$3");

            CreationHelper creationHelper = workbook.getCreationHelper();
            Hyperlink hyperlink = creationHelper.createHyperlink(HyperlinkType.URL);
            hyperlink.setAddress("https://poi.apache.org/");
            northLinkCell.setHyperlink(hyperlink);

            Drawing<?> drawing = sheet.createDrawingPatriarch();
            ClientAnchor anchor = creationHelper.createClientAnchor();
            anchor.setCol1(3);
            anchor.setCol2(4);
            anchor.setRow1(1);
            anchor.setRow2(3);
            Comment comment = drawing.createCellComment(anchor);
            comment.setAuthor("metadata-forge");
            comment.setString(creationHelper.createRichTextString("Verified"));
            northLinkCell.setCellComment(comment);

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            evaluator.evaluateFormulaCell(totalRow.getCell(1));

            return writeDocument(workbook);
        }
    }

    private static byte[] createWorkbookWithDataValidationsBytes() throws Exception {
        try (HSSFWorkbook workbook = new HSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Validation");

            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Region");
            headerRow.createCell(1).setCellValue("Quantity");

            DataValidationHelper validationHelper = sheet.getDataValidationHelper();

            DataValidationConstraint listConstraint = validationHelper.createExplicitListConstraint(
                    new String[] {"North", "South", "West"});
            DataValidation listValidation = validationHelper.createValidation(
                    listConstraint,
                    new CellRangeAddressList(1, 3, 0, 0));
            listValidation.setEmptyCellAllowed(false);
            listValidation.setShowPromptBox(true);
            listValidation.createPromptBox("Region", "Choose a supported region");
            listValidation.setShowErrorBox(true);
            listValidation.createErrorBox("Invalid region", "Use one of the supported regions");
            sheet.addValidationData(listValidation);

            DataValidationConstraint quantityConstraint = validationHelper.createCustomConstraint("AND(B2>=1,B2<=100)");
            DataValidation quantityValidation = validationHelper.createValidation(
                    quantityConstraint,
                    new CellRangeAddressList(1, 3, 1, 1));
            quantityValidation.setShowPromptBox(true);
            quantityValidation.createPromptBox("Quantity", "Enter a value between 1 and 100");
            quantityValidation.setShowErrorBox(true);
            quantityValidation.createErrorBox("Invalid quantity", "Quantity must stay between 1 and 100");
            sheet.addValidationData(quantityValidation);

            sheet.createRow(1).createCell(0).setCellValue("North");
            sheet.getRow(1).createCell(1).setCellValue(12);
            sheet.createRow(2).createCell(0).setCellValue("South");
            sheet.getRow(2).createCell(1).setCellValue(24);

            return writeDocument(workbook);
        }
    }

    private static DataValidation findValidationByRegion(Sheet sheet, String expectedRegion) {
        return sheet.getDataValidations().stream()
                .filter(validation -> validation.getRegions().countRanges() == 1)
                .filter(validation -> validation.getRegions().getCellRangeAddress(0).formatAsString().equals(expectedRegion))
                .findFirst()
                .orElseThrow();
    }

    private static byte[] writeDocument(POIDocument document) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        document.write(outputStream);
        return outputStream.toByteArray();
    }

    private static byte[] readDocumentBytes(DirectoryEntry directory, String documentName) throws Exception {
        try (DocumentInputStream inputStream = new DocumentInputStream((DocumentEntry) directory.getEntry(documentName))) {
            return inputStream.readAllBytes();
        }
    }
}

@TargetClass(className = "org.apache.logging.log4j.spi.AbstractLogger")
final class Target_org_apache_logging_log4j_spi_AbstractLogger {

    @Substitute
    private static MessageFactory2 createDefaultMessageFactory() {
        return ReusableMessageFactory.INSTANCE;
    }

    @Substitute
    private static FlowMessageFactory createDefaultFlowMessageFactory() {
        return new DefaultFlowMessageFactory();
    }
}
