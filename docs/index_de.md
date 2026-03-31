---
title: Excel Metadaten Import
identifier: intranda_workflow_excel_metadata_import
description: Workflow Plugin zum Importieren von Metadaten aus einer Excel-Datei in bestehende Goobi-Vorgänge
published: false
keywords:
    - Goobi workflow
    - Plugin
    - Workflow Plugin
    - Metadaten
    - Excel
    - Import
---

## Einführung
Dieses Workflow-Plugin ermöglicht den Massenimport von Metadaten aus einer Excel-Datei (`.xlsx`, `.xls`) in bestehende Goobi-Vorgänge. Nach dem Upload wird die Kopfzeile der Tabelle analysiert, die Spalten werden den verfügbaren Metadatenfeldern zugeordnet, und anschließend werden die Daten zeilenweise in die entsprechenden Vorgänge geschrieben.

## Installation
Um das Plugin nutzen zu können, müssen folgende Dateien installiert werden:

```bash
/opt/digiverso/goobi/plugins/workflow/plugin-workflow-excel-metadata-import-base.jar
/opt/digiverso/goobi/plugins/GUI/plugin-workflow-excel-metadata-import-gui.jar
/opt/digiverso/goobi/config/plugin_intranda_workflow_excel_metadata_import.xml
```

Für eine Nutzung dieses Plugins muss der Nutzer über die korrekte Rollenberechtigung verfügen.

![Ohne korrekte Berechtigung ist das Plugin nicht nutzbar](screen1_de.png)

Bitte weisen Sie daher der Gruppe die Rolle `Plugin_workflow_excel_metadata_import` zu.

![Korrekt zugewiesene Rolle für die Nutzer](screen2_de.png)


## Überblick und Funktionsweise
Wenn das Plugin korrekt installiert und konfiguriert wurde, ist es innerhalb des Menüpunkts `Workflow` zu finden.

![Nutzeroberfläche des Plugins](screen3_de.png)

### Ablauf

**1. Excel-Datei hochladen**

Im oberen Bereich der Oberfläche befindet sich eine Upload-Komponente. Die Datei kann per Drag & Drop oder über den Dateiauswahl-Dialog übergeben werden. Unterstützte Formate sind `.xlsx` und `.xls`.

**2. Analyse der Kopfzeile**

Nach dem Upload liest das Plugin die erste Zeile der Tabelle als Kopfzeile ein. Dabei wird geprüft, ob mindestens eine Spalte einem der konfigurierten Werte aus `processIdField` oder `processTitleField` entspricht (Groß-/Kleinschreibung wird ignoriert).

- Wird eine passende **ID-Spalte** gefunden, wird der Vorgang über die numerische Vorgangs-ID gesucht.
- Wird eine passende **Titel-Spalte** gefunden, wird der Vorgang über den exakten Vorgangstitel gesucht.
- Wird **keine** passende Spalte gefunden, bricht das Plugin mit einer Fehlermeldung ab.

Die ID- bzw. Titel-Spalte wird aus der Mapping-Tabelle herausgenommen und dient ausschließlich zur Identifikation des Vorgangs.

**3. Spaltenzuordnung**

Alle übrigen Spalten werden in einer Tabelle dargestellt. Für jede Spalte kann über ein Dropdown-Menü ein Metadatenfeld aus dem Regelsatz des ersten gefundenen Vorgangs ausgewählt werden. Das Plugin versucht dabei, Spaltenname und Metadatenbezeichnung automatisch zuzuordnen (Vergleich mit internem Namen sowie deutscher und englischer Bezeichnung).

Zusätzlich kann pro Spalte über Radio-Buttons festgelegt werden, ob vorhandene Metadaten desselben Typs **ergänzt** oder **ersetzt** werden sollen.

**4. Import starten**

Über den Import-Button werden alle Datenzeilen verarbeitet. Für jede Zeile wird der Vorgang geladen, die konfigurierten Metadaten geschrieben und die Metadaten-Datei gespeichert. Zeilen ohne Identifikationswert werden übersprungen. Am Ende wird eine Zusammenfassung mit Erfolgs- und Fehlerzähler angezeigt.


## Konfiguration
Die Konfiguration des Plugins erfolgt in der Datei `plugin_intranda_workflow_excel_metadata_import.xml` wie hier aufgezeigt:

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

Die folgende Tabelle enthält eine Zusammenstellung der Parameter und ihrer Beschreibungen:

Parameter               | Erläuterung
------------------------|------------------------------------
`processIdField`        | Mögliche Spaltennamen für die Vorgangs-ID-Spalte. Es können mehrere Werte angegeben werden. Der Vergleich erfolgt ohne Berücksichtigung der Groß-/Kleinschreibung. Wird eine solche Spalte gefunden, wird der Vorgang über die numerische ID geladen.
`processTitleField`     | Mögliche Spaltennamen für die Vorgangstitel-Spalte. Es können mehrere Werte angegeben werden. Der Vergleich erfolgt ohne Berücksichtigung der Groß-/Kleinschreibung. Wird eine solche Spalte gefunden, wird der Vorgang über den exakten Titel gesucht.
