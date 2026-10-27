package com.junaid.loan.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls loan fields out of document text with labelled patterns ("Loan Amount: $250,000").
 * Rule-based extraction is fast, free and explainable: every value comes with the line it was read from.
 * The service only asks the LLM for fields these rules couldn't find.
 */
public final class FieldExtractor {

    /** Extracted values plus, for each field, the line of text it was read from. */
    public record Extraction(LoanFields fields, Map<String, String> evidence) {
        public Extraction {
            evidence = Map.copyOf(evidence);
        }
    }

    private static final String MONEY = "\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)";
    private static final String SEP = "\\s*[:\\-]?\\s*";
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.MULTILINE;

    private static final List<Pattern> NAME = List.of(
            Pattern.compile("(?:applicant(?:'s)?\\s+name|borrower(?:'s)?\\s+name|employee\\s+name|account\\s+holder)[ \\t]*:[ \\t]*([A-Za-z][A-Za-z.'\\-]*(?:[ ]+[A-Za-z][A-Za-z.'\\-]*){1,3})[ \\t]*$", FLAGS),
            Pattern.compile("^[ \\t]*(?:borrower|applicant|employee|name)[ \\t]*:[ \\t]*([A-Za-z][A-Za-z.'\\-]*(?:[ ]+[A-Za-z][A-Za-z.'\\-]*){1,3})[ \\t]*$", FLAGS));
    private static final List<Pattern> EMPLOYER = List.of(
            Pattern.compile("^[ \\t]*(?:employer(?:\\s+name)?|employed\\s+by|company\\s+name|company)[ \\t]*:[ \\t]*(.+?)\\s*$", FLAGS));
    private static final List<Pattern> ANNUAL_INCOME = List.of(
            Pattern.compile("(?:gross\\s+annual\\s+income|annual\\s+(?:gross\\s+)?income|annual\\s+salary|yearly\\s+income|base\\s+salary)" + SEP + MONEY, FLAGS));
    private static final List<Pattern> MONTHLY_INCOME = List.of(
            Pattern.compile("(?:gross\\s+monthly\\s+income|monthly\\s+(?:gross\\s+)?income|monthly\\s+salary)" + SEP + MONEY, FLAGS));
    private static final Pattern GROSS_PAY = Pattern.compile("^[ \\t]*(?:current\\s+)?gross\\s+pay(?:\\s+this\\s+period)?" + SEP + MONEY, FLAGS);
    private static final Pattern PAY_FREQUENCY = Pattern.compile("pay\\s+(?:frequency|period\\s+type|schedule)\\s*:\\s*(weekly|bi-?weekly|semi-?monthly|monthly)", FLAGS);
    private static final List<Pattern> MONTHLY_DEBT = List.of(
            Pattern.compile("(?:total\\s+monthly\\s+debts?(?:\\s+payments?)?|monthly\\s+debts?(?:\\s+payments?)?|monthly\\s+obligations)" + SEP + MONEY, FLAGS));
    private static final List<Pattern> LOAN_AMOUNT = List.of(
            Pattern.compile("(?:requested\\s+loan\\s+amount|loan\\s+amount(?:\\s+requested)?|amount\\s+requested)" + SEP + MONEY, FLAGS));
    private static final List<Pattern> PROPERTY_VALUE = List.of(
            Pattern.compile("(?:appraised\\s+value|property\\s+value|estimated\\s+(?:property\\s+)?value|purchase\\s+price)" + SEP + MONEY, FLAGS));
    private static final List<Pattern> CREDIT_SCORE = List.of(
            Pattern.compile("(?:credit\\s+score|fico(?:\\s+score)?)" + SEP + "([0-9]{3})\\b", FLAGS));
    private static final List<Pattern> INTEREST_RATE = List.of(
            Pattern.compile("(?:\\binterest\\s+rate|\\bnote\\s+rate|\\bapr\\b)" + SEP + "([0-9]{1,2}(?:\\.[0-9]{1,3})?)\\s*%", FLAGS));
    private static final Pattern TERM = Pattern.compile("(?:loan\\s+term|term\\s+of\\s+loan|term)\\s*:\\s*([0-9]{1,3})\\s*(years?|yrs?|months?|mos?)\\b", FLAGS);

