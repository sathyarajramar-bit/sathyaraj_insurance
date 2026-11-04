package com.insurance.document.extraction;

import com.insurance.document.entity.DocumentType;
import com.insurance.document.extraction.dto.ExtractionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Turns a PDF, scan or photo into structured JSON. One endpoint pair for every file format: the format is detected
 * from the bytes, so clients never choose between "pdf" and "image" routes.
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Document extraction", description = "PDF / scan / photo -> fields (driving licence, generic)")
public class ExtractionController {

    private final ExtractionService extractionService;

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Extract fields from an uploaded file without storing it (?documentType=DRIVING_LICENSE). "
            + "scenario (SUCCESS | MISSING_FIELDS | PROCESSING_FAILURE) is only honoured by the MOCK provider.")
    public ExtractionResponse extractUpload(@RequestPart("file") MultipartFile file,
                                            @RequestParam(defaultValue = "OTHER") DocumentType documentType,
                                            @RequestParam(required = false) String scenario) throws IOException {
        return extractionService.extractUpload(file.getBytes(), file.getContentType(), documentType, scenario);
    }

    @PostMapping("/{id}/extract")
    @Operation(summary = "Extract fields from a stored document (owner, staff, SERVICE); documentType overrides the stored type")
    public ExtractionResponse extractStored(@PathVariable Long id, @RequestParam(required = false) DocumentType documentType,
                                            @RequestParam(required = false) String scenario) {
        return extractionService.extractStored(id, documentType, scenario);
    }

    /** Extraction failures use the S/E envelope the callers branch on, not the platform's generic error body. */
    @ExceptionHandler(ExtractionException.class)
    public ResponseEntity<ExtractionResponse> handleExtraction(ExtractionException ex) {
        log.warn("Extraction failed [{}]: {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ExtractionResponse.error(ex.getCode(), ex.getMessage()));
    }
}
