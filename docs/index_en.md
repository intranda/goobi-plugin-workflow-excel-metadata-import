---
title: Excel Metadata Import
identifier: intranda_workflow_excel_metadata_import
description: Workflow plugin for importing metadata from an Excel file into existing Goobi processes
published: false
keywords:
    - Goobi workflow
    - Plugin
    - Workflow Plugin
    - Metadata
    - Excel
    - Import
---

## Introduction
This workflow plugin enables the bulk import of metadata from an Excel file (`.xlsx`, `.xls`) into existing Goobi processes. After uploading, the header row of the spreadsheet is analysed, columns are mapped to the available metadata fields, and the data is then written row by row into the corresponding processes.

## Installation
In order to use the plugin, the following files must be installed:

```bash
/opt/digiverso/goobi/plugins/workflow/plugin-workflow-excel-metadata-import-base.jar
/opt/digiverso/goobi/plugins/GUI/plugin-workflow-excel-metadata-import-gui.jar
/opt/digiverso/goobi/config/plugin_intranda_workflow_excel_metadata_import.xml
```

To use this plugin, the user must have the correct role authorisation.

![The plugin cannot be used without correct authorisation](screen1_en.png)

Therefore, please assign the role `Plugin_workflow_excel_metadata_import` to the group.

![Correctly assigned role for users](screen2_en.png)


## Overview and functionality
If the plugin has been installed and configured correctly, it can be found under the `Workflow` menu item.

![User interface of the plugin](screen3_en.png)

### Workflow

**1. Upload the Excel file**

The upper section of the interface contains an upload component. The file can be provided via drag & drop or through the file selection dialog. Supported formats are `.xlsx` and `.xls`.

**2. Header row analysis**

After uploading, the plugin reads the first row of the spreadsheet as the header row. It checks whether at least one column matches one of the configured values from `processIdField` or `processTitleField` (case-insensitive comparison).

- If a matching **ID column** is found, the process is looked up by its numeric process ID.
- If a matching **title column** is found, the process is looked up by its exact process title.
- If **no** matching column is found, the plugin aborts with an error message.

The ID or title column is excluded from the mapping table and is used solely for process identification.

**3. Column mapping**

All remaining columns are displayed in a table. For each column, a metadata field from the ruleset of the first matched process can be selected via a dropdown menu. The plugin attempts to automatically assign a metadata field by comparing the column name with the internal name as well as the German and English labels of each metadata type.

Additionally, radio buttons on each row allow you to specify whether existing metadata of the same type should be **added to** or **replaced**.

**4. Start the import**

Clicking the import button processes all data rows. For each row the process is loaded, the configured metadata is written, and the metadata file is saved. Rows without an identification value are skipped. At the end, a summary showing the number of successful and failed imports is displayed.


## Configuration
The plugin is configured in the file `plugin_intranda_workflow_excel_metadata_import.xml` as shown here:

```xml
<config_plugin>

    <!-- possible names for the process id column -->
    <processIdField>VorgangsID</processIdField>
    <processIdField>Vorgang-ID</processIdField>
    <processIdField>ID</processIdField>
    <processIdField>Processid</processIdField>

    <!-- possible names for the process title column -->
    <processTitleField>Vorgangstitel</processTitleField>
    <processTitleField>process title</processTitleField>
    <processTitleField>processtitle</processTitleField>
    <processTitleField>Title</processTitleField>

</config_plugin>
```

The following table contains a summary of the parameters and their descriptions:

Parameter               | Explanation
------------------------|------------------------------------
`processIdField`        | Possible column names for the process ID column. Multiple values can be specified. The comparison is case-insensitive. If such a column is found, the process is loaded by its numeric ID.
`processTitleField`     | Possible column names for the process title column. Multiple values can be specified. The comparison is case-insensitive. If such a column is found, the process is looked up by its exact title.
