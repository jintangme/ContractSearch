package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.LongNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeSet;

public final class SourceHashes {
    private SourceHashes() { }
    public static String sha256(String source) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            TreeSet<String> keys = new TreeSet<>(); node.fieldNames().forEachRemaining(keys::add);
            keys.forEach(k -> result.set(k, canonical(node.get(k)))); return result;
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            node.forEach(v -> result.add(canonical(v))); return result;
        }
        // The runtime accepts only safe integral JSON numbers. Match JSON.stringify
        // for equivalent forms such as 0, 0.0 and -0.0 before hashing the plan.
        if (node.isNumber() && node.canConvertToLong() && node.decimalValue().stripTrailingZeros().scale() <= 0)
            return LongNode.valueOf(node.longValue());
        return node.deepCopy();
    }
    public static String planHash(JsonNode plan) { return sha256(canonical(plan).toString()); }
}
