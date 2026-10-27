package com.junaid.loan.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds deposits in bank statement text. Lines look like:
 * {@code 2026-01-15  DIRECT DEPOSIT ACME PAYROLL   4,250.00}
 * Payroll deposits are expected; other big deposits are what underwriters ask about.
 */
public final class BankStatementAnalyzer {

    public record Deposit(String date, String description, BigDecimal amount, boolean payroll) {}

    private static final Pattern LINE = Pattern.compile(
            "^[ \\t]*(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}/\\d{1,2}/\\d{4})\\s+(.+?)\\s+(\\+?-?\\$?[0-9][0-9,]*\\.[0-9]{2})\\s*$",
            Pattern.MULTILINE);
    private static final Pattern DEPOSIT_WORDS = Pattern.compile("deposit|transfer in|incoming|credit|zelle from|ach in", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAYROLL_WORDS = Pattern.compile("payroll|salary|direct dep", Pattern.CASE_INSENSITIVE);

    public List<Deposit> deposits(String text, String employer) {
        List<Deposit> out = new ArrayList<>();
        String employerKey = employer == null ? null : employer.toLowerCase(Locale.ROOT);
        Matcher m = LINE.matcher(text);
        while (m.find()) {
            String desc = m.group(2).trim();
            String rawAmount = m.group(3).replace("$", "").replace("+", "");
            if (rawAmount.startsWith("-") || !DEPOSIT_WORDS.matcher(desc).find()) {
                continue;
            }
            BigDecimal amount = FieldExtractor.parseNumber(rawAmount);
            boolean payroll = PAYROLL_WORDS.matcher(desc).find()
                    || (employerKey != null && desc.toLowerCase(Locale.ROOT).contains(employerKey));
            out.add(new Deposit(m.group(1), desc, amount, payroll));
        }
        return out;
    }
}
