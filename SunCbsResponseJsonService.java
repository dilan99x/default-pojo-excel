package com.dbs.plugin.service;

import com.dbs.plugin.constants.ExcelHeaderConstants;
import com.dbs.plugin.model.ApiField;
import com.dbs.plugin.model.ApiMapping;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

public class SunCbsResponseJsonService {

    public Map<String, ApiMapping> extractSunCbsResponseMappings(File excelFile) throws Exception {
        Map<String, ApiMapping> result = new LinkedHashMap<>();

        try (Workbook workbook = WorkbookFactory.create(new FileInputStream(excelFile))) {
            Sheet sheet = workbook.getSheet(ExcelHeaderConstants.SHEET_NAME);
            if (sheet == null) return result;

            int headerRowIndex = findResponseHeaderRow(sheet);
            if (headerRowIndex == -1) return result;

            String sectionHeader = findMainHeaderBefore(sheet, headerRowIndex);
            if (sectionHeader == null) return result;

            String baseName = sectionHeader.replace(ExcelHeaderConstants.RESPONSE_MARKER, "").trim().replaceAll("\\s*-\\s*$", "");
            String mappingId = baseName + "_suncbs_response";
            String fileName = mappingId + "_transformer.json";

            Row headerRow = sheet.getRow(headerRowIndex);
            Map<String, Integer> columnMap = buildColumnIndexMap(headerRow);

            List<ApiField> fields = new ArrayList<>();

            for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                // Filter: SG only and SG Mandatory not blank
                String country = getSafeCellValue(row, columnMap.get("Applicable Country"));
                String mandatory = getSafeCellValue(row, columnMap.get("SG Mandatory"));
                if (!"SG".equalsIgnoreCase(country) || mandatory == null || mandatory.trim().isEmpty()) continue;

                String source = getSafeCellValue(row, columnMap.get(ExcelHeaderConstants.SUNCBS_FIELDNAME));
                String sourceType = getSafeCellValue(row, columnMap.get(ExcelHeaderConstants.SUNCBS_DATATYPE));
                String target = getSafeCellValue(row, columnMap.get(ExcelHeaderConstants.GLOBAL_API_FIELDNAME));
                String targetType = getSafeCellValue(row, columnMap.get(ExcelHeaderConstants.GLOBAL_API_DATATYPE));
                String operation = getSafeCellValue(row, columnMap.get(ExcelHeaderConstants.SUNCBS_OPERATION));
                String transform = getSafeCellValue(row, columnMap.get(ExcelHeaderConstants.SUNCBS_TRANSFORM_VALUE));

                // Skip header repetition
                if (source.equalsIgnoreCase(ExcelHeaderConstants.SUNCBS_FIELDNAME) ||
                        target.equalsIgnoreCase(ExcelHeaderConstants.GLOBAL_API_FIELDNAME)) continue;

                if (!source.isEmpty() || !target.isEmpty()) {
                    fields.add(new ApiField(
                            source,
                            sourceType.isEmpty() ? null : sourceType,
                            target,
                            targetType.isEmpty() ? null : targetType,
                            operation.isEmpty() ? null : operation,
                            transform.isEmpty() ? null : transform.replace("\n", "").replace("\r", "")
                    ));
                }
            }

            ApiMapping mapping = new ApiMapping(
                    mappingId,
                    baseName,
                    baseName,
                    ExcelHeaderConstants.SUNCBS_FIELDNAME,
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