package com.insurance.document.extraction.field;

import com.insurance.document.entity.DocumentType;
import com.insurance.document.extraction.source.ExtractedText;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.insurance.document.extraction.field.TextPatterns.allDates;
import static com.insurance.document.extraction.field.TextPatterns.arabicPart;
import static com.insurance.document.extraction.field.TextPatterns.find;
import static com.insurance.document.extraction.field.TextPatterns.isMostlyArabic;
import static com.insurance.document.extraction.field.TextPatterns.labelled;
import static com.insurance.document.extraction.field.TextPatterns.latinPart;
import static com.insurance.document.extraction.field.TextPatterns.lines;
import static com.insurance.document.extraction.field.TextPatterns.normaliseDate;
import static com.insurance.document.extraction.field.TextPatterns.parseDate;

/**
 * UAE driving licence (front side). Bilingual card: every value has an English and an Arabic rendering, dates are
 * dd/MM/yyyy, the licence number is digits only and the traffic code is a 10-digit number.
 *
 * <p>Strategy: label-anchored regexes first (confidence 0.95), positional fallbacks second (0.6, e.g. "the three
 * dates on the card are DOB, issue, expiry in chronological order"), then validation that downgrades or flags
 * fields. Vehicle class words are stripped from the name because OCR and layout engines routinely glue the
 * "Permitted vehicles" line onto it.
 */
@Component
public class DrivingLicenseExtractor implements FieldExtractor {

    static final double LABELLED = 0.95;
    static final double INFERRED = 0.6;

    /** "Today" for the derived driving experience; injectable so tests can pin it. */
    private final Clock clock;

    public DrivingLicenseExtractor() {
        this(Clock.systemUTC());
    }

    @Autowired
    public DrivingLicenseExtractor(Clock clock) {
        this.clock = clock;
    }

    private static final Pattern LICENSE_NO = labelled("licen[cs]e\\s*(?:no|number|#)?", "\\d{4,12}");
    /** "Issue Date" / "Date of Issue" / "Issued", but not the "Issue" inside "Place of Issue". */
    private static final Pattern ISSUE_DATE = labelled("date\\s*of\\s*issue|(?<!of\\s)issue(?:d)?(?:\\s*date)?", "\\d{2}[/.-]\\d{2}[/.-]\\d{4}");
    private static final Pattern EXPIRY_DATE = labelled("(?:date\\s*of\\s*)?expir\\w*(?:\\s*date)?|exp\\.?\\s*date|valid\\s*(?:until|to)", "\\d{2}[/.-]\\d{2}[/.-]\\d{4}");
    private static final Pattern DOB = labelled("date\\s*of\\s*birth|birth\\s*date|d\\.?o\\.?b\\.?", "\\d{2}[/.-]\\d{2}[/.-]\\d{4}");
    private static final Pattern NATIONALITY = labelled("nationality", "[A-Za-z][A-Za-z .'-]{2,40}");
    private static final Pattern PLACE_OF_ISSUE = labelled("place\\s*of\\s*issue|issue\\s*place|issued\\s*(?:at|in)|issuing\\s*emirate", "[A-Za-z][A-Za-z ]{2,30}");
    private static final Pattern TRAFFIC_CODE = labelled("traffic\\s*(?:code|file)(?:\\s*(?:no|number))?", "\\d{8,12}");
    private static final Pattern AUTHORITY = labelled("(?:licen[cs]ing\\s*)?authority(?:\\s*id)?", "[A-Z]{2,6}\\d{3,8}");
    private static final Pattern AUTHORITY_SHAPE = Pattern.compile("\\b([A-Z]{4,5}\\d{5})\\b");
    private static final Pattern NAME = labelled("(?:full\\s*|holder\\s*)?name", "[A-Za-z][A-Za-z .'-]{2,120}");
    private static final Pattern PERMITTED = Pattern.compile("(?i)permitted\\s*vehicles?(?:\\s*(?:class(?:es)?|categor(?:y|ies)))?");
    private static final Pattern TEN_DIGITS = Pattern.compile("\\b(\\d{10})\\b");

