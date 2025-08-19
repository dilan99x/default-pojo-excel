package com.dbs.plugin;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

public final class JsonNav {

    /** Resolve a value from root using either JSON Pointer (/a/b/0) or dot/bracket (a.b[0].c). */
    public static JsonNode resolve(JsonNode root, String path) {
        if (root == null || path == null || path.isEmpty()) return MissingNode.getInstance();

        // 1) JSON Pointer (RFC 6901)
        if (path.startsWith("/")) {
            // If any key literally contains '/' or '~', it must be escaped as ~1 and ~0 respectively.
            return root.at(JsonPointer.compile(path));
        }

        // 2) Dot/bracket notation
        JsonNode cur = root;
        for (String token : path.split("\\.")) {
            if (cur.isMissingNode() || cur.isNull()) return MissingNode.getInstance();
            if (token.isEmpty()) continue;

            // handle repeated [idx] parts: e.g. "items[2][1]"
            int firstBracket = token.indexOf('[');
            if (firstBracket < 0) {
                // simple field
                cur = cur.path(unescapeDot(token));
                continue;
            }

            // base field then indexes
            String base = token.substring(0, firstBracket);
            if (!base.isEmpty()) cur = cur.path(unescapeDot(base));

            int i = firstBracket;
            while (i >= 0 && !cur.isMissingNode()) {
                int close = token.indexOf(']', i);
                if (close < 0) return MissingNode.getInstance(); // malformed
                String idxStr = token.substring(i + 1, close).trim();
                int idx;
                try {
                    idx = Integer.parseInt(idxStr);
                } catch (NumberFormatException e) {
                    return MissingNode.getInstance();
                }
                cur = (cur.isArray() && idx >= 0 && idx < cur.size()) ? cur.path(idx) : MissingNode.getInstance();
                i = token.indexOf('[', close + 1);
            }
        }
        return cur == null ? MissingNode.getInstance() : cur;
    }

    /** Convenience: get text or null if missing/null. */
    public static String textOrNull(JsonNode root, String path) {
        JsonNode n = resolve(root, path);
        return (n.isMissingNode() || n.isNull()) ? null : n.asText();
    }

    // Allow escaping a literal '.' in field names as '\.' in dot-notation
    private static String unescapeDot(String s) {
        return s.replace("\\.", ".");
    }

    private JsonNav() {}
}

