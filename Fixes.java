private void handleArrayProjections(JsonNode sourceNode,
                                    FieldMapping field,
                                    JsonNode targetNode,
                                    MappingConfig config) {
    handleArrayProjectionsInternal(
        sourceNode,
        field,
        field.getSourcePointer(),
        field.getTargetPointer(),
        field.getDependencies(),
        targetNode,
        config
    );
}



// Recursive worker — passes around local pointer strings & deps (NO mutation)
private void handleArrayProjectionsInternal(JsonNode sourceNode,
                                            FieldMapping field,
                                            String curSourcePtr,
                                            String curTargetPtr,
                                            Map<String, String> curDependencies,
                                            JsonNode targetNode,
                                            MappingConfig config) {
    if (sourceNode == null || sourceNode.isNull()) return;

    long sCount = curSourcePtr == null ? 0 : curSourcePtr.chars().filter(c -> c == '*').count();
    long tCount = curTargetPtr == null ? 0 : curTargetPtr.chars().filter(c -> c == '*').count();
    if (sCount != tCount) {
        throw new TransformationException(String.format(
            "Failed on field mapping: %s -> %s, error: source and target must have same number of levels of dynamic arrays (*)",
            curSourcePtr, curTargetPtr));
    }

    boolean srcStartsWithArray = curSourcePtr != null && curSourcePtr.startsWith("/*");
    boolean tgtStartsWithArray = curTargetPtr != null && curTargetPtr.startsWith("/*");
    boolean srcIsDynamic      = curSourcePtr != null && curSourcePtr.contains("*");

    if (!(srcStartsWithArray || sourceNode.isArray() || srcIsDynamic)) return;

    // locate the array we’ll iterate
    String nextSourcePtr;
    JsonNode sourceArrayNode;
    if (sourceNode.isArray()) {
        sourceArrayNode = sourceNode;
        nextSourcePtr   = srcStartsWithArray ? curSourcePtr.substring(2) : curSourcePtr;
    } else {
        int starIdx = curSourcePtr.indexOf("/*");
        String toArray = (starIdx >= 0) ? curSourcePtr.substring(0, starIdx) : curSourcePtr;
        sourceArrayNode = (toArray == null || toArray.isEmpty())
                ? sourceNode
                : getValueFromPointer(toArray, sourceNode);
        nextSourcePtr = (starIdx >= 0) ? curSourcePtr.substring(starIdx + 2) : curSourcePtr;
    }
    if (sourceArrayNode == null || !sourceArrayNode.isArray() || sourceArrayNode.size() == 0) return;

    String nextTargetBase = curTargetPtr;
    if (tgtStartsWithArray && nextTargetBase != null && nextTargetBase.startsWith("/*")) {
        nextTargetBase = nextTargetBase.substring(2);
    }

    for (int i = 0; i < sourceArrayNode.size(); i++) {
        JsonNode indexedSourceNode = sourceArrayNode.get(i);
        if (indexedSourceNode == null) continue;

        // index-scoped deps (no mutation to FieldMapping)
        Map<String, String> idxDeps = remapDependenciesLocal(curDependencies, i);

        // resolve this index’s target pointer
        String idxTargetPtr = curTargetPtr == null ? "" : curTargetPtr;
        if (!tgtStartsWithArray) {
            idxTargetPtr = ASTERIK_PATTERN.matcher(idxTargetPtr).replaceFirst(String.valueOf(i));
        } else {
            idxTargetPtr = nextTargetBase;
        }
        boolean targetIsDynamic = idxTargetPtr != null && idxTargetPtr.contains("*");

        boolean noMoreSourceStars = nextSourcePtr == null || !nextSourcePtr.contains("*");

        if (noMoreSourceStars) {
            JsonNode value = (nextSourcePtr == null || nextSourcePtr.isEmpty())
                    ? indexedSourceNode
                    : getValueFromPointer(nextSourcePtr, indexedSourceNode);

            if (value == null || value.isNull() || value.isMissingNode()) continue;
            if (!dependenciesMatchLocal(idxDeps, config)) continue;

            if (field.getOperationType() != null) {
                value = customOperationHandling(value, field);
            }
            if (!io.micrometer.common.util.StringUtils.isBlank(field.getTargetDataType())) {
                value = convertDataType(value, field.getTargetDataType(), field.getTargetPointer());
            }

            if (!targetIsDynamic) {
                if (!targetNode.isArray()) {
                    setValueAtPointer(idxTargetPtr, value, targetNode);
                } else {
                    ArrayNode targetArrayNode = (ArrayNode) targetNode;
                    ensureArraySize(targetArrayNode, i);
                    JsonNode row = targetArrayNode.get(i);
                    if (row == null || row.isNull() || row.isMissingNode()) row = objectMapper.createObjectNode();
                    setValueAtPointer(idxTargetPtr, value, row);
                    targetArrayNode.set(i, row);
                }
                continue;
            }
        }

        // recurse deeper
        int srcStarIdx2 = (nextSourcePtr == null) ? -1 : nextSourcePtr.indexOf("/*");
        String srcPrefixToNested = (srcStarIdx2 >= 0) ? nextSourcePtr.substring(0, srcStarIdx2) : "";
        String deeperSourcePtr   = (srcStarIdx2 >= 0) ? nextSourcePtr.substring(srcStarIdx2 + 2) : "";

        JsonNode nestedSourceArray = srcPrefixToNested.isEmpty()
                ? indexedSourceNode
                : getValueFromPointer(srcPrefixToNested, indexedSourceNode);
        if (nestedSourceArray == null ||
            nestedSourceArray.isNull() ||
            (nestedSourceArray.isArray() && nestedSourceArray.size() == 0)) {
            continue;
        }

        int tgtStarIdx = (idxTargetPtr == null) ? -1 : idxTargetPtr.indexOf("/*");
        String tgtPrefixToNested = (tgtStarIdx >= 0) ? idxTargetPtr.substring(0, tgtStarIdx) : idxTargetPtr;
        String deeperTargetPtr   = (tgtStarIdx >= 0) ? idxTargetPtr.substring(tgtStarIdx + 2) : "";

        JsonNode nestedTargetArray;
        if (!targetNode.isArray()) {
            nestedTargetArray = (tgtPrefixToNested == null || tgtPrefixToNested.isEmpty())
                    ? targetNode
                    : targetNode.at(tgtPrefixToNested);
        } else {
            ArrayNode targetArrayNode = (ArrayNode) targetNode;
            ensureArraySize(targetArrayNode, i);
            JsonNode row = targetArrayNode.get(i);
            if (row == null || row.isMissingNode()) {
                row = objectMapper.createObjectNode();
                targetArrayNode.set(i, row);
            }
            nestedTargetArray = (tgtPrefixToNested == null || tgtPrefixToNested.isEmpty())
                    ? row
                    : row.at(tgtPrefixToNested);
        }
        if (nestedTargetArray == null || nestedTargetArray.isNull() || nestedTargetArray.isMissingNode()) {
            nestedTargetArray = objectMapper.createArrayNode();
        }

        handleArrayProjectionsInternal(
            nestedSourceArray,
            field,
            deeperSourcePtr,
            deeperTargetPtr,
            idxDeps,
            nestedTargetArray,
            config
        );

        if (!targetNode.isArray()) {
            setValueAtPointer(tgtPrefixToNested, nestedTargetArray, targetNode);
        } else {
            ArrayNode targetArrayNode = (ArrayNode) targetNode;
            ensureArraySize(targetArrayNode, i);
            JsonNode row = targetArrayNode.get(i);
            if (row == null || row.isNull() || row.isMissingNode()) row = objectMapper.createObjectNode();
            setValueAtPointer(tgtPrefixToNested, nestedTargetArray, row);
            targetArrayNode.set(i, row);
        }
    }
}



// local-only: replace '*' with index; NO mutation to original map
private Map<String, String> remapDependenciesLocal(Map<String, String> deps, int idx) {
    if (deps == null || deps.isEmpty()) return deps;
    Map<String, String> out = new HashMap<>(deps.size());
    for (Map.Entry<String, String> e : deps.entrySet()) {
        String k = e.getKey();
        if (k != null && k.contains("*")) {
            k = ASTERIK_PATTERN.matcher(k).replaceFirst(String.valueOf(idx));
        }
        out.put(k, e.getValue());
    }
    return out;
}

// local-only: validate deps against mappingConfig.dependentValues
private boolean dependenciesMatchLocal(Map<String, String> deps, MappingConfig config) {
    if (deps == null || deps.isEmpty()) return true;
    Map<String, String> actual = config.getDependentValues();
    if (actual == null) return false;
    for (Map.Entry<String, String> e : deps.entrySet()) {
        String expect = e.getValue();
        String got    = actual.get(e.getKey());
        if (!Objects.equals(expect, got)) return false;
    }
    return true;
}