    /** Vehicle classes as printed on UAE licences, English -> Arabic. Order matters: longer phrases first. */
    private static final Map<String, String> VEHICLE_CLASSES = new LinkedHashMap<>();
    /** Issuing emirates, English -> Arabic. */
    private static final Map<String, String> EMIRATES = new LinkedHashMap<>();
    private static final Map<String, String> NATIONALITIES_AR = Map.ofEntries(
            Map.entry("PAKISTAN", "باكستان"), Map.entry("INDIA", "الهند"), Map.entry("BANGLADESH", "بنغلاديش"),
            Map.entry("PHILIPPINES", "الفلبين"), Map.entry("EGYPT", "مصر"), Map.entry("UNITED ARAB EMIRATES", "الإمارات العربية المتحدة"),
            Map.entry("SRI LANKA", "سريلانكا"), Map.entry("NEPAL", "نيبال"), Map.entry("JORDAN", "الأردن"), Map.entry("SYRIA", "سوريا"),
            Map.entry("LEBANON", "لبنان"), Map.entry("SUDAN", "السودان"), Map.entry("UNITED KINGDOM", "المملكة المتحدة"),
            Map.entry("SAUDI ARABIA", "السعودية"), Map.entry("OMAN", "عمان"), Map.entry("YEMEN", "اليمن"), Map.entry("IRAN", "إيران"));

    static {
        VEHICLE_CLASSES.put("Light Mechanical Equipment", "معدات ميكانيكية خفيفة");
        VEHICLE_CLASSES.put("Heavy Mechanical Equipment", "معدات ميكانيكية ثقيلة");
        VEHICLE_CLASSES.put("Light Vehicle", "مركبة خفيفة");
        VEHICLE_CLASSES.put("Heavy Vehicle", "مركبة ثقيلة");
        VEHICLE_CLASSES.put("Light Truck", "شاحنة خفيفة");
        VEHICLE_CLASSES.put("Heavy Truck", "شاحنة ثقيلة");
        VEHICLE_CLASSES.put("Light Bus", "حافلة خفيفة");
        VEHICLE_CLASSES.put("Heavy Bus", "حافلة ثقيلة");
        VEHICLE_CLASSES.put("Light Forklift", "رافعة شوكية خفيفة");
        VEHICLE_CLASSES.put("Heavy Forklift", "رافعة شوكية ثقيلة");
        VEHICLE_CLASSES.put("Motorcycle", "دراجة نارية");
        VEHICLE_CLASSES.put("Motor Cycle", "دراجة نارية");
        VEHICLE_CLASSES.put("Automatic", "أوتوماتيك");

        EMIRATES.put("Abu Dhabi", "أبوظبي");
        EMIRATES.put("Dubai", "دبي");
        EMIRATES.put("Sharjah", "الشارقة");
        EMIRATES.put("Ajman", "عجمان");
        EMIRATES.put("Umm Al Quwain", "أم القيوين");
        EMIRATES.put("Ras Al Khaimah", "رأس الخيمة");
        EMIRATES.put("Fujairah", "الفجيرة");
    }

    @Override
    public DocumentType supports() {
        return DocumentType.DRIVING_LICENSE;
    }

    @Override
    public ExtractionResult extract(ExtractedText text) {
        String all = text.joined();
        List<String> lines = lines(all);
        ExtractionResult r = new ExtractionResult();

        authority(lines, all, r);
        licenseNumber(lines, r);
        dates(lines, all, r);
        drivingExperience(r);
        placeOfIssue(lines, all, r);
        name(lines, r);
        nationality(lines, r);
        trafficCode(lines, all, r);
        permittedVehicles(lines, all, r);

        validate(r);
        r.applyOcrConfidence(text.ocrConfidence());
        return r;
    }

    private void authority(List<String> lines, String all, ExtractionResult r) {
        Optional<TextPatterns.Match> m = find(lines, AUTHORITY);
        if (m.isPresent()) {
            r.put("license_authority_id", m.get().value().toUpperCase(Locale.ROOT), LABELLED);
            return;
        }
        Matcher shape = AUTHORITY_SHAPE.matcher(all);
        if (shape.find()) {
            r.put("license_authority_id", shape.group(1), INFERRED);
        } else {
            r.missing("license_authority_id");
        }
    }

    private void licenseNumber(List<String> lines, ExtractionResult r) {
        Optional<TextPatterns.Match> m = find(lines, LICENSE_NO);
        if (m.isPresent()) {
            String digits = m.get().value().replaceAll("\\D", "");
            if (digits.length() >= 4) {
                r.put("license_number", digits, m.get().stacked() ? INFERRED : LABELLED);
                return;
            }
        }
        r.missing("license_number");
    }

    private void dates(List<String> lines, String all, ExtractionResult r) {
        String issue = find(lines, ISSUE_DATE).flatMap(x -> normaliseDate(x.value())).orElse(null);
        String expiry = find(lines, EXPIRY_DATE).flatMap(x -> normaliseDate(x.value())).orElse(null);
        String dob = find(lines, DOB).flatMap(x -> normaliseDate(x.value())).orElse(null);

        if (issue == null && expiry == null && dob == null) {
            // Positional fallback: a licence front carries exactly these three dates and they are always in
            // chronological order DOB < issue < expiry whatever the layout.
            List<LocalDate> unlabelled = allDates(all).stream().map(d -> parseDate(d).orElse(null)).filter(d -> d != null)
                    .distinct().sorted().toList();
            if (unlabelled.size() == 3) {
                r.put("license_issue_date", fmt(unlabelled.get(1)), INFERRED)
                        .put("license_expire_date", fmt(unlabelled.get(2)), INFERRED)
                        .put("date_of_birth", fmt(unlabelled.get(0)), INFERRED);
                r.warn("Dates assigned by chronological order, labels were not readable");
                return;
            }
        }
        putOrMissing(r, "license_issue_date", issue);
        putOrMissing(r, "license_expire_date", expiry);
        putOrMissing(r, "date_of_birth", dob);
    }

