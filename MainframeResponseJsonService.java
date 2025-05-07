package com.dbs.plugin.service;

import com.dbs.plugin.constants.ExcelHeaderConstants;
import com.dbs.plugin.model.ApiField;
import com.dbs.plugin.model.ApiMapping;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

public class MainframeResponseJsonService {

    public Map<String, ApiMapping> extractMainframeResponseMappings(File excelFile) throws Exception {
        Map<String, ApiMapping> result = new LinkedHashMap<>();

        try (Workbook workbook = WorkbookFactory.create(new FileInputStream(excelFile))) {
            Sheet sheet = workbook.getSheet(ExcelHeaderConstants.SHEET_NAME);
            if (sheet == null) return result;

            int headerRowIndex = findResponseHeaderRow(sheet);
            if (headerRowIndex == -1) {
                System.out.println("⚠️ Could not find response header row.");
                return result;
            }

            String sectionHeader = findMainHeaderBefore(sheet, headerRowIndex);
            if (sectionHeader == null) {
                System.out.println("⚠️ Could not find section header before response header row.");
                return result;
            }

            String baseName = sectionHeader.replace(ExcelHeaderConstants.RESPONSE_MARKER, "").trim().replace(" -", "").replace("-", "");
            String mappingId = baseName + "_mainframe_response";
            String fileName = mappingId + "_transformer.json";

            Row headerRow = sheet.getRow(headerRowIndex);
            Map<String, Integer> columnMap = buildColumnIndexMap(headerRow);

            List<ApiField> fields = new ArrayList<>();

            for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                // ✅ Changed field to SG Field Availability for response mapping
                String country = getSafeCellValue(row, columnMap.get("Applicable Country"));
                String mandatory = getSafeCellValue(row, columnMap.get(ExcelHeaderConstants.SG_FIELD_AVAILABILITY));
                if (!"SG".equalsIgnoreCase(country) || mandatory == null || mandatory.trim().isEmpty()) {
                    System.out.printf("❌ Row %d skipped due to country='%s' or SG Field Availability='%s'%n", i + 1, country, mandatory);
                    continue;
                }

                String source = getSafeCellValue(row, columnMap.get(ExcelHeaderConstants.MAINFRAME_FIELDNAME));
                String sourceType = getSafeCellValue(row, columnMap.get(ExcelHeaderConstants.MAINFRAME_DATATYPE));
                String target = getSafeCellValue(row, columnMap.get(ExcelHeaderConstants.GLOBAL_API_FIELDNAME));
                String targetType = getSafeCellValue(row, columnMap.get(ExcelHeaderConstants.GLOBAL_API_DATATYPE));
                String operation = getSafeCellValue(row, columnMap.get(ExcelHeaderConstants.MAINFRAME_OPERATION));
                String transform = getSafeCellValue(row, columnMap.get(ExcelHeaderConstants.MAINFRAME_TRANSFORM_VALUE));

                if (
                        source.equalsIgnoreCase(ExcelHeaderConstants.MAINFRAME_FIELDNAME) ||
                                target.equalsIgnoreCase(ExcelHeaderConstants.GLOBAL_API_FIELDNAME)
                ) {
                    System.out.printf("❌ Row %d skipped due to header repetition.%n", i + 1);
                    continue;
                }

                if (!source.isEmpty() || !target.isEmpty()) {
                    fields.add(new ApiField(
                            target,
                            targetType.isEmpty() ? null : targetType,
                            source,
                            sourceType.isEmpty() ? null : sourceType,
                            operation.isEmpty() ? null : operation,
                            transform.isEmpty() ? null : transform.replace("\n", "").replace("\r", "")
                    ));
                    System.out.printf("✅ Row %d mapped: %s → %s%n", i + 1, source, target);
                } else {
                    System.out.printf("❌ Row %d skipped due to empty source/target.%n", i + 1);
                }
            }

            ApiMapping mapping = new ApiMapping(
                    mappingId,
                    baseName,
                    baseName,
                    ExcelHeaderConstants.MAINFRAME_FIELDNAME,
                    ExcelHeaderConstants.GLOBAL_API_FIELDNAME,
                    fields
            );

            result.put(fileName, mapping);
        }

        return result;
    }

    private int findResponseHeaderRow(Sheet sheet) {
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().toLowerCase().contains("response")) {
                    for (int j = i + 1; j <= sheet.getLastRowNum(); j++) {
                        Row candidate = sheet.getRow(j);
                        if (candidate == null) continue;
                        for (Cell inner : candidate) {
                            if (inner.getCellType() == CellType.STRING &&
                                    inner.getStringCellValue().trim().equalsIgnoreCase(ExcelHeaderConstants.GLOBAL_API_FIELDNAME)) {
                                return j;
                            }
                        }
                    }
                }
            }
        }
        return -1;
    }

    private String findMainHeaderBefore(Sheet sheet, int beforeRow) {
        for (int i = beforeRow - 1; i >= 0; i--) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING) {
                    String text = cell.getStringCellValue().trim();
                    if (text.toLowerCase().contains(ExcelHeaderConstants.RESPONSE_MARKER.toLowerCase())) {
                        return text;
                    }
                }
            }
        }
        return null;
    }

    private Map<String, Integer> buildColumnIndexMap(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        if (headerRow == null) return map;
        for (Cell cell : headerRow) {
            if (cell.getCellType() == CellType.STRING) {
                String key = cell.getStringCellValue()
                        .replace('\u00A0', ' ')
                        .replaceAll("[\\s\\u00A0]+", " ")
                        .trim();
                map.put(key, cell.getColumnIndex());
            }
        }
        return map;
    }

    private String getSafeCellValue(Row row, Integer colIndex) {
        if (row == null || colIndex == null || colIndex < 0) return "";
        Cell cell = row.getCell(colIndex);
        return getCellValue(cell);
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        try {
            cell.setCellType(CellType.STRING);
            return cell.getStringCellValue().trim();
        } catch (Exception e) {
            return "";
        }
    }
}