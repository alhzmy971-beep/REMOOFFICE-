# REMOOFFICE — Phase 2: close all remaining gaps radically

## A. SmartArt
- [x] Read SmartArtChartUtils / OoxmlDiagramChartParser / SmartArtDialog current state
- [x] Build a real Word file with SmartArt (diagramData/diagramLayout/quickStyle/colors) programmatically
- [x] Verify parse: nodes, text, levels, children, relationships, layout type
- [x] Verify round-trip: import -> model -> export -> re-import (no data loss)
- [x] Node add/delete/edit/level/layout/colors UI bound to model (dialog now .copy() preserves template)
- [x] Fix parse/export gaps: data part always regenerated from model; layout/style preserved; pPr replacement bug; parent linkage; raw XML persisted in JSON

## B. Charts
- [x] Read ChartData model + ChartDialog + chart export/import
- [x] Build real Word file with chart (c:chart + embedded xlsx)
- [x] Verify parse: type/title/categories/series/values/colors/axes/legend
- [x] Verify re-render after model edit (not static SVG)
- [x] Verify export Chart XML reopenable (strRef/numRef + embedded workbook + externalData)
- [x] Fix gaps: chart always regenerated from model; numCache/strCache parsing; scatter xVal/yVal; dialog .copy() preserves axes/legend/3D/secondary/number-format
- [x] Test: 52/52 assertions pass (SmartArt + chart round-trip, workbook readable, scatter/pie)

## C. TOC page numbers
- [x] Compute real page numbers (paginate by page-break + measured height)
- [x] Show page number in TOC entries
- [x] Refresh TOC on content change (live, no rebuild)
- [x] Export TOC field to DOCX (multi-paragraph TOC field with cached entries)
- [x] Verified in headless Chromium: explicit-break pagination, height pagination (Bottom=9), live shrink (Bottom=1)

## D. Editor tools audit
- [x] Enumerate every DynamicToolsPanel button -> callback -> EditorCommands -> editorApi (75/75 wired)
- [x] Find buttons with default {} (no-op) and implement or disable (none found; 0 empty bodies)
- [x] Verify each implemented tool end-to-end in browser (18 feature families pass: find/replace, bookmarks, footnotes, endnotes, comments, track-changes, styles, TOC)
- [x] FIXED real bug: findAndReplace corrupted attributes (href) by innerHTML.replace; now walks text nodes only

## E. Security/privacy (online conversion)
- [x] Explicit consent dialog naming provider (CloudConvert) + "file leaves device" warning
- [x] Delete remote file after conversion (success/failure/cancel all call deleteRemoteFileIfSupported)
- [x] Never log API key (verified: no Log/println/printStackTrace with key/api/token anywhere)
- [x] Clean temp files on success/failure/cancel (finally block deletes tempPdf + docxFile)
- [x] Working Cancel + timeout/network handling (60s connect / 120s read-write, 60 polls, cancel checked each step)

## F. Tests
- [x] Real DOCX SmartArt/Chart round-trip test (52/52 assertions pass)
- [x] Real PDF tests (Arabic/English/mixed/scanned) generated with reportlab; styled stripper verified: font sizes + bold/italic preserved, Arabic extracted, balanced HTML (16/16)
- [x] Editor tools smoke test in browser (18 feature families pass; find/replace attribute-corruption bug fixed)
- [x] NEW: PDF import now preserves font size + bold/italic via StyledPdfTextStripper (was plain <p>)
- [x] NEW: PDF import now preserves TEXT COLOUR. Root cause: PDFTextStripper extends LegacyPDFStreamEngine which registers only 22 text/state operators and NO colour operators, so rg/g/k/sc/scn were silently dropped and the graphics state stayed DeviceGray 0.0. Fix: register all 13 colour operator processors in StyledPdfTextStripper.init. Colour is snapshotted in processTextPosition (live state) and looked up by TextPosition identity in writeString (state is stale there). Verified 13/13 on a real 6-colour PDF (#D91A1A/#1A59D9/#1A8C26/#8033B3/#E68000) + 13/13 HTML emission test.
- [x] NEW: real Gradle build. Fixed 2 pre-existing compile blockers: PdfExporter used package-private PrintDocumentAdapter.*ResultCallback constructors (impossible in app code) -> rewritten on android.graphics.pdf.PdfDocument with full-content pagination; PdfImporter used Bitmap.getRGB -> getPixels. `:app:compileDebugKotlin` and `:app:assembleDebug` both BUILD SUCCESSFUL (app-debug.apk 79.9 MB).

## G. Push
- [x] Commit & push, honest report (commit 2120490 -> main)