    public Extraction extract(String text) {
        Map<String, String> evidence = new LinkedHashMap<>();
        String name = findText(NAME, text, "applicantName", evidence);
        String employer = findText(EMPLOYER, text, "employer", evidence);
        BigDecimal annual = findMoney(ANNUAL_INCOME, text, "annualIncome", evidence);
        BigDecimal monthly = findMoney(MONTHLY_INCOME, text, "monthlyIncome", evidence);
        if (monthly == null) {
            monthly = monthlyFromPayStub(text, evidence);
        }
        BigDecimal debt = findMoney(MONTHLY_DEBT, text, "monthlyDebtPayments", evidence);
        BigDecimal loan = findMoney(LOAN_AMOUNT, text, "loanAmount", evidence);
        BigDecimal property = findMoney(PROPERTY_VALUE, text, "propertyValue", evidence);
        Integer score = findCreditScore(text, evidence);
        BigDecimal rate = findMoney(INTEREST_RATE, text, "interestRate", evidence);
        Integer term = findTerm(text, evidence);
        return new Extraction(new LoanFields(name, employer, annual, monthly, debt, loan, property, score, rate, term), evidence);
    }

    private static String findText(List<Pattern> patterns, String text, String field, Map<String, String> evidence) {
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                String value = m.group(1).trim();
                if (!value.isEmpty()) {
                    evidence.put(field, lineOf(text, m.start()));
                    return value;
                }
            }
        }
        return null;
    }

    private static BigDecimal findMoney(List<Pattern> patterns, String text, String field, Map<String, String> evidence) {
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                evidence.put(field, lineOf(text, m.start()));
                return parseNumber(m.group(1));
            }
        }
        return null;
    }

    private static BigDecimal monthlyFromPayStub(String text, Map<String, String> evidence) {
        Matcher pay = GROSS_PAY.matcher(text);
        Matcher freq = PAY_FREQUENCY.matcher(text);
        if (!pay.find() || !freq.find()) {
            return null;
        }
        BigDecimal gross = parseNumber(pay.group(1));
        String f = freq.group(1).toLowerCase(Locale.ROOT).replace("-", "");
        BigDecimal monthly = switch (f) {
            case "weekly" -> gross.multiply(BigDecimal.valueOf(52)).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            case "biweekly" -> gross.multiply(BigDecimal.valueOf(26)).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            case "semimonthly" -> gross.multiply(BigDecimal.valueOf(2)).setScale(2, RoundingMode.HALF_UP);
            default -> gross.setScale(2, RoundingMode.HALF_UP);
        };
        evidence.put("monthlyIncome", lineOf(text, pay.start()) + " | " + lineOf(text, freq.start()));
        return monthly;
    }

    private static Integer findCreditScore(String text, Map<String, String> evidence) {
        for (Pattern p : CREDIT_SCORE) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                int score = Integer.parseInt(m.group(1));
                if (score >= 300 && score <= 850) {
                    evidence.put("creditScore", lineOf(text, m.start()));
                    return score;
                }
            }
        }
        return null;
    }

    private static Integer findTerm(String text, Map<String, String> evidence) {
        Matcher m = TERM.matcher(text);
        if (!m.find()) {
            return null;
        }
        int n = Integer.parseInt(m.group(1));
        evidence.put("loanTermMonths", lineOf(text, m.start()));
        return m.group(2).toLowerCase(Locale.ROOT).startsWith("y") ? n * 12 : n;
    }

    static BigDecimal parseNumber(String raw) {
        return new BigDecimal(raw.replace(",", "").trim());
    }

    private static String lineOf(String text, int index) {
        int start = text.lastIndexOf('\n', Math.max(0, index - 1)) + 1;
        if (index == 0) {
            start = 0;
        }
        int end = text.indexOf('\n', index);
        return text.substring(start, end < 0 ? text.length() : end).trim();
    }
}
