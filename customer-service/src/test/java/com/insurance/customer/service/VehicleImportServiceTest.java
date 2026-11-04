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
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleImportServiceTest {

    private static final int YEAR = Year.now().getValue() - 2;
    private static final String[] HEADER = {"registrationNumber", "vehicleType", "make", "model", "variant", "fuelType",
            "manufacturingYear", "engineCapacityCc", "chassisNumber", "registrationDate", "currentValue"};

    @Mock private VehicleService vehicleService;
    @Mock private VehicleRepository vehicleRepository;

    private final CustomerProperties properties = new CustomerProperties();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private VehicleImportService service;

    @BeforeEach
    void setUp() {
        properties.getVehicle().setImportMaxRows(10);
        service = new VehicleImportService(vehicleService, vehicleRepository, properties, validator);
    }

    @Test
    void importsEveryRowWhenTheWholeFileIsClean() throws IOException {
        when(vehicleRepository.existsByRegistrationNumber(any())).thenReturn(false);
        when(vehicleService.addToMine(any())).thenAnswer(inv -> response(inv.getArgument(0)));

        VehicleImportResult result = service.importMine(xlsx(
                row("MH12AB1234", "CAR", "Honda", "City", "VX", "PETROL", YEAR, 1498, "", YEAR + "-06-01", "650000"),
                row("ka-01-zz-9999", "bike", "Hero", "Splendor", "", "Petrol", YEAR, 97, "", "01/03/" + YEAR, "55,000")), false);

        assertThat(result.status()).isEqualTo(VehicleImportResult.Status.IMPORTED);
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.importedCount()).isEqualTo(2);
        assertThat(result.errors()).isEmpty();
        ArgumentCaptor<VehicleRequest> saved = ArgumentCaptor.forClass(VehicleRequest.class);
        verify(vehicleService, org.mockito.Mockito.times(2)).addToMine(saved.capture());
        VehicleRequest bike = saved.getAllValues().get(1);
        assertThat(bike.vehicleType()).isEqualTo(VehicleType.BIKE);
        assertThat(bike.fuelType()).isEqualTo(FuelType.PETROL);
        assertThat(bike.registrationDate()).isEqualTo(LocalDate.of(YEAR, 3, 1));
        assertThat(bike.currentValue()).isEqualByComparingTo("55000");
        assertThat(bike.variant()).isNull();
    }

    @Test
    void rejectsTheWholeFileAndReportsEveryRowProblem() throws IOException {
        when(vehicleRepository.existsByRegistrationNumber("MH12AB1234")).thenReturn(true);
        when(vehicleRepository.existsByRegistrationNumber("KA01ZZ9999")).thenReturn(false);

        VehicleImportResult result = service.importMine(xlsx(
                row("MH12AB1234", "CAR", "Honda", "City", "", "PETROL", YEAR, 1498, "", YEAR + "-06-01", "650000"),   // exists in DB
                row("12345", "TRUCK", "Tata", "", "", "PETROL", "abc", 1498, "", "yesterday", "650000"),            // many problems
                row("KA01ZZ9999", "CAR", "Honda", "City", "", "DIESEL", YEAR, 1498, "", YEAR + "-06-01", "650000"),
                row("ka 01 zz 9999", "CAR", "Honda", "City", "", "DIESEL", YEAR, 1498, "", YEAR + "-06-01", "650000")), false);  // duplicate of row 4

        assertThat(result.status()).isEqualTo(VehicleImportResult.Status.REJECTED);
        assertThat(result.totalRows()).isEqualTo(4);
        assertThat(result.importedCount()).isZero();
        verify(vehicleService, never()).addToMine(any());

        List<VehicleImportRowError> errors = result.errors();
        assertThat(errors).extracting(VehicleImportRowError::row).containsOnly(2, 3, 5);
        assertThat(errors).filteredOn(e -> e.row() == 2).extracting(VehicleImportRowError::message)
                .containsExactly("Vehicle MH12AB1234 is already registered");
        assertThat(errors).filteredOn(e -> e.row() == 3).extracting(VehicleImportRowError::column)
                .containsExactlyInAnyOrder("vehicleType", "manufacturingYear", "registrationDate");
        assertThat(errors).filteredOn(e -> e.row() == 5).extracting(VehicleImportRowError::message)
                .containsExactly("Duplicate of row 4 (KA01ZZ9999)");
    }

    @Test
    void beanValidationAndBusinessRulesApplyPerRow() throws IOException {
        when(vehicleRepository.existsByRegistrationNumber(any())).thenReturn(false);
        org.mockito.Mockito.doThrow(new ValidationException("Vehicles older than 20 years are not eligible for insurance"))
                .when(vehicleService).validateBusinessRules(any());

        VehicleImportResult result = service.importMine(xlsx(
                row("12345", "CAR", "Honda", "City", "", "PETROL", 1970, 1498, "", "1970-06-01", "650000")), false);

        assertThat(result.status()).isEqualTo(VehicleImportResult.Status.REJECTED);
        assertThat(result.errors()).extracting(VehicleImportRowError::column, VehicleImportRowError::message)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("registrationNumber", "Registration number must look like MH12AB1234 (spaces or hyphens allowed)"),
                        org.assertj.core.groups.Tuple.tuple("manufacturingYear", "Manufacturing year is too old"),
                        org.assertj.core.groups.Tuple.tuple(null, "Vehicles older than 20 years are not eligible for insurance"));
    }

    @Test
    void dryRunValidatesWithoutSaving() throws IOException {
        when(vehicleRepository.existsByRegistrationNumber(any())).thenReturn(false);

        VehicleImportResult result = service.importMine(xlsx(
                row("MH12AB1234", "CAR", "Honda", "City", "", "PETROL", YEAR, 1498, "", YEAR + "-06-01", "650000")), true);

        assertThat(result.status()).isEqualTo(VehicleImportResult.Status.VALID);
        assertThat(result.dryRun()).isTrue();
        assertThat(result.totalRows()).isEqualTo(1);
        verify(vehicleService, never()).addToMine(any());
    }

    @Test
    void csvWithReorderedHumanHeadersAndQuotedCommasIsAccepted() {
        when(vehicleRepository.existsByRegistrationNumber(any())).thenReturn(false);
        when(vehicleService.addToMine(any())).thenAnswer(inv -> response(inv.getArgument(0)));
        String csv = """
                Make,Model,Registration Number,Vehicle Type,Fuel Type,Manufacturing Year,Engine Capacity CC,Registration Date,IDV
                Honda,"City, ZX",MH12AB1234,CAR,PETROL,%d,1498,%d-06-01,"6,50,000"
                """.formatted(YEAR, YEAR);

        VehicleImportResult result = service.importMine(
                new MockMultipartFile("file", "cars.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)), false);

        assertThat(result.status()).isEqualTo(VehicleImportResult.Status.IMPORTED);
        ArgumentCaptor<VehicleRequest> saved = ArgumentCaptor.forClass(VehicleRequest.class);
        verify(vehicleService).addToMine(saved.capture());
        assertThat(saved.getValue().model()).isEqualTo("City, ZX");
        assertThat(saved.getValue().currentValue()).isEqualByComparingTo("650000");
    }

    @Test
    void fileLevelProblemsAreBadRequests() throws IOException {
        assertThatThrownBy(() -> service.importMine(new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[]{1}), false))
                .isInstanceOf(ValidationException.class).hasMessageContaining("Unsupported file type");
        assertThatThrownBy(() -> service.importMine(new MockMultipartFile("file", "x.xlsx", null, new byte[0]), false))
                .isInstanceOf(ValidationException.class).hasMessageContaining("non-empty");

        byte[] noRegistration = workbook(new String[]{"make", "model"}, new Object[]{"Honda", "City"});
        assertThatThrownBy(() -> service.importMine(new MockMultipartFile("file", "x.xlsx", null, noRegistration), false))
                .isInstanceOf(ValidationException.class).hasMessageContaining("registrationNumber");

        properties.getVehicle().setImportMaxRows(1);
        assertThatThrownBy(() -> service.importMine(xlsx(
                row("MH12AB1234", "CAR", "Honda", "City", "", "PETROL", YEAR, 1498, "", YEAR + "-06-01", "650000"),
                row("MH12AB1235", "CAR", "Honda", "City", "", "PETROL", YEAR, 1498, "", YEAR + "-06-01", "650000")), false))
                .isInstanceOf(ValidationException.class).hasMessageContaining("at most 1");
    }

    @Test
    void realExcelDatesAndNumbersAreReadAsTyped() throws IOException {
        when(vehicleRepository.existsByRegistrationNumber(any())).thenReturn(false);
        when(vehicleService.addToMine(any())).thenAnswer(inv -> response(inv.getArgument(0)));
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADER.length; i++) {
                header.createCell(i).setCellValue(HEADER[i]);
            }
            Row r = sheet.createRow(1);
            r.createCell(0).setCellValue("MH12AB1234");
            r.createCell(1).setCellValue("CAR");
            r.createCell(2).setCellValue("Honda");
            r.createCell(3).setCellValue("City");
            r.createCell(5).setCellValue("PETROL");
            r.createCell(6).setCellValue((double) YEAR);          // numeric year
            r.createCell(7).setCellValue(1498.0);
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
            Cell date = r.createCell(9);
            date.setCellValue(LocalDate.of(YEAR, 6, 1));          // real Excel date
            date.setCellStyle(dateStyle);
            r.createCell(10).setCellValue(650000.5);
            wb.write(out);

            VehicleImportResult result = service.importMine(new MockMultipartFile("file", "v.xlsx", null, out.toByteArray()), false);

            assertThat(result.status()).isEqualTo(VehicleImportResult.Status.IMPORTED);
            ArgumentCaptor<VehicleRequest> saved = ArgumentCaptor.forClass(VehicleRequest.class);
            verify(vehicleService).addToMine(saved.capture());
            assertThat(saved.getValue().manufacturingYear()).isEqualTo(YEAR);
            assertThat(saved.getValue().engineCapacityCc()).isEqualTo(1498);
            assertThat(saved.getValue().registrationDate()).isEqualTo(LocalDate.of(YEAR, 6, 1));
            assertThat(saved.getValue().currentValue()).isEqualByComparingTo("650000.5");
        }
    }

    @Test
    void templateHasTheHeaderRowAndAnExample() throws IOException {
        byte[] bytes = service.template();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 0; i < HEADER.length; i++) {
                assertThat(sheet.getRow(0).getCell(i).getStringCellValue()).isEqualTo(HEADER[i]);
            }
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("MH12AB1234");
        }
        // the template itself round-trips through the importer
        when(vehicleRepository.existsByRegistrationNumber(any())).thenReturn(false);
        VehicleImportResult result = service.importMine(new MockMultipartFile("file", "t.xlsx", null, bytes), true);
        assertThat(result.status()).isEqualTo(VehicleImportResult.Status.VALID);
    }

    @Test
    void headerAliasesAndCsvSplitting() {
        assertThat(VehicleImportService.matchColumn("Registration Number")).isEqualTo("registrationNumber");
        assertThat(VehicleImportService.matchColumn("REG_NO")).isEqualTo("registrationNumber");
        assertThat(VehicleImportService.matchColumn("Engine capacity (cc)")).isEqualTo("engineCapacityCc");
        assertThat(VehicleImportService.matchColumn("colour")).isNull();
        assertThat(VehicleImportService.splitCsvLine("a,\"b,c\",\"say \"\"hi\"\"\",")).containsExactly("a", "b,c", "say \"hi\"", "");
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    private static Object[] row(Object... cells) {
        return cells;
    }

    private static MockMultipartFile xlsx(Object[]... rows) throws IOException {
        return new MockMultipartFile("file", "vehicles.xlsx", null, workbook(HEADER, rows));
    }

    private static byte[] workbook(String[] header, Object[]... rows) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            Row h = sheet.createRow(0);
            for (int i = 0; i < header.length; i++) {
                h.createCell(i).setCellValue(header[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(String.valueOf(rows[r][c]));
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static VehicleResponse response(VehicleRequest r) {
        return new VehicleResponse(1L, 7L, VehicleService.normalise(r.registrationNumber()), r.vehicleType(), r.make(), r.model(),
                r.variant(), r.fuelType(), r.manufacturingYear(), r.engineCapacityCc(), r.chassisNumber(), r.registrationDate(),
                r.currentValue() == null ? BigDecimal.ZERO : r.currentValue(), Instant.now(), Instant.now());
    }
}
