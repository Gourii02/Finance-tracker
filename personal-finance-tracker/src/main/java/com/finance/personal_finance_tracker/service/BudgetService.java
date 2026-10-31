package com.finance.personal_finance_tracker.service;

import com.finance.personal_finance_tracker.model.Budget;
import com.finance.personal_finance_tracker.model.User;
import com.finance.personal_finance_tracker.repository.BudgetRepository;
import com.finance.personal_finance_tracker.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BudgetService {
    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;

    public BudgetService(BudgetRepository budgetRepository, TransactionRepository transactionRepository) {
        this.budgetRepository = budgetRepository;
        this.transactionRepository = transactionRepository;
    }

    public List<BudgetView> currentMonth(User user) {
        LocalDate month = LocalDate.now().withDayOfMonth(1);
        Map<String, BigDecimal> expensesByCategory = transactionRepository
                .sumExpensesByCategoryInPeriod(user, month, month.plusMonths(1)).stream()
                .collect(Collectors.toMap(row -> String.valueOf(row[0]).toLowerCase(java.util.Locale.ROOT),
                        row -> (BigDecimal) row[1],
                        BigDecimal::add));
        return budgetRepository.findByUserAndMonthStartOrderByCategoryAsc(user, month).stream()
                .map(budget -> {
                    BigDecimal spent = expensesByCategory.getOrDefault(
                            budget.getCategory().toLowerCase(java.util.Locale.ROOT), BigDecimal.ZERO);
                    BigDecimal ratio = spent.multiply(BigDecimal.valueOf(100))
                            .divide(budget.getAmount(), 0, RoundingMode.DOWN);
                    long percentage = ratio.min(BigDecimal.valueOf(1_000_000)).longValue();
                    String alert = spent.compareTo(budget.getAmount()) >= 0 ? "Over budget"
                            : spent.multiply(BigDecimal.valueOf(100))
                            .compareTo(budget.getAmount().multiply(BigDecimal.valueOf(80))) >= 0
                            ? "Approaching limit" : "";
                    return new BudgetView(budget.getId(), budget.getCategory(), budget.getAmount(),
                            spent, percentage, (int) Math.min(percentage, 100), alert);
                }).toList();
    }

    @Transactional
    public void saveCurrentMonth(User user, String category, BigDecimal amount) {
        String normalizedCategory = category == null ? "" : category.trim();
        if (normalizedCategory.isBlank() || normalizedCategory.length() > 80) {
            throw new IllegalArgumentException("Enter a category of up to 80 characters.");
        }
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || amount.precision() > 12) {
            throw new IllegalArgumentException("Budget must be positive and have no more than two decimal places.");
        }
        LocalDate month = LocalDate.now().withDayOfMonth(1);
        Optional<Budget> existing = budgetRepository.findByUserAndCategoryIgnoreCaseAndMonthStart(
                user, normalizedCategory, month);
        if (existing.isPresent()) {
            existing.get().setAmount(amount);
        } else {
            budgetRepository.save(new Budget(user, normalizedCategory, month, amount));
        }
    }

    @Transactional
    public void deleteBudget(Long id, User user) {
        if (budgetRepository.deleteByIdAndUser(id, user) == 0) {
            throw new IllegalArgumentException("Budget not found.");
        }
    }

    public record BudgetView(Long id, String category, BigDecimal amount, BigDecimal spent,
                             long percentage, int progressPercentage, String alert) {
    }
}
