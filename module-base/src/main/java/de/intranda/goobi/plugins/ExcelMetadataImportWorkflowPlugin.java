package de.intranda.goobi.plugins;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;
import org.goobi.beans.Process;
import org.goobi.beans.User;
import org.goobi.managedbeans.LoginBean;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.plugin.interfaces.IWorkflowPlugin;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.forms.SpracheForm;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.persistence.managers.ProcessManager;
import jakarta.faces.model.SelectItem;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.Corporate;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.MetadataTypeNotAllowedException;

@PluginImplementation
@Log4j2
public class ExcelMetadataImportWorkflowPlugin implements IWorkflowPlugin, IPlugin {

    private static final long serialVersionUID = -1019421070242388427L;

    @Getter
    private String title = "intranda_workflow_excel_metadata_import";

    @Getter
    private List<MatchingField> headerOrder;

    @Getter
    private List<SelectItem> metadataFields;

    @Getter
    private Path excelFile;

    private Path tempFolder;
    private User user;

    private transient DataFormatter dataFormatter = new DataFormatter();

    @Getter
    private transient List<Row> rowsToImport;

    private transient List<String> processIdFields;
    private transient List<String> processTitleFields;

    @Getter
    private MatchingField processIdColumn;
    @Getter
    private MatchingField processTitleColumn;

    @Override
    public PluginType getType() {
        return PluginType.Workflow;
    }

    @Override
    public String getGui() {
        return "/uii/plugin_workflow_excel_metadata_import.xhtml";
    }

    public void uploadFile(FileUploadEvent event) {
        if (processIdFields == null) {
            loadConfiguration();
        }
        String processIdentifierValue = null;
        processTitleColumn = null;
        processIdColumn = null;
        if (user == null) {
            LoginBean login = (LoginBean) Helper.getBeanByName("LoginForm", LoginBean.class);
            if (login != null) {
                user = login.getMyBenutzer();
            }
        }

        String documentLanguage = "en";

        SpracheForm sf = (SpracheForm) Helper.getBeanByName("SpracheForm", SpracheForm.class);
        if (sf != null && sf.getLocale() != null) {
            documentLanguage = sf.getLocale().getLanguage();
        }

        try {
            if (tempFolder == null) {
                tempFolder = Paths.get(ConfigurationHelper.getInstance().getTemporaryFolder(), user.getLogin());
                if (!StorageProvider.getInstance().isFileExists(tempFolder)) {
                    StorageProvider.getInstance().createDirectories(tempFolder);
                }
            }
            UploadedFile upload = event.getFile();
            saveFileTemporary(upload.getFileName(), upload.getInputStream());

            // analyse excel header row

            try (InputStream fis = StorageProvider.getInstance().newInputStream(excelFile);
                    BOMInputStream bomInputStream = BOMInputStream.builder().setInputStream(fis).get();
                    Workbook wb = WorkbookFactory.create(bomInputStream)) {
                Sheet sheet = wb.getSheetAt(0);
                Iterator<Row> rowIterator = sheet.rowIterator();
                Row headerRow = rowIterator.next();
                int numberOfCells = headerRow.getLastCellNum();
                headerOrder = new ArrayList<>(numberOfCells);
                log.debug("Found {} cell(s)", numberOfCells);

                for (int i = 0; i < numberOfCells; i++) {
                    Cell cell = headerRow.getCell(i);
                    if (cell != null) {
                        String value = dataFormatter.formatCellValue(cell).trim();
                        MatchingField field = new MatchingField(value, i, CellReference.convertNumToColString(i));
                        boolean matchedColumn = false;
                        if (processIdColumn == null && processIdFields.stream().anyMatch(value::equalsIgnoreCase)) {
                            processIdColumn = field;
                            matchedColumn = true;
                        }
                        if (processTitleColumn == null && processTitleFields.stream().anyMatch(value::equalsIgnoreCase)) {
                            processTitleColumn = field;
                            matchedColumn = true;
                        }
                        if (!matchedColumn) {
                            headerOrder.add(field);
                        }
                    }
                }

                rowsToImport = new LinkedList<>();
                MatchingField lookupColumn = processIdColumn != null ? processIdColumn : processTitleColumn;
                if (lookupColumn != null) {
                    while (rowIterator.hasNext()) {
                        Row dataRow = rowIterator.next();
                        Cell cell = dataRow.getCell(lookupColumn.getColumnOrderNumber());
                        if (cell != null) {
                            rowsToImport.add(dataRow);
                        }
                    }

                    // find first data row and read the process identifier value

                    if (!rowsToImport.isEmpty()) {
                        Row dataRow = rowsToImport.get(0);
                        Cell cell = dataRow.getCell(lookupColumn.getColumnOrderNumber());
                        if (cell != null) {
                            String value = dataFormatter.formatCellValue(cell).trim();
                            if (!value.isEmpty()) {
                                processIdentifierValue = value;
                            }
                        }
                    }
                    log.debug("First process identifier: {}", processIdentifierValue);
                }
            }

            // abort, if no process column could be found
            if (processIdColumn == null && processTitleColumn == null) {
                log.error("Excel file contains no column matching processIdFields {} or processTitleFields {}", processIdFields, processTitleFields);
                Helper.setFehlerMeldung("plugin_workflow_excel_metadata_import_noMatchingColumn");
                cancelUpload();
                return;
            }

            // try to get ruleset from first process
            Process process = null;
            if (processIdColumn != null) {
                process = ProcessManager.getProcessById(Integer.parseInt(processIdentifierValue));
            } else {
                process = ProcessManager.getProcessByExactTitle(processIdentifierValue);
            }
            if (process == null) {
                log.error("Excel file contains non existing process {} ", processIdentifierValue);
                Helper.setFehlerMeldung(Helper.getTranslation("plugin_workflow_excel_metadata_import_noProcessFound", processIdentifierValue));
                cancelUpload();
                return;
            }
            Prefs prefs = process.getRegelsatz().getPreferences();

            // read possible metadata fields
            List<MetadataType> types = prefs.getAllMetadataTypes();

            for (MatchingField mf : headerOrder) {
                //try to assign metadata field to column
                for (MetadataType t : types) {
                    if (mf.getColumnHeader().equalsIgnoreCase(t.getName())
                            || mf.getColumnHeader().equalsIgnoreCase(t.getLanguage("de"))
                            || mf.getColumnHeader().equalsIgnoreCase(t.getLanguage("en"))) {
                        mf.setAssignedField(t.getName());
                        break;
                    }
                }
            }

            // create SelectItem list
            metadataFields = new LinkedList<>();
            for (MetadataType t : types) {
                String label = t.getLanguage(documentLanguage);
                if (StringUtils.isBlank(label)) {
                    label = t.getName();
                }
                SelectItem item = new SelectItem(t.getName(), label);
                metadataFields.add(item);
            }
            metadataFields.sort(Comparator.comparing(SelectItem::getLabel, Comparator.nullsLast(
                    Comparator.comparing((String l) -> l.startsWith("_"))
                            .thenComparing(String::compareToIgnoreCase))));

        } catch (IOException e) {
            log.error("Error while uploading file", e);
        }
    }

