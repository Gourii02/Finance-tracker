package com.finance.personal_finance_tracker.service;

import com.finance.personal_finance_tracker.model.Transaction;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CsvImportService {
    private static final long MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_ROWS = 5_000;
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"));
    private final TransactionService transactionService;

    public CsvImportService(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    public int importFile(MultipartFile file, com.finance.personal_finance_tracker.model.User user) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a CSV file to import.");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new IllegalArgumentException("Choose a .csv file.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("CSV files must be 2 MB or smaller.");
        }
        try {
            List<List<String>> rows = parse(new String(file.getBytes(), StandardCharsets.UTF_8));
            if (rows.size() < 2) {
                throw new IllegalArgumentException("The CSV must include a header and at least one transaction.");
            }
            if (rows.size() - 1 > MAX_ROWS) {
                throw new IllegalArgumentException("CSV imports are limited to 5,000 transactions at a time.");
            }
            Map<String, Integer> columns = headerMap(rows.get(0));
            int dateColumn = requiredColumn(columns, "date");
            int titleColumn = columns.containsKey("description") ? columns.get("description")
                    : requiredColumn(columns, "title");
            int amountColumn = requiredColumn(columns, "amount");
            Integer typeColumn = columns.get("type");
            Integer categoryColumn = columns.get("category");

            List<Transaction> transactions = new ArrayList<>();
            for (int line = 1; line < rows.size(); line++) {
                List<String> row = rows.get(line);
                if (row.size() == 1 && row.get(0).isBlank()) {
                    continue;
                }
                String prefix = "CSV row " + (line + 1) + ": ";
                int expectedColumns = Math.max(Math.max(dateColumn, titleColumn), amountColumn);
                if (typeColumn != null) expectedColumns = Math.max(expectedColumns, typeColumn);
                if (categoryColumn != null) expectedColumns = Math.max(expectedColumns, categoryColumn);
                if (row.size() <= expectedColumns) {
                    throw new IllegalArgumentException(prefix + "not enough columns.");
                }

                String rawAmount = cell(row, amountColumn).replace("₹", "").replace(",", "").trim();
                BigDecimal amount;
                try {
                    amount = new BigDecimal(rawAmount);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException(prefix + "amount is not a valid number.");
                }
                String type = typeColumn == null ? "" : cell(row, typeColumn).trim().toUpperCase(Locale.ROOT);
                if (type.isBlank()) {
                    type = amount.signum() < 0 ? "EXPENSE" : "INCOME";
                } else if ("CR".equals(type) || "CREDIT".equals(type)) {
                    type = "INCOME";
                } else if ("DR".equals(type) || "DEBIT".equals(type)) {
                    type = "EXPENSE";
                }
                if (amount.signum() < 0) {
                    amount = amount.abs();
                }
                if (!"INCOME".equals(type) && !"EXPENSE".equals(type)) {
                    throw new IllegalArgumentException(prefix + "type must be INCOME or EXPENSE.");
                }

                Transaction transaction = new Transaction();
                transaction.setDate(parseDate(cell(row, dateColumn), prefix));
                transaction.setTitle(cell(row, titleColumn).trim());
                transaction.setAmount(amount);
                transaction.setType(type);
                transaction.setCategory(categoryColumn == null ? "" : cell(row, categoryColumn).trim());
                transactions.add(transaction);
            }
            if (transactions.isEmpty()) {
                throw new IllegalArgumentException("The CSV contains no transaction rows.");
            }
            return transactionService.importCsv(transactions, user);
        } catch (IOException exception) {
            throw new IllegalArgumentException("The CSV file could not be read.", exception);
        }
    }

    private LocalDate parseDate(String value, String prefix) {
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                LocalDate date = LocalDate.parse(value.trim(), format);
                if (date.isAfter(LocalDate.now().plusDays(1))) {
                    throw new IllegalArgumentException(prefix + "date cannot be in the future.");
                }
                return date;
            } catch (DateTimeParseException ignored) {
                // Try the next supported date format.
            }
        }
        throw new IllegalArgumentException(prefix + "date must use yyyy-MM-dd, d/M/yyyy, or d-M-yyyy.");
    }

    private Map<String, Integer> headerMap(List<String> header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String name = header.get(i).replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT);
            if (!name.isBlank() && columns.putIfAbsent(name, i) != null) {
                throw new IllegalArgumentException("The CSV has duplicate column names.");
            }
        }
        return columns;
    }

    private int requiredColumn(Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        if (index == null) {
            throw new IllegalArgumentException("CSV is missing the required '" + name + "' column.");
        }
        return index;
    }

    private String cell(List<String> row, int column) {
        return row.get(column).trim();
    }

    private List<List<String>> parse(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            char current = csv.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if ((current == '\n' || current == '\r') && !quoted) {
                if (current == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') i++;
                row.add(cell.toString());
                cell.setLength(0);
                if (!(row.size() == 1 && row.get(0).isBlank())) rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(current);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("The CSV contains an unclosed quoted field.");
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }
}
