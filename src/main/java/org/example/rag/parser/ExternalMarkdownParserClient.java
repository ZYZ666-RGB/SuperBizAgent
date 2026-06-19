package org.example.rag.parser;

import org.example.rag.config.RagProperties;
import org.example.rag.model.ParsedDocument;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Order(20)
@Component
public class ExternalMarkdownParserClient implements DocumentParser {

    private final RagProperties ragProperties;
    private final FileTypeDetector fileTypeDetector;
    private final RestTemplate restTemplate;

    public ExternalMarkdownParserClient(RagProperties ragProperties, FileTypeDetector fileTypeDetector) {
        this.ragProperties = ragProperties;
        this.fileTypeDetector = fileTypeDetector;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = Math.max(1, ragProperties.getParser().getTimeoutSeconds()) * 1000;
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @Override
    public boolean supports(String fileName, String contentType) {
        return "external".equalsIgnoreCase(ragProperties.getParser().getMode())
                && fileTypeDetector.isRichDocument(fileName);
    }

    @Override
    public ParsedDocument parse(Path filePath, String namespace) {
        String fileName = filePath.getFileName().toString();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            headers.set("X-Parser-Timeout-Seconds", String.valueOf(ragProperties.getParser().getTimeoutSeconds()));

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(filePath));

            ResponseEntity<ExternalParseResponse> response = restTemplate.postForEntity(
                    ragProperties.getParser().getExternalUrl(),
                    new HttpEntity<>(body, headers),
                    ExternalParseResponse.class);

            ExternalParseResponse parsed = response.getBody();
            if (parsed == null || !parsed.success || parsed.markdown == null || parsed.markdown.isBlank()) {
                throw new IllegalStateException("External parser returned empty markdown");
            }

            ParsedDocument document = new ParsedDocument();
            document.setFileName(fileName);
            document.setFileType(fileTypeDetector.extension(fileName));
            document.setContentType(fileTypeDetector.contentType(filePath));
            document.setNamespace(namespace);
            document.setSourcePath(filePath);
            document.setMarkdownContent(parsed.markdown);
            document.setPlainText(parsed.markdown);
            document.setParserName((String) parsed.metadata.getOrDefault("parser", "external-markdown"));
            document.setMetadata(parsed.metadata);
            return document;
        } catch (Exception e) {
            throw new IllegalStateException("External markdown parser failed within "
                    + Duration.ofSeconds(ragProperties.getParser().getTimeoutSeconds()) + ": " + fileName, e);
        }
    }

    public static class ExternalParseResponse {
        public boolean success;
        public String markdown;
        public Map<String, Object> metadata = new LinkedHashMap<>();
    }
}
