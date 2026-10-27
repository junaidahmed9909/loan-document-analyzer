package com.junaid.loan.api;

import com.junaid.loan.llm.LlmClient;
import com.junaid.loan.llm.LlmProperties;
import com.junaid.loan.service.AnalysisService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class CaseController {

    public record QuestionRequest(@NotBlank @Size(max = 500) String question) {}

    static final int MAX_FILES = 10;

    private final AnalysisService service;
    private final LlmClient llm;
    private final LlmProperties llmProps;

    public CaseController(AnalysisService service, LlmClient llm, LlmProperties llmProps) {
        this.service = service;
        this.llm = llm;
        this.llmProps = llmProps;
    }

    /** Upload one applicant's documents (application, pay stubs, bank statements...) and get the analysis back. */
    @PostMapping(path = "/cases", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Views.CaseView createCase(@RequestPart("files") List<MultipartFile> files) throws IOException {
        if (files.size() > MAX_FILES) {
            throw new IllegalArgumentException("Upload at most " + MAX_FILES + " files per case");
        }
        List<AnalysisService.Upload> uploads = new ArrayList<>();
        for (MultipartFile f : files) {
            if (!f.isEmpty()) {
                uploads.add(new AnalysisService.Upload(f.getOriginalFilename(), f.getBytes()));
            }
        }
        return Views.CaseView.of(service.analyze(uploads));
    }

    @GetMapping("/cases/{id}")
    public Views.CaseView getCase(@PathVariable String id) {
        return Views.CaseView.of(service.get(id));
    }

    @PostMapping("/cases/{id}/questions")
    public Views.AnswerView ask(@PathVariable String id, @Valid @RequestBody QuestionRequest request) {
        return service.ask(id, request.question());
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("llmEnabled", llm.isAvailable(), "model", llm.isAvailable() ? llmProps.model() : "none");
    }
}
