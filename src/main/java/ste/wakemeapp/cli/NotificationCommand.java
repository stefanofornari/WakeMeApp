package ste.wakemeapp.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;

@Command(name = "notify", description = "Trigger a meeting notification",
         mixinStandardHelpOptions = true, version = "1.0.0")
public class NotificationCommand implements Callable<Integer> {

    @Option(names = {"-t", "--title"}, description = "Meeting title", required = true)
    private String title;

    @Option(names = {"-s", "--start-date"}, description = "Meeting start date/time (ISO-8601)", required = true)
    private String startDateStr;

    @Option(names = {"-e", "--end-date"}, description = "Meeting end date/time (ISO-8601)")
    private String endDateStr;

    @Option(names = {"-o", "--organizer"}, description = "Meeting organizer")
    private String organizer;

    @Option(names = {"-u", "--url"}, description = "Join URL (Teams, Zoom, etc.)")
    private String url;

    @Option(names = {"-d", "--description"}, description = "Meeting description")
    private String description;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @Override
    public Integer call() {
        try {
            validateAndParseDates();
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private void validateAndParseDates() {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        try {
            startDate = LocalDateTime.parse(startDateStr, formatter);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid --start-date format. Expected ISO-8601 (e.g., 2026-11-03T10:00:00)");
        }

        if (endDateStr != null && !endDateStr.isBlank()) {
            try {
                endDate = LocalDateTime.parse(endDateStr, formatter);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid --end-date format. Expected ISO-8601 (e.g., 2026-11-03T11:00:00)");
            }
        }
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public String getOrganizer() {
        return organizer;
    }

    public String getUrl() {
        return url;
    }

    public String getDescription() {
        return description;
    }
}