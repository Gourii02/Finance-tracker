package com.finance.personal_finance_tracker.repository;

import com.finance.personal_finance_tracker.model.Budget;
import com.finance.personal_finance_tracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findByUserAndMonthStartOrderByCategoryAsc(User user, LocalDate monthStart);

    Optional<Budget> findByUserAndCategoryIgnoreCaseAndMonthStart(User user, String category, LocalDate monthStart);

    long deleteByIdAndUser(Long id, User user);
}
