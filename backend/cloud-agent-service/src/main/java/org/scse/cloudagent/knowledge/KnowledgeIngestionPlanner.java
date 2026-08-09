package org.scse.cloudagent.knowledge;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeIngestionPlanner {
    private final SoliditySourceRouter sourceRouter;
    private final SolidityChunker chunker;
    private final SolidityAstClient astClient;
    private final SolidityAstChunker astChunker;

    public KnowledgeIngestionPlanner(SoliditySourceRouter sourceRouter, SolidityChunker chunker,
                                     SolidityAstClient astClient, SolidityAstChunker astChunker) {
        this.sourceRouter = sourceRouter;
        this.chunker = chunker;
        this.astClient = astClient;
        this.astChunker = astChunker;
    }

    public KnowledgeIngestionPlan dryRun(String sourceRef, Long existingContractId, String sourceCode) {
        SourceManifest manifest = sourceRouter.route(sourceRef, existingContractId, sourceCode);
        List<KnowledgeChunk> chunks = new ArrayList<>();
        if (manifest.parserStatus() == ParserStatus.PARSED) {
            for (NormalizedSourceFile file : manifest.files()) {
                chunks.addAll(chunker.chunk(file));
            }
        }
        return new KnowledgeIngestionPlan(manifest, chunks, "solidity-lexical-v1", null,
                manifest.diagnostics());
    }

    public KnowledgeIngestionPlan planWithAst(String sourceRef, Long existingContractId, String sourceCode) {
        SourceManifest manifest = sourceRouter.route(sourceRef, existingContractId, sourceCode);
        if (manifest.parserStatus() != ParserStatus.PARSED) {
            return new KnowledgeIngestionPlan(manifest, List.of(), "none", null,
                    manifest.diagnostics());
        }
        AstParseResult ast = astClient.parse(manifest.files());
        if (ast.success()) {
            try {
                return new KnowledgeIngestionPlan(manifest, astChunker.chunk(ast, manifest.files()), ast.parser(),
                        ast.compilerVersion(), ast.diagnostics());
            } catch (IllegalArgumentException invalidMapping) {
                List<String> diagnostics = new ArrayList<>(ast.diagnostics());
                diagnostics.add("AST_MAPPING_INVALID");
                return lexicalFallback(manifest, ast.compilerVersion(), diagnostics);
            }
        }
        return lexicalFallback(manifest, ast.compilerVersion(), ast.diagnostics());
    }

    private KnowledgeIngestionPlan lexicalFallback(SourceManifest manifest, String compilerVersion,
                                                    List<String> diagnostics) {
        List<KnowledgeChunk> fallback = new ArrayList<>();
        for (NormalizedSourceFile file : manifest.files()) fallback.addAll(chunker.chunk(file));
        return new KnowledgeIngestionPlan(manifest, fallback, "solidity-lexical-fallback-v1",
                compilerVersion, diagnostics);
    }
}
