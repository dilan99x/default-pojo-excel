package com.dbs.cb.gapi.core;

public class Fixes {
    // --- NEW ENTRY POINT: non-mutating wrapper ---
    private void handleArrayProjections(JsonNode sourceNode, FieldMapping field,
                                        JsonNode targetNode, MappingConfig config) {
        handleArrayProjections(sourceNode,
                field,
                field.getSourcePointer(),
                field.getTargetPointer(),
                field.getDependencies(),
                targetNode,
                config);
    }

    // --- NEW WORKER: carries pointers/deps via parameters (no mutation) ---
    private void handleArrayProjections(JsonNode sourceNode, FieldMapping field,
                                        String curSourcePtr, String curTargetPtr,
                                        Map<String, String> curDeps,
                                        JsonNode targetNode, MappingConfig config) {
        long startTime = System.currentTimeMillis();
        if (targetNode.isEmpty() || sourceNode.isNull()) return;

        long sCount = curSourcePtr.chars().filter(c -> c == '*').count();
        long tCount = curTargetPtr.chars().filter(c -> c == '*').count();
        if (sCount != tCount) {
            String msg = String.format(
                    "Failed on field mapping: %s -> %s, error: source and target must have same number of levels of dynamic arrays (*)",
                    curSourcePtr, curTargetPtr);
            throw new TransformationException(msg);
        }

        boolean srcStartsWithArray = curSourcePtr.startsWith("/*");
        boolean tgtStartsWithArray = curTargetPtr.startsWith("/*");
        boolean srcIsDynamic = curSourcePtr.contains("*");

        if (srcStartsWithArray || sourceNode.isArray() || srcIsDynamic) {
            String nextSourcePtr;
            String nextTargetPtr = curTargetPtr;
            if (tgtStartsWithArray) {
                nextTargetPtr = curTargetPtr.substring(2);
            }

            JsonNode sourceArrayNode;
            if (sourceNode.isArray()) {
                sourceArrayNode = sourceNode;
                nextSourcePtr = srcStartsWithArray ? curSourcePtr.substring(2) : curSourcePtr;
            } else {
                int srcAstIdx = curSourcePtr.indexOf("/*");
                sourceArrayNode = sourceNode.at(curSourcePtr.substring(0, srcAstIdx));
                nextSourcePtr = curSourcePtr.substring(srcAstIdx + 2);
            }
            boolean nextSourceIsDynamic = nextSourcePtr.contains("*");

            for (int i = 0; i < sourceArrayNode.size(); i++) {
                JsonNode indexedSourceNode = sourceArrayNode.get(i);
                if (indexedSourceNode == null) continue; // guard

                // dependencies for this index (non-mutating)
                Map<String, String> idxDeps = remapDependencies(curDeps, i);

                // resolve target pointer for this index if needed
                String idxTargetPtr = tgtStartsWithArray
                        ? nextTargetPtr
                        : ASTERIK_PATTERN.matcher(curTargetPtr).replaceFirst(String.valueOf(i));

                boolean targetIsDynamic = idxTargetPtr.contains("*");

                if (!nextSourceIsDynamic) {
                    JsonNode value = indexedSourceNode.at(nextSourcePtr);
                    if (value.isNull()) continue;

                    if (!isDependencyValid(idxDeps, config)) continue;

                    if (field.getOperationType() != null) {
                        value = customOperationHandling(value, field);
                    }
                    value = !StringUtils.isBlank(field.getTargetDataType())
                            ? convertDataType(value, field.getTargetDataType(), field.getTargetPointer())
                            : value;

                    if (!targetIsDynamic) {
                        if (!targetNode.isArray()) {
                            setValueAtPointer(idxTargetPtr, value, targetNode);
                        } else {
                            ArrayNode targetArrayNode = (ArrayNode) targetNode;
                            ensureArraySize(targetArrayNode, i);
                            JsonNode targetNestedObjectNode = targetArrayNode.get(i);
                            if (targetNestedObjectNode.isNull() || targetNestedObjectNode.isMissingNode()) {
                                targetNestedObjectNode = objectMapper.createObjectNode();
                            }
                            setValueAtPointer(idxTargetPtr, value, targetNestedObjectNode);
                            targetArrayNode.set(i, targetNestedObjectNode);
                        }
                    } else {
                        // recurse deeper, still non-mutating
                        int srcAstIdx2 = nextSourcePtr.indexOf("/*");
                        String srcPtrToNestedArray = nextSourcePtr.substring(0, srcAstIdx2);
                        JsonNode nestedSourceArray = indexedSourceNode.at(srcPtrToNestedArray);
                        if (nestedSourceArray.isEmpty()) continue;

                        String deeperSourcePtr = nextSourcePtr.substring(srcAstIdx2);
                        int tgtAstIdx = idxTargetPtr.indexOf("/*");
                        String tgtPtrToNestedArray = idxTargetPtr.substring(0, tgtAstIdx);
                        String deeperTargetPtr = idxTargetPtr.substring(tgtAstIdx);

                        JsonNode nestedTargetArray;
                        if (!targetNode.isArray()) {
                            nestedTargetArray = targetNode.at(tgtPtrToNestedArray);
                        } else {
                            nestedTargetArray = targetNode.get(i).at(tgtPtrToNestedArray);
                        }
                        if (nestedTargetArray.isNull() || nestedTargetArray.isMissingNode()) {
                            nestedTargetArray = objectMapper.createArrayNode();
                        }

                        handleArrayProjections(nestedSourceArray, field,
                                deeperSourcePtr, deeperTargetPtr, idxDeps,
                                nestedTargetArray, config);

                        if (!targetNode.isArray()) {
                            setValueAtPointer(tgtPtrToNestedArray, nestedTargetArray, targetNode);
                        } else {
                            ArrayNode targetArrayNode = (ArrayNode) targetNode;
                            ensureArraySize(targetArrayNode, i);
                            JsonNode targetNestedObjectNode = targetArrayNode.get(i);
                            if (targetNestedObjectNode.isNull() || targetNestedObjectNode.isMissingNode()) {
                                targetNestedObjectNode = objectMapper.createObjectNode();
                            }
                            setValueAtPointer(tgtPtrToNestedArray, nestedTargetArray, targetNestedObjectNode);
                            targetArrayNode.set(i, targetNestedObjectNode);
                        }
                    }
                }
            }
        }

        log.debug("PROJECTION_COMPLETION_FOR_FIELD_LATENCY {}", System.currentTimeMillis() - startTime);
    }

}
