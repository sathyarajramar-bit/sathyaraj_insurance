package com.insurance.document.extraction.ocr;

import com.insurance.document.config.DocumentProperties;
import com.insurance.document.extraction.ExtractionException;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Tesseract 5 through Tess4J. Tess4J ships the Windows/Linux native libraries inside its jar; only the language
 * models ({@code <tessdata>/eng.traineddata}, {@code ara.traineddata}) have to be present on disk, see
 * {@code scripts/tessdata-download.cmd}.
 *
 * <p>Thread safety: a {@link Tesseract} instance is not thread safe, so one is created per call. Initialisation
 * (loading the language models) costs ~100 ms, negligible next to the recognition itself.
 */
@Slf4j
@Component
public class TesseractOcrEngine implements OcrEngine {

    private final DocumentProperties.Extraction.Ocr settings;
    private final String unavailableReason;

    public TesseractOcrEngine(DocumentProperties properties) {
        this.settings = properties.getExtraction().getOcr();
        this.unavailableReason = probe();
        if (unavailableReason == null) {
            log.info("Tesseract OCR ready: tessdata={} languages={}", settings.getTessdataPath(), settings.getLanguages());
        } else {
            log.warn("Tesseract OCR unavailable: {}", unavailableReason);
        }
    }

    @Override
    public boolean isAvailable() {
        return unavailableReason == null;
    }

    @Override
    public String unavailableReason() {
        return unavailableReason;
    }

    @Override
    public OcrResult recognize(BufferedImage image) {
        if (!isAvailable()) {
            throw new ExtractionException("OCR_UNAVAILABLE", unavailableReason);
        }
        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath(Paths.get(settings.getTessdataPath()).toAbsolutePath().toString());
        tesseract.setLanguage(settings.getLanguages());
        // PSM 6 "assume a single uniform block of text": best for ID cards and licences where the layout is a grid of
        // label/value pairs; PSM 3 (auto) tends to split them into unrelated columns.
        tesseract.setPageSegMode(ITessAPI.TessPageSegMode.PSM_SINGLE_BLOCK);
        tesseract.setVariable("preserve_interword_spaces", "1");
        try {
            String text = tesseract.doOCR(image);
            Double confidence = settings.isConfidence() ? meanConfidence(tesseract, image) : null;
            return new OcrResult(text, confidence);
        } catch (TesseractException e) {
            throw new ExtractionException("OCR_FAILED", "OCR failed: " + e.getMessage(), e);
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            throw new ExtractionException("OCR_UNAVAILABLE", "Tesseract native library could not be loaded: " + e.getMessage(), e);
        }
    }

    private static Double meanConfidence(ITesseract tesseract, BufferedImage image) {
        List<Word> words = tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD);
        if (words.isEmpty()) {
            return null;
        }
        double sum = 0;
        for (Word w : words) {
            sum += w.getConfidence();
        }
        return Math.round(sum / words.size()) / 100.0;
    }

    /** Checks configuration and language files up front so the failure is one clear message, not a native crash later. */
    private String probe() {
        if (!settings.isEnabled()) {
            return "OCR is disabled (document.extraction.ocr.enabled=false)";
        }
        Path dir = Paths.get(settings.getTessdataPath());
        if (!Files.isDirectory(dir)) {
            return "tessdata folder not found: " + dir.toAbsolutePath() + " (run scripts\\tessdata-download.cmd or set TESSDATA_PATH)";
        }
        for (String lang : settings.getLanguages().split("\\+")) {
            if (!Files.isRegularFile(dir.resolve(lang + ".traineddata"))) {
                return "language model missing: " + dir.resolve(lang + ".traineddata").toAbsolutePath();
            }
        }
        return null;
    }
}
