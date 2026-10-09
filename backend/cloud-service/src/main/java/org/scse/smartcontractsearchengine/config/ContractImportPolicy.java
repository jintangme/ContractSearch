package org.scse.smartcontractsearchengine.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;

/** Authentication and filesystem boundary for the offline discovery callback. */
@Component
public class ContractImportPolicy {
    public static final String TOKEN_HEADER = "X-Contract-Import-Token";
    private final String token;
    private final String root;

    public ContractImportPolicy(@Value("${contract.import.token:}") String token,
                                @Value("${contract.import.root:}") String root) {
        this.token = token;
        this.root = root;
    }

    public Path authorizeAndResolve(String suppliedToken, String filePath) {
        if (token.length() < 24 || root.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract import is disabled");
        }
        if (suppliedToken == null || !MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal import token required");
        }
        try {
            if (filePath == null || filePath.isBlank()) throw new IllegalArgumentException();
            Path allowedRoot = Path.of(root).toRealPath();
            Path requested = Path.of(filePath);
            if (!requested.isAbsolute()) requested = allowedRoot.resolve(requested);
            Path realFile = requested.toRealPath();
            if (!Files.isDirectory(allowedRoot) || !realFile.startsWith(allowedRoot)
                    || !Files.isRegularFile(realFile)
                    || !realFile.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".parquet")) {
                throw new IllegalArgumentException();
            }
            return realFile;
        } catch (IOException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Expected a readable Parquet file within the configured import directory");
        }
    }
}