    public void cancelUpload() {
        if (excelFile != null) {
            StorageProvider.getInstance().deleteDataInDir(excelFile);
        }
        excelFile = null;
        headerOrder.clear();
        processTitleColumn = null;
        processIdColumn = null;
    }

    private void saveFileTemporary(String fileName, InputStream in) throws IOException {
        if (user == null) {
            LoginBean login = (LoginBean) Helper.getBeanByName("LoginForm", LoginBean.class);
            if (login != null) {
                user = login.getMyBenutzer();
            }
        }
        if (tempFolder == null) {
            tempFolder = Paths.get(ConfigurationHelper.getInstance().getTemporaryFolder(), user.getLogin());
            if (!StorageProvider.getInstance().isFileExists(tempFolder)) {
                StorageProvider.getInstance().createDirectories(tempFolder);
            }
        }
        Path targetFile = tempFolder.resolve(fileName);
        try (OutputStream out = StorageProvider.getInstance().newOutputStream(targetFile)) {
            byte[] bytes = new byte[1024];
            int read;
            while ((read = in.read(bytes)) != -1) {
                out.write(bytes, 0, read);
            }
            out.flush();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    log.error(e);
                }
            }
        }
        excelFile = targetFile;
    }

    private void loadConfiguration() {
        XMLConfiguration config = ConfigPlugins.getPluginConfig(title);
        config.setDelimiterParsingDisabled(true);
        config.setExpressionEngine(new XPathExpressionEngine());
        processIdFields = Arrays.asList(config.getStringArray("/processIdField"));
        processTitleFields = Arrays.asList(config.getStringArray("/processTitleField"));

    }

    public void importData() {
        MatchingField lookupColumn = processIdColumn != null ? processIdColumn : processTitleColumn;
        if (lookupColumn == null || rowsToImport == null || rowsToImport.isEmpty()) {
            return;
        }

        int successCount = 0;
        int errorCount = 0;

        for (Row row : rowsToImport) {
            Cell identifierCell = row.getCell(lookupColumn.getColumnOrderNumber());
            if (identifierCell == null) {
                continue;
            }
            String identifierValue = dataFormatter.formatCellValue(identifierCell).trim();
            if (StringUtils.isBlank(identifierValue)) {
                continue;
            }

            Process process;
            if (processIdColumn != null) {
                process = ProcessManager.getProcessById(Integer.parseInt(identifierValue));
            } else {
                process = ProcessManager.getProcessByExactTitle(identifierValue);
            }
            if (process == null) {
                log.error("Process not found: {}", identifierValue);
                Helper.setFehlerMeldung(Helper.getTranslation("plugin_workflow_excel_metadata_import_noProcessFound", identifierValue));
                errorCount++;
                continue;
            }

            try {
                Fileformat fileformat = process.readMetadataFile();
                DocStruct logical = fileformat.getDigitalDocument().getLogicalDocStruct();
                if (logical.getType().isAnchor()) {
                    logical = logical.getAllChildren().get(0);
                }
                Prefs prefs = process.getRegelsatz().getPreferences();
                for (MatchingField mf : headerOrder) {
                    if (mf.getAssignedField() == null) {
                        continue;
                    }
                    Cell cell = row.getCell(mf.getColumnOrderNumber());
                    if (cell == null) {
                        continue;
                    }
                    String value = dataFormatter.formatCellValue(cell).trim();
                    if (StringUtils.isBlank(value)) {
                        continue;
                    }
                    MetadataType type = prefs.getMetadataTypeByName(mf.getAssignedField());
                    if (type.getIsPerson()) {
                        importPersonField(mf, logical, value, type);
                    } else if (type.isCorporate()) {
                        importCorporateField(mf, logical, value, type);
                    } else {
                        importMetadataField(mf, logical, value, type);
                    }
                }
                process.writeMetadataFile(fileformat);
                successCount++;
            } catch (Exception e) {
                log.error("Error importing metadata for process {}", identifierValue, e);
                Helper.setFehlerMeldung(Helper.getTranslation("plugin_workflow_excel_metadata_import_errorImporting", identifierValue));
                errorCount++;
            }
        }

        log.info("Import finished: {} successful, {} errors", successCount, errorCount);
        if (errorCount == 0) {
            Helper.setMeldung(Helper.getTranslation("plugin_workflow_excel_metadata_import_importSuccessful", String.valueOf(successCount)));
        } else {
            Helper.setFehlerMeldung(Helper.getTranslation("plugin_workflow_excel_metadata_import_importFinishedWithErrors",
                    String.valueOf(successCount), String.valueOf(errorCount)));
        }
    }

    private void importPersonField(MatchingField mf, DocStruct docstruct, String value, MetadataType type) {
        if (mf.isReplaceExistingData()) {
            List<Person> existing = docstruct.getAllPersonsByType(type);
            if (existing != null) {
                for (Person per : existing) {
                    docstruct.removePerson(per, true);
                }
            }
        }

        try {
            Person per = new Person(type);
            String firstname = "";
            String lastname = "";
            if (value.contains(",")) {
                // first case: lastname, firstname
                lastname = value.substring(0, value.lastIndexOf(",")).trim();
                firstname = value.substring(value.lastIndexOf(",") + 1).trim();
            } else if (value.contains(" ")) {
                // second case: firstname lastname
                firstname = value.substring(0, value.lastIndexOf(" "));
                lastname = value.substring(value.lastIndexOf(" "));
            } else {
                // third case: only a common name is used
                firstname = value;
            }
            per.setFirstname(firstname);
            per.setLastname(lastname);
            docstruct.addPerson(per);
        } catch (MetadataTypeNotAllowedException | DocStructHasNoTypeException e) {
            log.error(e);
        }

    }

    private void importCorporateField(MatchingField mf, DocStruct docstruct, String value, MetadataType type) {
        if (mf.isReplaceExistingData()) {
            List<Corporate> existing = docstruct.getAllCorporatesByType(type);
            if (existing != null) {
                for (Corporate md : existing) {
                    docstruct.removeCorporate(md, true);
                }
            }
        }

        try {
            Corporate md = new Corporate(type);
            md.setMainName(value);
            docstruct.addCorporate(md);
        } catch (MetadataTypeNotAllowedException | DocStructHasNoTypeException e) {
            log.error(e);
        }
    }

    private void importMetadataField(MatchingField mf, DocStruct docstruct, String value, MetadataType type) {
        if (mf.isReplaceExistingData()) {
            List<? extends Metadata> existing = docstruct.getAllMetadataByType(type);
            if (existing != null) {
                for (Metadata md : existing) {
                    docstruct.removeMetadata(md, true);
                }
            }
        }

        try {
            Metadata md = new Metadata(type);
            md.setValue(value);
            docstruct.addMetadata(md);
        } catch (MetadataTypeNotAllowedException | DocStructHasNoTypeException e) {
            log.error(e);
        }
    }

    /**
     * This class is used to match the excel columns with metadata fields
     */
    @Data
    @RequiredArgsConstructor
    public class MatchingField implements Serializable {

        private static final long serialVersionUID = 7037009721345445066L;

        /**
         * Name of the header of the current column within the excel file
         */
        @NonNull
        private String columnHeader;

        /**
         * Internal order number of the current column within the excel file
         */
        @NonNull
        private Integer columnOrderNumber;

        /**
         * Displayed label the current column within the excel file (1=A, 2=B, 3=C, ...)
         */
        @NonNull
        private String columnLetter;

        /**
         * field in which the current data is imported
         */
        private String assignedField;

        private boolean replaceExistingData = false;

    }

}
