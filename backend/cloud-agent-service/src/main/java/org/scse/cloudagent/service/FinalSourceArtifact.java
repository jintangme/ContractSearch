package org.scse.cloudagent.service;

import org.scse.cloudagent.tool.InvalidToolArgumentsException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Accept the legacy single-source answer shape without rewriting its bytes. */
final class FinalSourceArtifact {
    private static final Pattern BLOCK = Pattern.compile("(?is)\\x60{3}(?:solidity|sol)[ \\t]*\\r?\\n(.*?)\\x60{3}");
    private static final Pattern NON_CODE = Pattern.compile("(?s)//[^\\r\\n]*|/\\*.*?\\*/|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'");
    private static final Pattern UNIT = Pattern.compile("\\b(?:abstract\\s+)?(?:contract|library|interface)\\s+[A-Za-z_$][A-Za-z0-9_$]*(?:\\s|\\{)");

    static String singleCompleteSource(String answer) {
        if (answer == null) return null;
        List<String> sources = new ArrayList<>();
        var blocks = BLOCK.matcher(answer);
        while (blocks.find()) {
            String source = blocks.group(1);
            if (isCompleteUnit(source)) sources.add(source);
        }
        if (sources.size() > 1) throw new InvalidToolArgumentsException(
                "Ambiguous source delivery: use top-level sourceCode for exactly one final source, or omit complete source blocks and select candidateId");
        if (sources.isEmpty()) return null;
        if (sources.get(0).length() > 24000) throw new InvalidToolArgumentsException("Final source exceeds 24000 characters");
        return sources.get(0);
    }

    static boolean isCompleteUnit(String source) {
        String code = NON_CODE.matcher(source).replaceAll(" ");
        if (!UNIT.matcher(code).find()) return false;
        int depth = 0; boolean opened = false;
        for (char c : code.toCharArray()) {
            if (c == '{') { depth++; opened = true; }
            if (c == '}' && --depth < 0) return false;
        }
        return opened && depth == 0;
    }
}