    /**
     * Driving experience = whole years from the licence issue date to today, the way the motor quote derives it
     * (the reference flow stores it as the "Driving Experience" of the primary driver). Inherits the issue date's
     * confidence; an issue date in the future yields no value and a warning.
     */
    private void drivingExperience(ExtractionResult r) {
        Optional<LocalDate> issue = parseDate(r.getString("license_issue_date"));
        if (issue.isEmpty()) {
            r.missing("driving_experience_years");
            return;
        }
        LocalDate today = LocalDate.now(clock);
        if (issue.get().isAfter(today)) {
            r.missing("driving_experience_years");
            r.warn("license_issue_date is in the future");
            return;
        }
        double confidence = r.fieldConfidence().getOrDefault("license_issue_date", INFERRED);
        r.put("driving_experience_years", (int) ChronoUnit.YEARS.between(issue.get(), today), confidence);
    }

    private void placeOfIssue(List<String> lines, String all, ExtractionResult r) {
        Optional<TextPatterns.Match> m = find(lines, PLACE_OF_ISSUE);
        String english = null;
        double confidence = LABELLED;
        if (m.isPresent()) {
            english = canonicalEmirate(m.get().value()).orElse(titleCase(m.get().value()));
        } else {
            for (String emirate : EMIRATES.keySet()) {
                if (all.toLowerCase(Locale.ROOT).contains(emirate.toLowerCase(Locale.ROOT))) {
                    english = emirate;
                    confidence = INFERRED;
                    break;
                }
            }
        }
        if (english == null) {
            r.missing("issue_place_english").missing("issue_place_arabic");
            return;
        }
        String arabic = m.map(x -> arabicPart(lines.get(x.line()))).filter(s -> !s.isEmpty()).orElse(EMIRATES.get(english));
        r.put("issue_place_english", english, confidence);
        r.put("issue_place_arabic", arabic, arabic == null ? 0 : confidence);
    }

    private void name(List<String> lines, ExtractionResult r) {
        Optional<TextPatterns.Match> m = find(lines, NAME);
        if (m.isEmpty()) {
            r.missing("name_english").missing("name_arabic");
            return;
        }
        int lineIdx = m.get().line();
        String rawLine = lines.get(lineIdx);
        String english = stripVehicleClasses(latinPart(m.get().stacked() ? rawLine : m.get().value()));
        english = english.replaceAll("(?i)^name\\s*[:.-]?\\s*", "").replaceAll("\\s+", " ").strip().toUpperCase(Locale.ROOT);

        // Arabic name: same line, else the nearest neighbouring line that is mostly Arabic.
        String arabic = arabicPart(rawLine);
        if (arabic.isEmpty()) {
            for (int offset : new int[]{1, -1, 2}) {
                int i = lineIdx + offset;
                if (i >= 0 && i < lines.size() && isMostlyArabic(lines.get(i))) {
                    arabic = arabicPart(lines.get(i));
                    break;
                }
            }
        }
        arabic = stripVehicleClasses(arabic).strip();

        if (english.isEmpty()) {
            r.missing("name_english");
        } else {
            r.put("name_english", english, LABELLED);
        }
        if (arabic.isEmpty()) {
            r.missing("name_arabic");
        } else {
            r.put("name_arabic", arabic, LABELLED);
        }
    }

    private void nationality(List<String> lines, ExtractionResult r) {
        Optional<TextPatterns.Match> m = find(lines, NATIONALITY);
        if (m.isEmpty()) {
            r.missing("nationality_english").missing("nationality_arabic");
            return;
        }
        String english = latinPart(m.get().value()).replaceAll("[^A-Za-z ]", " ").replaceAll("\\s+", " ").strip().toUpperCase(Locale.ROOT);
        String arabic = arabicPart(lines.get(m.get().line()));
        if (arabic.isEmpty()) {
            arabic = NATIONALITIES_AR.get(english);
        }
        r.put("nationality_english", english.isEmpty() ? null : english, LABELLED);
        r.put("nationality_arabic", arabic, arabic == null ? 0 : (arabic.equals(NATIONALITIES_AR.get(english)) ? INFERRED : LABELLED));
    }

