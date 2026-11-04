package com.insurance.document.extraction.field;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small, dependency-free helpers shared by the field extractors: scripts, dates, label lookups. */
final class TextPatterns {

    /** Arabic letters, Arabic presentation forms and the Arabic-Indic digits. */
    static final Pattern ARABIC_RUN = Pattern.compile("[\\p{IsArabic}\\u0660-\\u0669\\uFB50-\\uFDFF\\uFE70-\\uFEFF][\\p{IsArabic}\\u0660-\\u0669\\uFB50-\\uFDFF\\uFE70-\\uFEFF\\s/.-]*");
    static final Pattern LATIN_DIGIT_RUN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 .,'/()&-]*");
    static final Pattern DATE = Pattern.compile("\\b(\\d{2})[/.-](\\d{2})[/.-](\\d{4})\\b");
    private static final DateTimeFormatter STRICT_DMY = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private TextPatterns() {
    }

    static List<String> lines(String text) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    /** Everything in Arabic script on the line, joined with single spaces. Empty when there is none. */
    static String arabicPart(String line) {
        Matcher m = ARABIC_RUN.matcher(line);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String run = m.group().strip();
            if (!run.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(run);
            }
        }
        return sb.toString().replaceAll("\\s+", " ").strip();
    }

    /** The line with every Arabic run removed and whitespace collapsed. */
    static String latinPart(String line) {
        return ARABIC_RUN.matcher(line).replaceAll(" ").replaceAll("\\s+", " ").strip();
    }

    static boolean isMostlyArabic(String line) {
        int arabic = 0;
        int latin = 0;
        for (char c : line.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.ARABIC) {
                arabic++;
            } else if (Character.isLetterOrDigit(c)) {
                latin++;
            }
        }
        return arabic > latin;
    }

    /** Normalises 18.09.1995 / 18-09-1995 to 18/09/1995 and rejects impossible dates (OCR likes 31/02). */
    static Optional<String> normaliseDate(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher m = DATE.matcher(raw);
        if (!m.find()) {
            return Optional.empty();
        }
        String candidate = m.group(1) + "/" + m.group(2) + "/" + m.group(3);
        try {
            LocalDate.parse(candidate, STRICT_DMY);
            return Optional.of(candidate);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    static Optional<LocalDate> parseDate(String ddMMyyyy) {
        if (ddMMyyyy == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(ddMMyyyy, STRICT_DMY));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** All valid dd/MM/yyyy dates in the text, in reading order. */
    static List<String> allDates(String text) {
        List<String> dates = new ArrayList<>();
        Matcher m = DATE.matcher(text);
        while (m.find()) {
            normaliseDate(m.group()).ifPresent(dates::add);
        }
        return dates;
    }

    /**
     * First capture group of {@code pattern} across the lines; when the label sits alone on a line the value is
     * taken from the next line (common on cards where label and value are stacked).
     */
    static Optional<Match> find(List<String> lines, Pattern pattern) {
        // Pass 1: a line where the label is followed by a value ("License No. 0194428") always wins over a line that
        // merely contains the label word ("DRIVING LICENSE" in the card title).
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = pattern.matcher(lines.get(i));
            while (m.find()) {
                String value = m.group(m.groupCount());
                if (value != null && !value.isBlank()) {
                    return Optional.of(new Match(value.strip(), i, false));
                }
            }
        }
        // Pass 2: label alone at the end of its line, value stacked on the line below.
        for (int i = 0; i + 1 < lines.size(); i++) {
            Matcher m = pattern.matcher(lines.get(i));
            if (m.find() && m.end() >= latinPart(lines.get(i)).length() - 1) {
                return Optional.of(new Match(lines.get(i + 1), i + 1, true));
            }
        }
        return Optional.empty();
    }

    /** Label (may contain alternations) followed by an optional separator and the optional value as the only capture group. */
    static Pattern labelled(String labelRegex, String valueRegex) {
        return Pattern.compile("(?i)(?:^|\\b)(?:" + labelRegex + ")\\s*[:.\\-]?\\s*(" + valueRegex + ")?");
    }

    static boolean containsIgnoreCase(String haystack, String... needles) {
        String lower = haystack.toLowerCase();
        return Arrays.stream(needles).anyMatch(n -> lower.contains(n.toLowerCase()));
    }

    /**
     * @param value    the matched value (already stripped)
     * @param line     index of the line the value came from
     * @param stacked  true when the value was taken from the line below the label
     */
    record Match(String value, int line, boolean stacked) {
    }
}
