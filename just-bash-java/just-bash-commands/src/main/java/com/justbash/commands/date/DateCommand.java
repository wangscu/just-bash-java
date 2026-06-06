package com.justbash.commands.date;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class DateCommand implements Command {
    @Override
    public String name() { return "date"; }

    private static final Set<String> VALID_ZONES = Set.of(
        "UTC", "GMT", "America/New_York", "America/Los_Angeles", "America/Chicago",
        "Europe/London", "Europe/Paris", "Europe/Berlin", "Asia/Tokyo", "Asia/Shanghai",
        "Asia/Dubai", "Australia/Sydney", "Pacific/Auckland"
    );

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            boolean utc = false;
            String dateStr = null;
            String fmt = null;
            boolean iso = false;
            boolean rfc = false;

            for (int i = 0; i < args.size(); i++) {
                String a = args.get(i);
                if (a.equals("-u") || a.equals("--utc")) {
                    utc = true;
                } else if (a.equals("-d") || a.equals("--date")) {
                    dateStr = i + 1 < args.size() ? args.get(++i) : "";
                } else if (a.startsWith("--date=")) {
                    dateStr = a.substring(7);
                } else if (a.equals("-I") || a.equals("--iso-8601")) {
                    iso = true;
                } else if (a.equals("-R") || a.equals("--rfc-email")) {
                    rfc = true;
                } else if (a.startsWith("+")) {
                    fmt = a.substring(1);
                } else if (a.startsWith("--")) {
                    return new ExecResult("", "date: invalid option '" + a + "'\n", 1);
                } else if (a.startsWith("-") && !a.equals("-")) {
                    for (char c : a.substring(1).toCharArray()) {
                        if (c == 'u') utc = true;
                        else if (c == 'I') iso = true;
                        else if (c == 'R') rfc = true;
                        else return new ExecResult("", "date: invalid option -- '" + c + "'\n", 1);
                    }
                }
            }

            String tzEnv = ctx.env().get("TZ");
            String parseTz = (tzEnv != null && VALID_ZONES.contains(tzEnv)) ? tzEnv : null;
            String displayTz = utc ? "UTC" : (parseTz != null ? parseTz : "UTC");

            ZonedDateTime date;
            if (dateStr != null) {
                Instant parsed = parseDate(dateStr, parseTz);
                if (parsed == null) {
                    return new ExecResult("", "date: invalid date '" + dateStr + "'\n", 1);
                }
                date = ZonedDateTime.ofInstant(parsed, ZoneId.of(displayTz));
            } else {
                date = ZonedDateTime.now(ZoneId.of(displayTz));
            }

            String out;
            if (fmt != null) {
                out = formatStrftime(fmt, date);
            } else if (iso) {
                out = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
            } else if (rfc) {
                out = date.format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss XXX", Locale.ENGLISH));
            } else {
                out = date.format(DateTimeFormatter.ofPattern("EEE MMM ppd HH:mm:ss z yyyy", Locale.ENGLISH));
            }
            return new ExecResult(out + "\n", "", 0);
        });
    }

    private Instant parseDate(String s, String tz) {
        s = s.trim();
        if (s.startsWith("@")) {
            String suffix = s.substring(1);
            if (suffix.matches("^-?\\d+$")) {
                return Instant.ofEpochSecond(Long.parseLong(suffix));
            }
            return null;
        }
        String l = s.toLowerCase();
        if (l.equals("now") || l.equals("today")) {
            return Instant.now();
        }
        if (l.equals("yesterday")) {
            return Instant.now().minusSeconds(86400);
        }
        if (l.equals("tomorrow")) {
            return Instant.now().plusSeconds(86400);
        }
        try {
            // Try ISO date parsing
            if (s.matches("^\\d{4}-\\d{2}-\\d{2}([T ]\\d{2}:\\d{2}(?::\\d{2})?)?$")) {
                String iso = s.replace(" ", "T");
                if (!iso.contains("T")) iso += "T00:00:00";
                else if (iso.matches(".*T\\d{2}:\\d{2}$")) iso += ":00";
                return Instant.parse(iso + "Z");
            }
            return Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private String formatStrftime(String fmt, ZonedDateTime d) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < fmt.length(); i++) {
            if (fmt.charAt(i) == '%' && i + 1 < fmt.length()) {
                char c = fmt.charAt(++i);
                result.append(switch (c) {
                    case '%' -> "%";
                    case 'Y' -> String.valueOf(d.getYear());
                    case 'y' -> String.format("%02d", d.getYear() % 100);
                    case 'm' -> String.format("%02d", d.getMonthValue());
                    case 'd' -> String.format("%02d", d.getDayOfMonth());
                    case 'e' -> String.format("%2d", d.getDayOfMonth());
                    case 'H' -> String.format("%02d", d.getHour());
                    case 'M' -> String.format("%02d", d.getMinute());
                    case 'S' -> String.format("%02d", d.getSecond());
                    case 'A' -> d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
                    case 'a' -> d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                    case 'B' -> d.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
                    case 'b', 'h' -> d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                    case 'I' -> String.format("%02d", d.getHour() % 12 == 0 ? 12 : d.getHour() % 12);
                    case 'p' -> d.getHour() < 12 ? "AM" : "PM";
                    case 'Z' -> d.getZone().getId();
                    case 'z' -> d.format(DateTimeFormatter.ofPattern("XX"));
                    case 'j' -> String.format("%03d", d.getDayOfYear());
                    case 'u' -> String.valueOf(d.getDayOfWeek().getValue());
                    case 'w' -> String.valueOf(d.getDayOfWeek().getValue() % 7);
                    case 's' -> String.valueOf(d.toEpochSecond());
                    case 'n' -> "\n";
                    case 't' -> "\t";
                    case 'c' -> d.format(DateTimeFormatter.ofPattern("EEE MMM ppd HH:mm:ss yyyy", Locale.ENGLISH));
                    case 'x' -> d.format(DateTimeFormatter.ofPattern("MM/dd/yy", Locale.ENGLISH));
                    case 'X' -> d.format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH));
                    case 'D' -> d.format(DateTimeFormatter.ofPattern("MM/dd/yy", Locale.ENGLISH));
                    case 'F' -> d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH));
                    case 'T' -> d.format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH));
                    case 'r' -> d.format(DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH));
                    case 'R' -> d.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH));
                    default -> "%" + c;
                });
            } else {
                result.append(fmt.charAt(i));
            }
        }
        return result.toString();
    }
}
