package com.insurance.customer.service;

import com.insurance.common.exception.ValidationException;
import com.insurance.customer.config.CustomerProperties;
import com.insurance.customer.dto.VehicleImportResult;
import com.insurance.customer.dto.VehicleImportRowError;
import com.insurance.customer.dto.VehicleRequest;
import com.insurance.customer.dto.VehicleResponse;
import com.insurance.customer.entity.FuelType;
import com.insurance.customer.entity.VehicleType;
import com.insurance.customer.repository.VehicleRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Bulk vehicle registration from a spreadsheet (.xlsx, .xls or .csv), the platform's version of the retail
 * fleet upload.
 *
 * <p>Same rules as the single-vehicle form, applied to every row: Bean Validation on {@link VehicleRequest},
 * then {@link VehicleService#validateBusinessRules}, then uniqueness of the registration number both inside
 * the file and against the database. Columns are matched by header name, not position, so the customer may
 * reorder or drop optional columns. The file is all-or-nothing: with one bad row nothing is saved and every
 * problem is reported at once, so one upload round-trip fixes everything.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleImportService {

    /** Header names of the template, in template order. Matching ignores case, spaces, underscores and hyphens. */
    static final List<String> COLUMNS = List.of(
            "registrationNumber", "vehicleType", "make", "model", "variant", "fuelType",
            "manufacturingYear", "engineCapacityCc", "chassisNumber", "registrationDate", "currentValue");
    static final Set<String> REQUIRED = Set.of(
            "registrationNumber", "vehicleType", "make", "model", "fuelType",
            "manufacturingYear", "engineCapacityCc", "registrationDate", "currentValue");

    public static final String TEMPLATE_FILE_NAME = "vehicle-import-template.xlsx";
    public static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private final VehicleService vehicleService;
    private final VehicleRepository vehicleRepository;
    private final CustomerProperties properties;
    private final Validator validator;

    /**
     * Validates every row and, unless {@code dryRun} or any row is invalid, saves them all in one transaction.
     * Throws {@link ValidationException} (400) only for problems with the file itself: wrong type, no header,
     * unknown required column, too many rows. Row problems come back inside the result.
     */
    @Transactional
    public VehicleImportResult importMine(MultipartFile file, boolean dryRun) {
        List<ParsedRow> rows = parse(file);
        if (rows.size() > properties.getVehicle().getImportMaxRows()) {
            throw new ValidationException("The file has " + rows.size() + " vehicles; at most "
                    + properties.getVehicle().getImportMaxRows() + " can be imported at once");
        }
        List<VehicleImportRowError> errors = new ArrayList<>();
        List<VehicleRequest> requests = new ArrayList<>();
        Map<String, Integer> seenRegistrations = new HashMap<>();
        for (ParsedRow row : rows) {
            VehicleRequest request = toRequest(row, errors);
            if (request == null) {
                continue;
            }
            for (ConstraintViolation<VehicleRequest> violation : validator.validate(request)) {
                errors.add(new VehicleImportRowError(row.number(), violation.getPropertyPath().toString(), violation.getMessage()));
            }
            try {
                vehicleService.validateBusinessRules(request);
            } catch (ValidationException e) {
                errors.add(new VehicleImportRowError(row.number(), null, e.getMessage()));
            }
            String registration = VehicleService.normalise(request.registrationNumber());
            Integer firstRow = seenRegistrations.putIfAbsent(registration, row.number());
            if (firstRow != null) {
                errors.add(new VehicleImportRowError(row.number(), "registrationNumber",
                        "Duplicate of row " + firstRow + " (" + registration + ")"));
            } else if (vehicleRepository.existsByRegistrationNumber(registration)) {
                errors.add(new VehicleImportRowError(row.number(), "registrationNumber",
                        "Vehicle " + registration + " is already registered"));
            }
            requests.add(request);
        }
        if (!errors.isEmpty()) {
            errors.sort(Comparator.comparingInt(VehicleImportRowError::row)
                    .thenComparing(VehicleImportRowError::column, Comparator.nullsLast(Comparator.naturalOrder())));
            log.info("Vehicle import rejected: {} rows, {} errors", rows.size(), errors.size());
            return VehicleImportResult.rejected(dryRun, rows.size(), errors);
        }
        if (dryRun) {
            return VehicleImportResult.valid(rows.size());
        }
        List<VehicleResponse> saved = new ArrayList<>(requests.size());
        for (VehicleRequest request : requests) {
            saved.add(vehicleService.addToMine(request));
        }
        log.info("Vehicle import saved {} vehicles", saved.size());
        return VehicleImportResult.imported(saved);
    }

    /** An .xlsx with the header row, one example row and a column-width that fits the headers. */
    public byte[] template() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Vehicles");
            Font bold = workbook.createFont();
            bold.setBold(true);
            CellStyle header = workbook.createCellStyle();
            header.setFont(bold);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < COLUMNS.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(COLUMNS.get(i));
                cell.setCellStyle(header);
                sheet.setColumnWidth(i, 20 * 256);
            }
            String[] example = {"MH12AB1234", "CAR", "Honda", "City", "VX", "PETROL",
                    String.valueOf(Year.now().getValue() - 2), "1498", "", LocalDate.now().minusYears(2).format(ISO), "650000"};
            Row exampleRow = sheet.createRow(1);
            for (int i = 0; i < example.length; i++) {
                exampleRow.createCell(i).setCellValue(example[i]);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the vehicle import template", e);
        }
    }

    // ---- parsing -------------------------------------------------------------------------------------------

    /** @param number 1-based spreadsheet row number; @param cells header -> trimmed text, absent when blank */
    record ParsedRow(int number, Map<String, String> cells) {
    }

    List<ParsedRow> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Upload a non-empty .xlsx, .xls or .csv file");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try (InputStream in = file.getInputStream()) {
            if (name.endsWith(".csv")) {
                return parseCsv(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                try (Workbook workbook = WorkbookFactory.create(in)) {
                    return parseSheet(workbook.getSheetAt(0));
                }
            }
            throw new ValidationException("Unsupported file type; upload .xlsx, .xls or .csv");
        } catch (IOException e) {
            throw new ValidationException("The file could not be read: " + e.getMessage());
        } catch (ValidationException e) {
            throw e;
        } catch (RuntimeException e) {
            // POI throws its own unchecked types for corrupt or encrypted workbooks
            throw new ValidationException("The file is not a readable spreadsheet: " + e.getMessage());
        }
    }

    private List<ParsedRow> parseSheet(Sheet sheet) {
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        Row headerRow = sheet.getRow(sheet.getFirstRowNum());
        if (headerRow == null) {
            throw new ValidationException("The first sheet is empty");
        }
        Map<Integer, String> columnByIndex = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String column = matchColumn(formatter.formatCellValue(cell));
            if (column != null) {
                columnByIndex.put(cell.getColumnIndex(), column);
            }
        }
        assertRequiredColumns(columnByIndex.values());
        List<ParsedRow> rows = new ArrayList<>();
        for (int r = headerRow.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            Map<String, String> cells = new LinkedHashMap<>();
            for (Map.Entry<Integer, String> e : columnByIndex.entrySet()) {
                String text = cellText(row.getCell(e.getKey()), formatter);
                if (!text.isEmpty()) {
                    cells.put(e.getValue(), text);
                }
            }
            if (!cells.isEmpty()) {
                rows.add(new ParsedRow(r + 1, cells));
            }
        }
        return rows;
    }

    /** Dates typed as real Excel dates come out as ISO text; everything else as the text Excel would display. */
    private static String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().format(ISO);
        }
        if (type == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            return value == Math.rint(value) ? String.valueOf((long) value) : BigDecimal.valueOf(value).toPlainString();
        }
        return formatter.formatCellValue(cell).strip();
    }

    private List<ParsedRow> parseCsv(String content) {
        List<List<String>> lines = new ArrayList<>();
        for (String line : content.split("\\R")) {
            if (!line.isBlank()) {
                lines.add(splitCsvLine(line));
            }
        }
        if (lines.isEmpty()) {
            throw new ValidationException("The file is empty");
        }
        Map<Integer, String> columnByIndex = new LinkedHashMap<>();
        List<String> headers = lines.get(0);
        for (int i = 0; i < headers.size(); i++) {
            String column = matchColumn(headers.get(i).replace("﻿", ""));
            if (column != null) {
                columnByIndex.put(i, column);
            }
        }
        assertRequiredColumns(columnByIndex.values());
        List<ParsedRow> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> values = lines.get(i);
            Map<String, String> cells = new LinkedHashMap<>();
            for (Map.Entry<Integer, String> e : columnByIndex.entrySet()) {
                if (e.getKey() < values.size() && !values.get(e.getKey()).isBlank()) {
                    cells.put(e.getValue(), values.get(e.getKey()).strip());
                }
            }
            if (!cells.isEmpty()) {
                rows.add(new ParsedRow(i + 1, cells));
            }
        }
        return rows;
    }

    /** Minimal RFC 4180: commas inside double quotes are kept, doubled quotes are one quote. */
    static List<String> splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        out.add(current.toString());
        return out;
    }

    /** "Registration Number", "registration_number", "REGISTRATIONNUMBER" all map to registrationNumber. */
    static String matchColumn(String header) {
        if (header == null) {
            return null;
        }
        String key = header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        for (String column : COLUMNS) {
            if (column.toLowerCase(Locale.ROOT).equals(key)) {
                return column;
            }
        }
        return switch (key) {
            case "registration", "regno", "registrationno", "plateno", "platenumber" -> "registrationNumber";
            case "type" -> "vehicleType";
            case "fuel" -> "fuelType";
            case "year", "mfgyear", "manufacturingyr" -> "manufacturingYear";
            case "enginecapacity", "cc", "enginecc" -> "engineCapacityCc";
            case "chassis", "chassisno" -> "chassisNumber";
            case "regdate", "registeredon" -> "registrationDate";
            case "idv", "value", "insuredvalue", "insureddeclaredvalue" -> "currentValue";
            default -> null;
        };
    }

    private static void assertRequiredColumns(Iterable<String> present) {
        Set<String> found = new HashSet<>();
        present.forEach(found::add);
        List<String> missing = REQUIRED.stream().filter(c -> !found.contains(c)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new ValidationException("The header row is missing the column(s): " + String.join(", ", missing)
                    + ". Download the template to see the expected layout.");
        }
    }

    // ---- row -> request ------------------------------------------------------------------------------------

    /** Converts cell text to the request; type errors are reported per cell and make the row unusable (null). */
    private VehicleRequest toRequest(ParsedRow row, List<VehicleImportRowError> errors) {
        int before = errors.size();
        Map<String, String> c = row.cells();
        VehicleType vehicleType = parseEnum(row, c, "vehicleType", VehicleType.class, errors);
        FuelType fuelType = parseEnum(row, c, "fuelType", FuelType.class, errors);
        Integer year = parseNumber(row, c, "manufacturingYear", Integer::valueOf, "a whole number such as 2021", errors);
        Integer cc = parseNumber(row, c, "engineCapacityCc", Integer::valueOf, "a whole number such as 1498", errors);
        BigDecimal value = parseNumber(row, c, "currentValue", s -> new BigDecimal(s.replace(",", "")), "an amount such as 650000", errors);
        LocalDate registrationDate = parseDate(row, c, "registrationDate", errors);
        if (errors.size() > before) {
            return null;
        }
        return new VehicleRequest(c.getOrDefault("registrationNumber", ""), vehicleType, c.getOrDefault("make", ""),
                c.getOrDefault("model", ""), c.get("variant"), fuelType, year, cc, c.get("chassisNumber"),
                registrationDate, value);
    }

    private static <E extends Enum<E>> E parseEnum(ParsedRow row, Map<String, String> cells, String column, Class<E> type,
                                                 List<VehicleImportRowError> errors) {
        String text = cells.get(column);
        if (text == null) {
            return null;    // @NotNull reports it with the form's own message
        }
        String key = text.strip().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(key)) {
                return constant;
            }
        }
        errors.add(new VehicleImportRowError(row.number(), column, "Must be one of "
                + Arrays.toString(type.getEnumConstants()) + " (was '" + text + "')"));
        return null;
    }

    private static <N> N parseNumber(ParsedRow row, Map<String, String> cells, String column, Function<String, N> parser,
                                     String expected, List<VehicleImportRowError> errors) {
        String text = cells.get(column);
        if (text == null) {
            return null;
        }
        try {
            return parser.apply(text.strip());
        } catch (NumberFormatException e) {
            errors.add(new VehicleImportRowError(row.number(), column, "Must be " + expected + " (was '" + text + "')"));
            return null;
        }
    }

    private static LocalDate parseDate(ParsedRow row, Map<String, String> cells, String column, List<VehicleImportRowError> errors) {
        String text = cells.get(column);
        if (text == null) {
            return null;
        }
        for (DateTimeFormatter format : List.of(ISO, DMY)) {
            try {
                return LocalDate.parse(text.strip(), format);
            } catch (DateTimeParseException ignored) {
                // try the next layout
            }
        }
        errors.add(new VehicleImportRowError(row.number(), column, "Must be a date like 2021-06-01 or 01/06/2021 (was '" + text + "')"));
        return null;
    }
}
