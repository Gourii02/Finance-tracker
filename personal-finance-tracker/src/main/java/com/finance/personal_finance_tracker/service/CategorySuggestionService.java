package com.finance.personal_finance_tracker.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CategorySuggestionService {
    private static final Map<String, List<String>> KEYWORDS = Map.of(
            "Food", List.of("restaurant", "cafe", "coffee", "grocery", "groceries", "swiggy", "zomato", "food"),
            "Transport", List.of("uber", "ola", "metro", "fuel", "petrol", "diesel", "bus", "train", "rapido"),
            "Housing", List.of("rent", "maintenance", "electricity", "water bill", "internet", "broadband"),
            "Shopping", List.of("amazon", "flipkart", "shopping", "clothing", "mall"),
            "Health", List.of("pharmacy", "hospital", "doctor", "medical", "medicine", "clinic"),
            "Entertainment", List.of("movie", "netflix", "spotify", "concert", "game"),
            "Salary", List.of("salary", "payroll", "wages"),
            "Utilities", List.of("bill", "recharge", "phone", "mobile"));

    public String suggest(String description, String type) {
        if (description == null || description.isBlank()) {
            return "INCOME".equalsIgnoreCase(type) ? "Other income" : "Other";
        }
        String normalized = description.toLowerCase(Locale.ROOT);
        return KEYWORDS.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(normalized::contains))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("INCOME".equalsIgnoreCase(type) ? "Other income" : "Other");
    }
}
