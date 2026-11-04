# Document extraction: PDF / scan / photo -> JSON

document-service can turn an uploaded driving licence (or any document) into structured fields. One pipeline serves
every file format; the format only decides how the text is obtained.

```
bytes ──ContentKindDetector──► PDF ──text layer?──yes──► PDFBox PDFTextStripper ─┐
       (magic numbers, not     │                 no───► render 300 dpi ──► OCR ──┤
        the file name)         IMAGE ─────────────────────────────────► OCR ──┼──► ExtractedText (pages, source, ocr_confidence)
                               TEXT ──────────────────────────────────────────┘            │
                                                                                            ▼
                                                              FieldExtractor for the DocumentType (DRIVING_LICENSE | OTHER)
                                                                                            │
                                                                                            ▼
                                                   ExtractionResponse { message, messageType S|E, response { ...fields } }
```

## Endpoints

| Method | Path | Who | Notes |
|---|---|---|---|
| POST | `/api/documents/extract?documentType=DRIVING_LICENSE[&scenario=]` (multipart `file`) | any authenticated user | nothing is stored; preview / prefill |
| POST | `/api/documents/{id}/extract?documentType=[&scenario=]` | owner, ADMIN, AGENT, CLAIMS_HANDLER, SERVICE | reads the stored bytes; `documentType` overrides the stored type |

Both return HTTP 200 with `messageType: "S"`, or HTTP 422 with `messageType: "E"` and an `errorCode`
(`UNSUPPORTED_CONTENT`, `PDF_ENCRYPTED`, `PDF_UNREADABLE`, `IMAGE_UNREADABLE`, `OCR_UNAVAILABLE`, `OCR_FAILED`,
`NO_TEXT`). Content-type / size violations are the platform's normal 400; a file over the multipart limit is a 413
`PAYLOAD_TOO_LARGE` (`config/MultipartErrorHandler`). The success body always carries `source` (`TEXT_LAYER`, `OCR`
or `MOCK`) and `simulated` (`true` only from the mock provider, see below). `scenario` is ignored by the LOCAL provider.

```json
{
  "message": "success",
  "messageType": "S",
  "response": {
    "license_authority_id": "SHTR24716",
    "license_number": "0194428",
    "license_issue_date": "18/09/1995",
    "license_expire_date": "07/01/2031",
    "date_of_birth": "01/01/1962",
    "issue_place_english": "Sharjah",
    "issue_place_arabic": "الشارقة",
    "name_english": "ANWAR UL HAQ FAZAL HAQ FAZAL HAQ",
    "name_arabic": "انور الحق فضل الحق فضل الحق",
    "nationality_english": "PAKISTAN",
    "nationality_arabic": "باكستان",
    "traffic_code": "3070265345",
    "permitted_vehicle": { "english": ["Light Vehicle"], "arabic": ["مركبة خفيفة"] },
    "transmission_type": null,
    "document_type": "DRIVING_LICENSE",
    "document_id": 17,
    "source": "OCR",
    "pages": 1,
    "ocr_confidence": 0.91,
    "field_confidence": { "license_number": 0.91, "name_english": 0.91, "...": 0.0 },
    "warnings": []
  }
}
```

## Stage 1: text (`extraction/source`)

* `ContentKindDetector` sniffs `%PDF`, PNG, JPEG, GIF, BMP, TIFF magic numbers. A phone-scanner "PDF" that is only
  a picture is still a PDF here; the text-layer check below sends it to OCR.
* `TextSourceRouter`
  * text file: used as is;
  * PDF: `PDFTextStripper` per page. If the whole document has fewer than `document.extraction.min-text-layer-chars`
    (40) characters it is a scan: every page is rendered with `PDFRenderer` at `render-dpi` (300) and OCR'd;
  * image: decoded with ImageIO and OCR'd.
* `OcrEngine` is a port. `TesseractOcrEngine` (Tess4J 5 / Tesseract 5) is the self-hosted adapter: `eng+ara`
  models, page-segmentation mode 6 (single uniform block, right for card layouts), a second pass for per-word
  confidences when `document.extraction.ocr.confidence=true`. It probes the tessdata folder at start-up and reports
  one clear reason when it cannot run; the pipeline then answers `OCR_UNAVAILABLE` for scans while digital PDFs keep
  working. A cloud adapter (Azure Document Intelligence, Textract, a vision LLM) implements the same two methods.

