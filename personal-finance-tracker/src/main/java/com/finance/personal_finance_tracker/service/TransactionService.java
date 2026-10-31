package com.finance.personal_finance_tracker.service;

import com.finance.personal_finance_tracker.model.Transaction;
import com.finance.personal_finance_tracker.model.User;
import com.finance.personal_finance_tracker.repository.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final CategorySuggestionService categorySuggestionService;

    public TransactionService(TransactionRepository transactionRepository,
                              CategorySuggestionService categorySuggestionService) {
        this.transactionRepository = transactionRepository;
        this.categorySuggestionService = categorySuggestionService;
    }

    public Page<Transaction> getTransactions(User user, int page, int pageSize) {
        return transactionRepository.findByUserOrderByDateDescIdDesc(user, PageRequest.of(page, pageSize));
    }

    public BigDecimal totalForType(User user, String type) {
        return transactionRepository.sumAmountByUserAndType(user, type);
    }

    @Transactional
    public Transaction saveTransaction(Transaction transaction, User user) {
        validate(transaction);
        transaction.setTitle(transaction.getTitle().trim());
        transaction.setType(transaction.getType().toUpperCase(java.util.Locale.ROOT));
        transaction.setCategory(transaction.getCategory() == null || transaction.getCategory().isBlank()
                ? categorySuggestionService.suggest(transaction.getTitle(), transaction.getType())
                : transaction.getCategory().trim());
        transaction.setUser(user);
        return transactionRepository.save(transaction);
    }

    @Transactional
    public void deleteTransaction(Long id, User user) {
        if (transactionRepository.deleteByIdAndUser(id, user) == 0) {
            throw new IllegalArgumentException("Transaction not found.");
        }
    }

    @Transactional
    public int importCsv(List<Transaction> transactions, User user) {
        for (Transaction transaction : transactions) {
            validate(transaction);
            transaction.setTitle(transaction.getTitle().trim());
            transaction.setType(transaction.getType().toUpperCase(java.util.Locale.ROOT));
            transaction.setCategory(transaction.getCategory() == null || transaction.getCategory().isBlank()
                    ? categorySuggestionService.suggest(transaction.getTitle(), transaction.getType())
                    : transaction.getCategory().trim());
            transaction.setUser(user);
        }
        transactionRepository.saveAll(transactions);
        return transactions.size();
    }

    private void validate(Transaction transaction) {
        if (transaction.getTitle() == null || transaction.getTitle().isBlank()
                || transaction.getTitle().trim().length() > 160) {
            throw new IllegalArgumentException("Transaction title is required and must be at most 160 characters.");
        }
        if (transaction.getAmount() == null || transaction.getAmount().signum() <= 0
                || transaction.getAmount().scale() > 2 || transaction.getAmount().precision() > 12) {
            throw new IllegalArgumentException("Enter a positive amount with no more than two decimal places.");
        }
        if (!"INCOME".equalsIgnoreCase(transaction.getType()) && !"EXPENSE".equalsIgnoreCase(transaction.getType())) {
            throw new IllegalArgumentException("Transaction type must be income or expense.");
        }
        if (transaction.getDate() == null || transaction.getDate().isAfter(LocalDate.now().plusDays(1))) {
            throw new IllegalArgumentException("Enter a valid transaction date.");
        }
        if (transaction.getCategory() != null && transaction.getCategory().trim().length() > 80) {
            throw new IllegalArgumentException("Category must be at most 80 characters.");
        }
    }
}
