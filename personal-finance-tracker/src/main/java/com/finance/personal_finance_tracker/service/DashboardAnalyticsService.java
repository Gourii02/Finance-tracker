package com.finance.personal_finance_tracker.service;

import com.finance.personal_finance_tracker.model.User;
import com.finance.personal_finance_tracker.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardAnalyticsService {
    private static final List<String> CHART_COLORS = List.of(
            "#168253", "#4b9e70", "#83bd91", "#d0a64f", "#5478a1", "#bd6a59", "#8b77b4", "#589d9b");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);
    private final TransactionRepository transactionRepository;

    public DashboardAnalyticsService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Analytics analyze(User user) {
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate categoryRangeStart = currentMonth.minusMonths(5);
        List<Object[]> categoryRows = transactionRepository.sumExpensesByCategoryInPeriod(
                user, categoryRangeStart, currentMonth.plusMonths(1));
        BigDecimal categoryTotal = categoryRows.stream()
                .map(row -> (BigDecimal) row[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CategorySpend> categories = new ArrayList<>();
        for (int i = 0; i < categoryRows.size(); i++) {
            Object[] row = categoryRows.get(i);
            BigDecimal amount = (BigDecimal) row[1];
            double percent = categoryTotal.signum() == 0 ? 0
                    : amount.multiply(BigDecimal.valueOf(100))
                    .divide(categoryTotal, 2, RoundingMode.HALF_UP).doubleValue();
            categories.add(new CategorySpend(String.valueOf(row[0]), amount, percent,
                    CHART_COLORS.get(i % CHART_COLORS.size())));
        }
        String donutStyle;
        if (categories.isEmpty()) {
            donutStyle = "background: conic-gradient(#e7eee8 0% 100%)";
        } else {
            StringBuilder gradient = new StringBuilder("background: conic-gradient(");
            double cursor = 0;
            for (int i = 0; i < categories.size(); i++) {
                CategorySpend category = categories.get(i);
                double end = i == categories.size() - 1 ? 100 : Math.min(100, cursor + category.percent());
                if (i > 0) {
                    gradient.append(", ");
                }
                gradient.append(category.color()).append(' ').append(cursor).append("% ").append(end).append('%');
                cursor = end;
            }
            donutStyle = gradient.append(')').toString();
        }

        LocalDate chartStart = currentMonth.minusMonths(5);
        List<Object[]> monthRows = transactionRepository.sumAmountsByMonthAndType(
                user, chartStart, currentMonth.plusMonths(1));
        Map<YearMonth, Map<String, BigDecimal>> byMonth = monthRows.stream().collect(Collectors.groupingBy(
                row -> YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()),
                Collectors.toMap(row -> String.valueOf(row[2]), row -> (BigDecimal) row[3])));

        List<MonthSummary> months = new ArrayList<>();
        BigDecimal maximum = BigDecimal.ZERO;
        for (int i = 0; i < 6; i++) {
            YearMonth month = YearMonth.from(chartStart).plusMonths(i);
            Map<String, BigDecimal> amounts = byMonth.getOrDefault(month, Map.of());
            BigDecimal income = amounts.getOrDefault("INCOME", BigDecimal.ZERO);
            BigDecimal expense = amounts.getOrDefault("EXPENSE", BigDecimal.ZERO);
            maximum = maximum.max(income).max(expense);
            months.add(new MonthSummary(month.format(MONTH_LABEL), income, expense, 0, 0));
        }
        BigDecimal scale = maximum.signum() == 0 ? BigDecimal.ONE : maximum;
        List<MonthSummary> scaledMonths = months.stream().map(month -> new MonthSummary(
                month.label(), month.income(), month.expense(),
                month.income().multiply(BigDecimal.valueOf(100)).divide(scale, 0, RoundingMode.HALF_UP).intValue(),
                month.expense().multiply(BigDecimal.valueOf(100)).divide(scale, 0, RoundingMode.HALF_UP).intValue()
        )).toList();
        return new Analytics(categories, scaledMonths, donutStyle, categoryTotal);
    }

    public record CategorySpend(String category, BigDecimal amount, double percent, String color) {
    }

    public record MonthSummary(String label, BigDecimal income, BigDecimal expense,
                               int incomeHeight, int expenseHeight) {
    }

    public record Analytics(List<CategorySpend> categories, List<MonthSummary> months,
                            String donutStyle, BigDecimal expenseTotal) {
    }
}
