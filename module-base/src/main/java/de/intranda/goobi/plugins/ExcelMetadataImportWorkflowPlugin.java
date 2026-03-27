package de.intranda.goobi.plugins;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.input.BOMInputStream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;
import org.goobi.beans.User;
import org.goobi.managedbeans.LoginBean;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.plugin.interfaces.IWorkflowPlugin;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;

import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.MetadataType;

@PluginImplementation
@Log4j2
public class ExcelMetadataImportWorkflowPlugin implements IWorkflowPlugin, IPlugin {

    private static final long serialVersionUID = -1019421070242388427L;

    @Getter
    private String title = "intranda_workflow_excel_metadata_import";

    @Getter
    private List<MatchingField> headerOrder;

    @Getter
    private List<MetadataType> metadataFields;

    @Getter
    private Path excelFile;

    private Path tempFolder;
    private User user;

    private transient DataFormatter dataFormatter = new DataFormatter();
    private transient List<Row> rowsToImport;

    @Override
    public PluginType getType() {
        return PluginType.Workflow;
    }

    @Override
    public String getGui() {
        return "/uii/plugin_workflow_excel_metadata_import.xhtml";
    }

    public void uploadFile(FileUploadEvent event) {
        if (user == null) {
            LoginBean login = (LoginBean) Helper.getBeanByName("LoginForm", LoginBean.class);
            if (login != null) {
                user = login.getMyBenutzer();
            }
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

            //TODO analyse excel header

            try (InputStream fis = StorageProvider.getInstance().newInputStream(excelFile);
                    BOMInputStream bomInputStream = BOMInputStream.builder().setInputStream(fis).get();
                    Workbook wb = WorkbookFactory.create(bomInputStream)) {
                Sheet sheet = wb.getSheetAt(0);
                Iterator<Row> rowIterator = sheet.rowIterator();
                Row headerRow = rowIterator.next();
                int numberOfCells = headerRow.getLastCellNum();
                headerOrder = new ArrayList<>(numberOfCells);
                log.debug("Found {} cell(s)", numberOfCells);

                rowsToImport = new LinkedList<>();
                for (int i = 0; i < numberOfCells; i++) {
                    Cell cell = headerRow.getCell(i);
                    if (cell != null) {
                        String value = dataFormatter.formatCellValue(cell).trim();
                        headerOrder.add(new MatchingField(value, i, CellReference.convertNumToColString(i)));
                    }
                }

            }

            //TODO find process-ID /process title column

            //TODO try to load first proces, get ruleset

            //TODO read possible metadata

            //TODO try to assign metadata field to column

        } catch (IOException e) {
            log.error("Error while uploading file", e);
        }
    }

    public void cancelUpload() {
        if (excelFile != null) {
            StorageProvider.getInstance().deleteDataInDir(excelFile);
            excelFile = null;
            headerOrder.clear();
        }
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
        private MetadataType assignedField;

    }

}
