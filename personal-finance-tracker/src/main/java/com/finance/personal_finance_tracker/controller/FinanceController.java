package com.finance.personal_finance_tracker.controller;

import com.finance.personal_finance_tracker.model.Transaction;
import com.finance.personal_finance_tracker.model.User;
import com.finance.personal_finance_tracker.service.BudgetService;
import com.finance.personal_finance_tracker.service.CsvImportService;
import com.finance.personal_finance_tracker.service.DashboardAnalyticsService;
import com.finance.personal_finance_tracker.service.TransactionService;
import com.finance.personal_finance_tracker.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

@Controller
public class FinanceController {
    private static final String USER_ID_SESSION_KEY = "loggedInUserId";
    private final UserService userService;
    private final TransactionService transactionService;
    private final BudgetService budgetService;
    private final DashboardAnalyticsService analyticsService;
    private final CsvImportService csvImportService;

    public FinanceController(UserService userService, TransactionService transactionService,
                             BudgetService budgetService, DashboardAnalyticsService analyticsService,
                             CsvImportService csvImportService) {
        this.userService = userService;
        this.transactionService = transactionService;
        this.budgetService = budgetService;
        this.analyticsService = analyticsService;
        this.csvImportService = csvImportService;
    }

    @GetMapping("/")
    public String index() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String showLoginPage(Model model) {
        model.addAttribute("user", new User());
        return "login";
    }

    @PostMapping("/login")
    public String processLogin(@ModelAttribute User credentials, HttpServletRequest request, Model model) {
        return userService.loginUser(credentials.getUsername(), credentials.getPassword())
                .map(user -> {
                    HttpSession session = request.getSession(true);
                    request.changeSessionId();
                    session.setAttribute(USER_ID_SESSION_KEY, user.getId());
                    return "redirect:/dashboard";
                })
                .orElseGet(() -> {
                    model.addAttribute("error", "Invalid username or password.");
                    model.addAttribute("user", new User());
                    return "login";
                });
    }

    @GetMapping("/register")
    public String showRegisterPage(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String processRegistration(@ModelAttribute User user, RedirectAttributes redirectAttributes) {
        try {
            userService.registerUser(user);
            return "redirect:/login?registered";
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("registrationError", exception.getMessage());
            return "redirect:/register";
        } catch (DataIntegrityViolationException exception) {
            redirectAttributes.addFlashAttribute("registrationError",
                    "That username or email address is already registered.");
            return "redirect:/register";
        }
    }

    @GetMapping("/dashboard")
    public String showDashboard(@RequestParam(defaultValue = "0") int page, HttpSession session, Model model) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        Page<Transaction> transactionPage = transactionService.getTransactions(user, Math.max(0, page), 20);
        if (transactionPage.isEmpty() && transactionPage.getTotalElements() > 0
                && transactionPage.getTotalPages() > 0) {
            return "redirect:/dashboard?page=" + (transactionPage.getTotalPages() - 1);
        }
        DashboardAnalyticsService.Analytics analytics = analyticsService.analyze(user);
        List<BudgetService.BudgetView> budgets = budgetService.currentMonth(user);
        BigDecimal totalIncome = transactionService.totalForType(user, "INCOME");
        BigDecimal totalExpense = transactionService.totalForType(user, "EXPENSE");

        model.addAttribute("user", user);
        model.addAttribute("transactions", transactionPage.getContent());
        model.addAttribute("transactionPage", transactionPage);
        model.addAttribute("totalIncome", totalIncome);
        model.addAttribute("totalExpense", totalExpense);
        model.addAttribute("balance", totalIncome.subtract(totalExpense));
        model.addAttribute("newTransaction", new Transaction());
        model.addAttribute("analytics", analytics);
        model.addAttribute("budgets", budgets);
        model.addAttribute("currentMonth", monthStart.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        model.addAttribute("suggestedCategories", List.of(
                "Food", "Transport", "Housing", "Shopping", "Health", "Entertainment",
                "Utilities", "Salary", "Other", "Other income"));
        return "dashboard";
    }

    @PostMapping("/transactions/add")
    public String addTransaction(@ModelAttribute Transaction transaction, HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        try {
            if (transaction.getDate() == null) {
                transaction.setDate(LocalDate.now());
            }
            transactionService.saveTransaction(transaction, user);
            redirectAttributes.addFlashAttribute("successMessage", "Transaction added.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("transactionError", exception.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/transactions/delete/{id}")
    public String deleteTransaction(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        try {
            transactionService.deleteTransaction(id, user);
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("transactionError", exception.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/budgets")
    public String saveBudget(@RequestParam String category, @RequestParam String amount,
                             HttpSession session, RedirectAttributes redirectAttributes) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        try {
            budgetService.saveCurrentMonth(user, category, new BigDecimal(amount));
            redirectAttributes.addFlashAttribute("successMessage", "Monthly budget saved.");
        } catch (NumberFormatException exception) {
            redirectAttributes.addFlashAttribute("budgetError", "Enter a valid budget amount.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("budgetError", exception.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/budgets/{id}/delete")
    public String deleteBudget(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        try {
            budgetService.deleteBudget(id, user);
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("budgetError", exception.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/transactions/import")
    public String importTransactions(@RequestParam("file") MultipartFile file, HttpSession session,
                                     RedirectAttributes redirectAttributes) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        try {
            int count = csvImportService.importFile(file, user);
            redirectAttributes.addFlashAttribute("successMessage", count + " transactions imported.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("importError", exception.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    private User currentUser(HttpSession session) {
        Object id = session.getAttribute(USER_ID_SESSION_KEY);
        if (!(id instanceof Long userId)) {
            return null;
        }
        return userService.findById(userId).orElse(null);
    }
}