## Stage 2: fields (`extraction/field`)

`FieldExtractor` has one bean per `DocumentType`; `ExtractionService` picks it by type and falls back to
`GenericTextExtractor` (key/value lines, dates, long numbers, raw text).

`DrivingLicenseExtractor` (UAE card, bilingual):

| field | how | confidence |
|---|---|---|
| `license_number`, `traffic_code`, `license_authority_id` | label-anchored regex (`License No.`, `Traffic Code No.`, `Authority`) | 0.95 |
| same, no label readable | shape fallback: any 10-digit number / `AAAA99999` | 0.6 |
| `license_issue_date`, `license_expire_date`, `date_of_birth` | labelled dd/MM/yyyy, strict calendar parse (31/02 is rejected) | 0.95 |
| same, no labels at all | the three dates on a card are always DOB < issue < expiry | 0.6 + warning |
| `name_english` / `name_arabic` | `Name:` line; Arabic from the same line or the nearest mostly-Arabic line; vehicle class words and short class codes (`Light Vehicle R EPM`) stripped | 0.95 |
| `nationality_*`, `issue_place_*` | labelled; Arabic from the line, else a built-in English->Arabic table for emirates and common nationalities | 0.95 / 0.6 |
| `permitted_vehicle` | known class list (Light Vehicle, Motorcycle, Heavy Truck, ...) with Arabic equivalents | 0.95 if the `Permitted vehicles` label is present |

Validation never throws: it adds `warnings` (required field missing, expiry not after issue, DOB less than 16 years
before issue, licence already expired, traffic code not 10 digits) and lowers the field's confidence. When OCR was
used every field confidence is multiplied by `0.5 + 0.5 * ocr_confidence`, so a weak scan shows up as weak fields.

## Configuration (`config/document-service.yml`)

```yaml
document:
  extraction:
    min-text-layer-chars: 40
    ocr:
      enabled: ${OCR_ENABLED:true}
      tessdata-path: ${TESSDATA_PATH:C:/Softwares/tessdata}   # eng.traineddata, ara.traineddata, osd.traineddata
      languages: eng+ara
      render-dpi: 300
      confidence: true
```

`scripts\tessdata-download.cmd` fetches the three models (tessdata_fast, ~16 MB) into `TESSDATA_PATH`. The Tesseract
and Leptonica native libraries ship inside the Tess4J / Lept4J jars (Windows x64 and Linux x64), nothing else to
install. In Docker the models must be copied into the image or mounted and `TESSDATA_PATH` pointed at them.

## Providers: LOCAL vs MOCK (`extraction/provider`)

`ExtractionService` validates the upload and hands the bytes to the one active `DocumentRecognitionProvider`:

| `document.extraction.provider` | bean | what it does |
|---|---|---|
| `LOCAL` (default) | `LocalRecognitionProvider` | the two stages above: PDFBox text layer or Tesseract, then the field extractors. Reads what is in the file. |
| `MOCK` | `MockRecognitionProvider` | checks the bytes are a PDF (magic number), then returns a **fixed fixture** for the document type (`MockFixtures`). |

Neither provider calls anything outside the JVM: there is no government or third-party OCR endpoint in this project.
The reference application posts the file to an external OCR server (`multipart templatename + file`, Basic auth) and
reads `response.license_number`, `response.license_issue_date`, `response.traffic_plate_number`, `response.emirate_id`
and so on; the fixtures use the same keys so a consumer built against the mock keeps working on LOCAL.

**The mock is not document recognition.** Any PDF gives the same values for the same scenario. The envelope says so:
`source: "MOCK"`, `simulated: true` and a warning. Real reading of an arbitrary PDF needs the LOCAL provider (digital
PDFs through the text layer, scans and photos through Tesseract); a deterministic mock cannot do that and does not claim to.

```yaml
document:
  extraction:
    provider: ${DOCUMENT_EXTRACTION_PROVIDER:LOCAL}        # LOCAL | MOCK
    mock:
      default-scenario: ${DOCUMENT_EXTRACTION_MOCK_SCENARIO:SUCCESS}
      allow-scenario-param: true                          # false => ?scenario= is ignored
```

