package org.scse.cloudagent.tool;

import java.util.*;
import java.util.regex.Pattern;

/** Lexical import hints only. A catalog name match is not deployed-code identity. */
public final class SourceDependencies {
    private SourceDependencies() { }
    public record ImportRef(String path, String symbol) { }
    private static final Pattern TOKEN = Pattern.compile("//[^\\n]*|/\\*[\\s\\S]*?\\*/|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|\\bimport\\b|;");
    public static List<ImportRef> imports(String source) {
        var matcher = TOKEN.matcher(source); boolean importing = false; String path = null;
        Map<String, ImportRef> imports = new LinkedHashMap<>();
        while (matcher.find()) {
            String token = matcher.group();
            if (token.startsWith("//") || token.startsWith("/*")) continue;
            if (token.equals("import")) { importing = true; path = null; }
            else if (token.equals(";")) {
                if (importing && path != null && path.endsWith(".sol")) {
                    String name = path.substring(path.lastIndexOf('/') + 1, path.length() - 4);
                    if (name.matches("[A-Za-z_][A-Za-z0-9_]*")) imports.putIfAbsent(path, new ImportRef(path, name));
                }
                importing = false; path = null;
            } else if (importing) path = token.substring(1, token.length() - 1);
        }
        return List.copyOf(imports.values());
    }
}
