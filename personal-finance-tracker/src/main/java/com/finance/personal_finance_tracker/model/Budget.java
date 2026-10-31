package com.finance.personal_finance_tracker.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "budgets", uniqueConstraints = @UniqueConstraint(
        name = "uk_budget_user_category_month", columnNames = {"user_id", "category", "month_start"}))
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(name = "month_start", nullable = false)
    private LocalDate monthStart;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    protected Budget() {
    }

    public Budget(User user, String category, LocalDate monthStart, BigDecimal amount) {
        this.user = user;
        this.category = category;
        this.monthStart = monthStart;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getCategory() {
        return category;
    }

    public LocalDate getMonthStart() {
        return monthStart;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
