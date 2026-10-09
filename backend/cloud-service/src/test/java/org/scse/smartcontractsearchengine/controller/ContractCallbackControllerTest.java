package org.scse.smartcontractsearchengine.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.scse.smartcontractsearchengine.config.ContractImportPolicy;
import org.scse.smartcontractsearchengine.service.ContractProcessService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContractCallbackControllerTest {
    private static final String TOKEN = "synthetic-import-test-token-123456";
    @TempDir Path temporary;
    private Path root;
    private ContractProcessService service;
    private MockMvc mvc;

    @BeforeEach
    void configure() throws Exception {
        root = Files.createDirectory(temporary.resolve("imports"));
        service = mock(ContractProcessService.class);
        mvc = mvc(TOKEN, root.toString());
    }

    @Test
    void rejectsAnonymousAndWrongTokenBeforeProcessingFiles() throws Exception {
        for (String token : List.of("", "wrong")) {
            request(token, "missing.parquet").andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
    }

    @Test
    void disabledConfigurationCannotBeBypassedWithUserIdentity() throws Exception {
        mvc = mvc("", "");
        request(TOKEN, "missing.parquet").andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }

    @Test
    void acceptsOnlyAuthorizedParquetInsideTheImportDirectory() throws Exception {
        Path file = Files.writeString(root.resolve("demo.parquet"), "synthetic fixture").toRealPath();
        when(service.processParquetFile(file.toString(), 3L)).thenReturn(List.of());
        request(TOKEN, "demo.parquet").andExpect(status().isOk());
        verify(service).processParquetFile(file.toString(), 3L);
        verifyNoMoreInteractions(service);
    }

    @Test
    void rejectsTraversalAndSymlinkEscapes() throws Exception {
        Path outside = Files.writeString(temporary.resolve("outside.parquet"), "synthetic fixture");
        request(TOKEN, "../outside.parquet").andExpect(status().isBadRequest());
        Files.createSymbolicLink(root.resolve("link.parquet"), outside);
        request(TOKEN, "link.parquet").andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsOtherFilesMissingFilesAndRemoteUris() throws Exception {
        Files.writeString(root.resolve("notes.txt"), "synthetic fixture");
        for (String path : List.of("notes.txt", "missing.parquet", "https://example.invalid/data.parquet")) {
            request(TOKEN, path).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    private MockMvc mvc(String token, String allowedRoot) {
        return MockMvcBuilders.standaloneSetup(new ContractCallbackController(service,
                new ContractImportPolicy(token, allowedRoot))).build();
    }

    private org.springframework.test.web.servlet.ResultActions request(String token, String path) throws Exception {
        return mvc.perform(post("/api/contracts/process")
                .header(ContractImportPolicy.TOKEN_HEADER, token).header("X-User-Id", "999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                        java.util.Map.of("filePath", path, "groupId", 3))));
    }
}
