# REMOOFFICE — Radical import/edit/export fix (honest, verified increment)

## 0. Setup
- [x] Clone repo, read architecture
- [x] Install JDK + standalone kotlinc for pure-Kotlin compile/test

## 1. Tables (editor.html) — real interactive editor
- [x] Descriptor-grid model (rowspan/colspan safe) + rebuild renderer
- [x] Cell/row/col/range selection with visible handles
- [x] Add row above/below, add col left/right, delete row/col
- [x] Drag-resize columns & rows; numeric width/height; table width/height
- [x] Merge full range (2x2+), split preserving rowspan/colspan, keep content
- [x] Per-side borders (top/bottom/left/right/insideH/insideV), color/width/style
- [x] Cell background, H/V alignment, header-row repeat, move table
- [x] Snapshot undo/redo for structural ops
- [x] getEditorModel() exposes structured tables for model sync
- [x] JS harness: 21/21 table assertions PASS

## 2. Images (editor.html) — real interactive
- [x] Selection frame + 8 resize handles + rotate handle
- [x] Drag move/resize, aspect lock, numeric X/Y/W/H
- [x] Rotate, flip H/V, crop (clip-path insets)
- [x] Wrap modes, z-order, align, opacity, border color/width, radius
- [x] Copy/paste/delete; persist props in data-* + inline styles
- [x] JS harness: images/shapes/textboxes 58/58 assertions PASS

## 3. Shapes & TextBoxes (editor.html)
- [x] EditableShape/TextBox JSON model + render
- [x] Select/move/resize/rotate handles, fill/stroke, text edit, z-order, group/ungroup

## 4. Kotlin models + persistence
- [x] EditableShape, EditableTextBox, EditableGroup, ShapeTransform, ShapeStyle, EditableTable, EditableImage
- [x] Add lists to DocumentPayload; serialize/deserialize in DocumentRepository (+ HTML extraction fallback)
- [x] Compile-check EditorModels.kt with kotlinc; pure round-trip test 18/18 PASS
- [x] HtmlSanitizer now preserves data-field/data-remo-*/data-shape-*/data-group-*/data-header-repeat (8/8 PASS)

## 5. PDF import (PdfImporter)
- [x] Remove global totalTextLength<10 threshold and 20-page cap
- [x] Per-page processing: text / image / OCR decision per page
- [x] OCR engine: Tesseract4Android (tess-two 9.1.0) with bundled ara+eng traineddata (real Arabic+English)
- [x] Accurate DocumentImportReport (pages, text/ocr/failed, tables/images/shapes/links, confidence, reasons)

## 6. PDF export/share (PdfExporter)
- [x] Save real PDF file; share via FileProvider ACTION_SEND
- [x] PNG/JPEG export + share; safe filenames; error handling

## 7. DOCX round-trip
- [x] Table export honours rowspan/colspan + cell props (grid-based physical cells)
- [x] Table import reads gridSpan/vMerge + cell props
- [x] Compile-check DocxConverter.kt against real POI 5.2.3 (EXIT 0)
- [x] Real HTML->DOCX->re-import test 13/13 PASS (spans, background, alignment, text, no phantom cells)
- [x] Dynamic PAGE/DATE emitted as real Word fldSimple fields (verified in document.xml)

## 8. Tests
- [x] JS harness for editor.html (headless Chromium): table 21/21, image/shape 58/58
- [x] Kotlin pure tests: model round-trip 18/18, DOCX round-trip 13/13, fields, sanitizer 8/8

## 9. UI wiring
- [x] Export-image buttons (PNG/JPG) in DynamicToolsPanel -> exportAndShareImage
- [x] Import report dialog shows PDF page/OCR fields
- [x] Dynamic page number + date fields (refresh on content change)

## 10. Remaining gaps (honest — NOT done)
- [ ] SmartArt section review against real Word/LibreOffice files (existing impl retained, not re-verified)
- [ ] Chart section review against real Word/LibreOffice files (existing impl retained, not re-verified)
- [ ] TOC page numbers (TOC regenerates from headings but shows no page numbers)
- [ ] Full editor-tools audit (section 12) — many callbacks exist but not every button re-verified end-to-end
- [ ] Security/privacy audit (section 13) for online conversion
- [ ] Real-file PDF tests (Arabic/English/scanned/mixed/>20 pages) — requires device with tess-two native libs
- [ ] Full Gradle/Android build — no Android SDK in this environment

## 11. Report + push
- [x] Commit & push to alhzmy971-beep/REMOOFFICE- (commit 0d92dbb)
- [x] Honest report: files changed, tests run/results, remaining limitations
