package com.junaid.loan.api;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Runs the full HTTP flow in search-only mode (no API key), so it never calls a real model. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CaseApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private static MockMultipartFile sample(String name) throws Exception {
        return new MockMultipartFile("files", name, "text/plain", Files.readAllBytes(Path.of("sample-docs", name)));
    }

    @Test
    void analyzesACaseAndAnswersFromTheDocuments() throws Exception {
        String body = mvc.perform(multipart("/api/cases")
                        .file(sample("james-carter-application.txt"))
                        .file(sample("james-carter-paystub.txt"))
                        .file(sample("james-carter-bank-statement.txt")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicant.applicantName").value("James Carter"))
                .andExpect(jsonPath("$.risk.level").value("HIGH"))
                .andExpect(jsonPath("$.risk.flags[*].code", hasItem("INCOME_MISMATCH")))
                .andExpect(jsonPath("$.documents.length()").value(3))
                .andExpect(jsonPath("$.llmUsed").value(false))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("id").asText();

        mvc.perform(get("/api/cases/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchableChunks").value(greaterThan(0)));

        mvc.perform(post("/api/cases/{id}/questions", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Where is the down payment coming from?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("search-only"))
                .andExpect(jsonPath("$.citations[0].documentName").value("james-carter-application.txt"));
    }

    @Test
    void readsPdfUploads() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("files", "application.pdf", "application/pdf",
                pdfWithLines("Applicant Name: Dana Reyes", "Annual Income: $72,000", "Credit Score: 701"));

        mvc.perform(multipart("/api/cases").file(pdf))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicant.applicantName").value("Dana Reyes"))
                .andExpect(jsonPath("$.applicant.creditScore").value(701))
                .andExpect(jsonPath("$.documents[0].pages").value(1));
    }

    @Test
    void rejectsUnsupportedFiles() throws Exception {
        mvc.perform(multipart("/api/cases").file(new MockMultipartFile("files", "photo.png", "image/png", new byte[] {1, 2})))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownCaseIsNotFound() throws Exception {
        mvc.perform(get("/api/cases/{id}", "nope")).andExpect(status().isNotFound());
    }

    @Test
    void blankQuestionIsRejected() throws Exception {
        mvc.perform(post("/api/cases/{id}/questions", "any").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusShowsSearchOnlyMode() throws Exception {
        mvc.perform(get("/api/status")).andExpect(jsonPath("$.llmEnabled").value(false));
    }

    private static byte[] pdfWithLines(String... lines) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.setLeading(16);
                cs.newLineAtOffset(72, 700);
                for (String line : lines) {
                    cs.showText(line);
                    cs.newLine();
                }
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
