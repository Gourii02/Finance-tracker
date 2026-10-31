package com.finance.personal_finance_tracker.repository;

import com.finance.personal_finance_tracker.model.Transaction;
import com.finance.personal_finance_tracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Page<Transaction> findByUserOrderByDateDescIdDesc(User user, Pageable pageable);

    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.user = :user and t.type = :type")
    BigDecimal sumAmountByUserAndType(@Param("user") User user, @Param("type") String type);

    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.user = :user and t.type = 'EXPENSE' and t.category = :category and t.date >= :from and t.date < :until")
    BigDecimal sumExpensesByCategoryAndPeriod(@Param("user") User user, @Param("category") String category,
                                               @Param("from") LocalDate from, @Param("until") LocalDate until);

    @Query("select t.category, sum(t.amount) from Transaction t where t.user = :user and t.type = 'EXPENSE' and t.date >= :from and t.date < :until group by t.category order by sum(t.amount) desc")
    List<Object[]> sumExpensesByCategoryInPeriod(@Param("user") User user, @Param("from") LocalDate from,
                                                 @Param("until") LocalDate until);

    @Query("select function('year', t.date), function('month', t.date), t.type, sum(t.amount) from Transaction t where t.user = :user and t.date >= :from and t.date < :until group by function('year', t.date), function('month', t.date), t.type")
    List<Object[]> sumAmountsByMonthAndType(@Param("user") User user, @Param("from") LocalDate from,
                                            @Param("until") LocalDate until);

    long deleteByIdAndUser(Long id, User user);
}