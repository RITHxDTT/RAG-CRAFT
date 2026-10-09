package com.ragcraft.knowledge.api;

import com.ragcraft.common.security.CurrentUser;
import com.ragcraft.knowledge.api.KnowledgeApi.ActivityResponse;
import com.ragcraft.knowledge.api.KnowledgeApi.AvailabilityResponse;
import com.ragcraft.knowledge.api.KnowledgeApi.DocumentDetailResponse;
import com.ragcraft.knowledge.api.KnowledgeApi.DocumentResponse;
import com.ragcraft.knowledge.api.KnowledgeApi.PreviewResponse;
import com.ragcraft.knowledge.api.KnowledgeApi.UploadPolicy;
import com.ragcraft.knowledge.api.KnowledgeApi.WebsiteRequest;
import com.ragcraft.knowledge.domain.Document;
import com.ragcraft.knowledge.service.KnowledgeService;
import jakarta.validation.Valid;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Same paths as the FastAPI knowledge router, plus website sources and replace from the V2 frontend. */
@RestController
@RequestMapping("/api")
public class KnowledgeController {

    private final KnowledgeService service;
    private final CurrentUser currentUser;

    public KnowledgeController(KnowledgeService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/knowledge/config")
    public UploadPolicy config() { return service.policy(); }

    @GetMapping("/knowledge/recent")
    public List<ActivityResponse> recent() { return service.recent(currentUser.require()); }

    @GetMapping("/chatbots/{chatbotId}/documents")
    public List<DocumentResponse> list(@PathVariable UUID chatbotId) { return service.list(chatbotId, currentUser.require()); }

    @PostMapping(value = "/chatbots/{chatbotId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(@PathVariable UUID chatbotId, @RequestParam("file") MultipartFile file) {
        return service.upload(chatbotId, file, currentUser.require());
    }

    @PostMapping("/chatbots/{chatbotId}/documents/website")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse website(@PathVariable UUID chatbotId, @Valid @RequestBody WebsiteRequest request) {
        return service.website(chatbotId, request.name(), request.url(), currentUser.require());
    }

    @GetMapping("/chatbots/{chatbotId}/documents/{documentId}")
    public DocumentDetailResponse detail(@PathVariable UUID chatbotId, @PathVariable UUID documentId) {
        return service.detail(chatbotId, documentId, currentUser.require());
    }

    @GetMapping("/chatbots/{chatbotId}/documents/{documentId}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID chatbotId, @PathVariable UUID documentId) {
        var principal = currentUser.require();
        Path path = service.download(chatbotId, documentId, principal);
        Document document = service.documentFor(chatbotId, documentId, principal);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(document.getName()).build().toString())
                .body(new FileSystemResource(path));
    }

    @GetMapping("/chatbots/{chatbotId}/documents/{documentId}/availability")
    public AvailabilityResponse availability(@PathVariable UUID chatbotId, @PathVariable UUID documentId) {
        return service.availability(chatbotId, documentId, currentUser.require());
    }

    @GetMapping("/chatbots/{chatbotId}/documents/{documentId}/preview")
    public PreviewResponse preview(@PathVariable UUID chatbotId, @PathVariable UUID documentId, @RequestParam(defaultValue = "5") int limit) {
        return service.preview(chatbotId, documentId, limit, currentUser.require());
    }

    @PutMapping(value = "/chatbots/{chatbotId}/documents/{documentId}/replace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentResponse replace(@PathVariable UUID chatbotId, @PathVariable UUID documentId, @RequestParam("file") MultipartFile file) {
        return service.replace(chatbotId, documentId, file, currentUser.require());
    }

    @PostMapping("/chatbots/{chatbotId}/documents/{documentId}/retry")
    public DocumentResponse retry(@PathVariable UUID chatbotId, @PathVariable UUID documentId) {
        return service.requeue(chatbotId, documentId, currentUser.require());
    }

    @PostMapping("/chatbots/{chatbotId}/documents/{documentId}/reindex")
    public DocumentResponse reindex(@PathVariable UUID chatbotId, @PathVariable UUID documentId) {
        return service.requeue(chatbotId, documentId, currentUser.require());
    }

    @DeleteMapping("/chatbots/{chatbotId}/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID chatbotId, @PathVariable UUID documentId) {
        service.delete(chatbotId, documentId, currentUser.require());
    }
}