Scenarios (`?scenario=`, case-insensitive, only honoured by MOCK):

| scenario | result |
|---|---|
| `SUCCESS` | every fixture field present, confidence 1.0 |
| `MISSING_FIELDS` | required fields null (licence: `license_issue_date`, `traffic_code`, hence no `driving_experience_years`) plus "Required field missing" warnings |
| `PROCESSING_FAILURE` | HTTP 422, `messageType: "E"`, `errorCode: "OCR_FAILED"` |

Invalid input is handled before the provider, exactly as for LOCAL: empty file, size over `document.max-size-bytes`
and a disallowed content type are 400 `VALIDATION_ERROR`; a non-PDF given to the mock is also a 400; an unknown
scenario is a 400; over the multipart limit is 413; no token is 401.

```bash
# run with the mock provider (nothing else changes)
set DOCUMENT_EXTRACTION_PROVIDER=MOCK
# through the gateway
curl -X POST "http://localhost:8080/api/documents/extract?documentType=DRIVING_LICENSE&scenario=SUCCESS" ^
     -H "Authorization: Bearer <jwt>" -F "file=@licence.pdf;type=application/pdf"
```

```json
{
  "message": "success",
  "messageType": "S",
  "response": {
    "license_authority_id": "MOCK00001",
    "license_number": "0194428",
    "name_english": "SAMPLE HOLDER NAME",
    "nationality_english": "SAMPLE",
    "date_of_birth": "01/01/1990",
    "license_issue_date": "18/09/2015",
    "license_expire_date": "17/09/2030",
    "issue_place_english": "Sharjah",
    "issue_place_arabic": "الشارقة",
    "traffic_code": "3070265345",
    "driving_experience_years": 11,
    "permitted_vehicle": { "english": ["Light Vehicle"], "arabic": ["مركبة خفيفة"] },
    "transmission_type": "AUTOMATIC",
    "document_type": "DRIVING_LICENSE",
    "source": "MOCK",
    "simulated": true,
    "pages": 1,
    "field_confidence": { "license_number": 1.0, "license_issue_date": 1.0, "driving_experience_years": 1.0 },
    "warnings": ["Simulated response: fixed sample values from the mock provider, not read from the uploaded file"]
  }
}
```

`driving_experience_years` (whole years from `license_issue_date` to today, the motor quote's "Driving Experience") is
derived by `DrivingLicenseExtractor` for LOCAL and by the fixture for MOCK; both use the injected `Clock`
(`config/ExtractionConfig`) so tests can pin the date. Fixture values are obviously synthetic; the mock never logs
file content, only sizes and ids.

## Tests

| test | covers |
|---|---|
| `DrivingLicenseExtractorTest` | pure text -> fields: the sample card, garbled labels (chronological dates, emirate lookup), impossible dates, class stripping |
| `ExtractionControllerIT` | HTTP: a real PDF with a text layer built with PDFBox, a stored document with the owner/staff/other rules, a text file, the E envelope and the 400/401 paths |
| `OcrExtractionIT` | the real Tesseract stack on a Java2D-rendered card as PNG and as an image-only PDF; skips itself when `tessdata` is absent |
| `MockRecognitionProviderTest` | the mock without Spring: determinism, the three scenarios, PDF-only check, unknown scenario, every type has a fixture |
| `MockExtractionControllerIT` | HTTP with `document.extraction.provider=MOCK`: success, missing fields, processing failure, empty / non-PDF / oversize / bad scenario 400s, 401, stored-document ownership |
| `MultipartErrorHandlerTest` | `MaxUploadSizeExceededException` -> 413 in the platform error body |

## Known limits / next steps

* Accuracy on real phone photos depends on the picture: glare, skew and low resolution hurt Tesseract far more than
  they hurt a cloud OCR or a vision LLM. The `OcrEngine` port exists so one of those can be dropped in when needed.
* Arabic names come straight from OCR; the English->Arabic tables only cover emirates, nationalities and vehicle
  classes.
* Only the licence front has a dedicated extractor. Emirates ID, vehicle registration (Mulkiya) and previous policy
  copies go through `GenericTextExtractor` until their own `FieldExtractor` is added.
* Results are not persisted. If the proposal flow should remember what was extracted, add an `extracted_json`
  column on `documents` and store the `response` block.
