package com.junaid.loan.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/** Turns an uploaded file into page texts. PDFs are read page by page; text files split on form feeds. */
@Component
public class DocumentTextExtractor {

    static final int MAX_PAGES = 200;

    public List<String> extractPages(String fileName, byte[] content) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) {
            return pdfPages(fileName, content);
        }
        if (name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".csv")) {
            String text = new String(content, StandardCharsets.UTF_8);
            return Arrays.asList(text.split("\f"));
        }
        throw new IllegalArgumentException("Unsupported file type for '" + fileName + "'. Upload PDF, TXT, MD or CSV files.");
    }

    private static List<String> pdfPages(String fileName, byte[] content) {
        try (PDDocument doc = Loader.loadPDF(content)) {
            int count = doc.getNumberOfPages();
            if (count > MAX_PAGES) {
                throw new IllegalArgumentException("'" + fileName + "' has " + count + " pages; the limit is " + MAX_PAGES);
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<String> pages = new ArrayList<>(count);
            for (int p = 1; p <= count; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                pages.add(stripper.getText(doc));
            }
            return pages;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read '" + fileName + "' as a PDF: " + e.getMessage(), e);
        }
    }
}
