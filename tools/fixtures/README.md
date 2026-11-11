# Test fixtures — provenance and licence

These files live in `app/src/test/resources/fixtures/` and are used by
`RealOfficeFixtureUnitTest`. They are deliberately **independent of the
application code under test**: none of them is produced by
`DocxConverter`, `OoxmlPackageEnricher` or `SmartArtChartUtils`. Importing
them therefore exercises the real parser instead of a self-consistent
round-trip.

| File | What it contains | How it was created | Licence |
|------|------------------|--------------------|---------|
| `smartart_real.docx` | One hierarchy SmartArt: `word/diagrams/data1.xml` (4 points, 4 connections), `layout1.xml`, `quickStyle1.xml`, `colors1.xml`, plus the `dgm:relIds` drawing in `document.xml` and the matching `document.xml.rels` entries. | Hand-authored from the ECMA-376 / ISO-29500 DrawingML schema by `generate_fixtures.py`. | CC0-1.0 (public domain dedication). |
| `scatter_real.docx` | One scatter chart with two series, explicit `c:xVal` / `c:yVal` `c:numLit` blocks and two `c:valAx` axes titled "X axis" / "Y axis". | Hand-authored from ECMA-376 by `generate_fixtures.py`. | CC0-1.0. |
| `column_real.docx` | One classic column chart (`c:barChart` with `c:cat` / `c:val`, i.e. the non-scatter path). | Hand-authored from ECMA-376 by `generate_fixtures.py`. | CC0-1.0. |
| `multi_smartart_chart.docx` | Two independent SmartArts and two independent charts in one document, used to prove that `rId`, `PartName`, `docPr` id and node `modelId` allocation do not collide. | Hand-authored from ECMA-376 by `generate_fixtures.py`. | CC0-1.0. |
| `template_markers.docx` | Empty body containing `___REMO_SMARTART_MARKER_*___` / `___REMO_CHART_MARKER_*___` placeholders, used to drive the enricher. | Hand-authored from ECMA-376 by `generate_fixtures.py`. | CC0-1.0. |
| `legacy_real.doc` | Genuine legacy Word 97–2003 binary document (OLE2 Compound File Binary, detected by `file(1)` as "Composite Document File V2 Document"). | A small DOCX was hand-authored, then converted with **LibreOffice 7.4** (`soffice --headless --convert-to "doc:MS Word 97"`). LibreOffice is MPL-2.0; the converted output is a plain document and is dedicated here to CC0-1.0. | CC0-1.0. |

## Regenerating

```bash
python3 tools/fixtures/generate_fixtures.py
```

The `.doc` fixture is the only one that requires an external tool; it is
checked in so the test suite does not depend on LibreOffice being
installed. If you regenerate it, keep the same command so the file stays a
real OLE2 document rather than a renamed DOCX.

## Why this matters

A test that exports with `DocxConverter` and then imports with the same
`DocxConverter` can pass even when both sides share the same bug. These
fixtures are authored from the specification (and, for the `.doc`, from a
different producer entirely), so a passing import proves that the parser
understands third-party-shaped OOXML rather than only its own output.