    private void trafficCode(List<String> lines, String all, ExtractionResult r) {
        Optional<TextPatterns.Match> m = find(lines, TRAFFIC_CODE);
        if (m.isPresent()) {
            r.put("traffic_code", m.get().value().replaceAll("\\D", ""), LABELLED);
            return;
        }
        Matcher ten = TEN_DIGITS.matcher(all);
        String license = r.getString("license_number");
        while (ten.find()) {
            if (!ten.group(1).equals(license)) {
                r.put("traffic_code", ten.group(1), INFERRED);
                return;
            }
        }
        r.missing("traffic_code");
    }

    private void permittedVehicles(List<String> lines, String all, ExtractionResult r) {
        List<String> english = new ArrayList<>();
        List<String> arabic = new ArrayList<>();
        String lower = all.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : VEHICLE_CLASSES.entrySet()) {
            if (e.getKey().equals("Automatic")) {
                continue;
            }
            if (lower.contains(e.getKey().toLowerCase(Locale.ROOT)) && !english.contains(normaliseClass(e.getKey()))) {
                english.add(normaliseClass(e.getKey()));
                arabic.add(e.getValue());
            }
        }
        boolean labelled = lines.stream().anyMatch(l -> PERMITTED.matcher(l).find());
        if (english.isEmpty()) {
            r.missing("permitted_vehicle");
        } else {
            Map<String, Object> permitted = new LinkedHashMap<>();
            permitted.put("english", english);
            permitted.put("arabic", arabic);
            r.put("permitted_vehicle", permitted, labelled ? LABELLED : INFERRED);
        }
        boolean automatic = lower.contains("automatic") || all.contains("أوتوماتيك");
        r.put("transmission_type", automatic ? "AUTOMATIC" : null, automatic ? INFERRED : 0);
    }

    private void validate(ExtractionResult r) {
        if (!r.has("license_number") && !r.has("name_english")) {
            r.warn("Neither licence number nor name could be read");
        }
        for (String key : new String[]{"license_number", "name_english", "license_expire_date"}) {
            if (!r.has(key)) {
                r.warn("Required field missing: " + key);
            }
        }
        if (r.has("traffic_code") && r.getString("traffic_code").length() != 10) {
            r.warn("traffic_code should be 10 digits, got " + r.getString("traffic_code").length());
            r.fieldConfidence().computeIfPresent("traffic_code", (k, v) -> Math.min(v, INFERRED));
        }
        Optional<LocalDate> issue = parseDate(r.getString("license_issue_date"));
        Optional<LocalDate> expiry = parseDate(r.getString("license_expire_date"));
        Optional<LocalDate> dob = parseDate(r.getString("date_of_birth"));
        if (issue.isPresent() && expiry.isPresent() && !expiry.get().isAfter(issue.get())) {
            r.warn("license_expire_date is not after license_issue_date");
        }
        if (dob.isPresent() && issue.isPresent() && !dob.get().plusYears(16).isBefore(issue.get())) {
            r.warn("date_of_birth is less than 16 years before the issue date");
        }
        if (expiry.isPresent() && expiry.get().isBefore(LocalDate.now())) {
            r.warn("Licence expired on " + r.getString("license_expire_date"));
        }
    }

    // ---- helpers ----

    private static void putOrMissing(ExtractionResult r, String key, String value) {
        if (value == null) {
            r.missing(key);
        } else {
            r.put(key, value, LABELLED);
        }
    }

    private static String fmt(LocalDate d) {
        return String.format("%02d/%02d/%04d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }

    private static Optional<String> canonicalEmirate(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        return EMIRATES.keySet().stream().filter(e -> lower.contains(e.toLowerCase(Locale.ROOT))).findFirst();
    }

    private static String titleCase(String raw) {
        StringBuilder sb = new StringBuilder();
        for (String word : raw.strip().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    private static String normaliseClass(String key) {
        return key.equals("Motor Cycle") ? "Motorcycle" : key;
    }

    /** Removes "Light Vehicle", "Automatic", the Arabic equivalents and the "R EPM"-style class codes OCR glues to names. */
    static String stripVehicleClasses(String value) {
        String out = value;
        for (Map.Entry<String, String> e : VEHICLE_CLASSES.entrySet()) {
            out = out.replaceAll("(?i)\\b" + Pattern.quote(e.getKey()) + "\\b", " ");
            out = out.replace(e.getValue(), " ");
        }
        // short all-caps codes such as "R", "EPM", "LMV" that sit between the class and the real name
        out = out.replaceAll("^(?:\\s*\\b[A-Z]{1,3}\\b)+(?=\\s+[A-Z]{3,})", " ");
        return out.replaceAll("\\s+", " ").strip();
    }
}
