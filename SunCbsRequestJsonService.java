package com.dbs.plugin.service;

import com.dbs.plugin.constants.ExcelHeaderConstants;
import com.dbs.plugin.model.ApiField;
import com.dbs.plugin.model.ApiMapping;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

public class SunCbsRequestJsonService {

    public Map<String, ApiMapping> extractSunCbsRequestMappings(File excelFile) throws Exception {
        Map<String, ApiMapping> result = new LinkedHashMap<>();

        try (Workbook workbook = WorkbookFactory.create(new FileInputStream(excelFile))) {
            Sheet sheet = workbook.getSheet(ExcelHeaderConstants.SHEET_NAME);
            if (sheet == null) return result;

            int headerRowIndex = findHeaderRow(sheet, 0);
            if (headerRowIndex == -1) return result;

            String sectionHeader = findMainHeaderBefore(sheet, headerRowIndex);
            if (sectionHeader == null) return result;

            // ✅ Fix: Remove ' - Request' with optional spaces and dash
            String baseName = sectionHeader.replaceAll("\\s*-\\s*Request", "").trim();
            String mappingId = baseName + "_suncbs_request";
            String fileName = mappingId + "_transformer.json";

            Row headerRow = sheet.getRow(headerRowIndex);
            Map<String, Integer> columnMap = buildColumnIndexMap(headerRow);

            List<ApiField> fields = new ArrayList<>();

            for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                if (rowContainsKeyword(row, "Response")) break;

                String country = getSafeCellValue(row, columnMap.get("Applicable Country"));
                String mandatory = getSafeCellValue(row, columnMap.get("SG Mandatory"));
                if (!"SG".equalsIgnoreCase(country) || mandatory == null || mandatory.trim().isEmpty()) continue;

                String source = getSafeCellValue(row, columnMap.get(ExcelHeaderConstants.GLOBAL_API_FIELDNAME));
                String sourceType = getSafeCellValue(row, columnMap.get(ExcelHeaderConstants.GLOBAL_API_DATATYPE));
                String target = getSafeCellValue(row, columnMap.get(ExcelHeaderConstants.SUNCBS_FIELDNAME));
                String targetType = getSafeCellValue(row, columnMap.get(ExcelHeaderConstants.SUNCBS_DATATYPE));
                String operation = getSafeCellValue(row, columnMap.get(ExcelHeaderConstants.SUNCBS_OPERATION));
                String transform = getSafeCellValue(row, columnMap.get(ExcelHeaderConstants.SUNCBS_TRANSFORM_VALUE));

                if (
                        source.equalsIgnoreCase(ExcelHeaderConstants.GLOBAL_API_FIELDNAME) ||
                                target.equalsIgnoreCase(ExcelHeaderConstants.SUNCBS_FIELDNAME)
                ) continue;

                if (!source.isEmpty() || !target.isEmpty()) {
                    transform = transform.replace("\n", "").replace("\r", "").trim();
                    fields.add(new ApiField(
                            source,
                            sourceType.isEmpty() ? null : sourceType,
                            target,
                            targetType.isEmpty() ? null : targetType,
                            operation.isEmpty() ? null : operation,
                            operation.isEmpty() || transform.isEmpty() ? null : transform
                    ));
                }
            }

            ApiMapping mapping = new ApiMapping(
                    mappingId,
                    baseName,
                    baseName,
                    ExcelHeaderConstants.GLOBAL_API_FIELDNAME,
                    ExcelHeaderConstants.SUNCBS_FIELDNAME,
                    fields
            );

            result.put(fileName, mapping);
        }

        return result;
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

    private int findHeaderRow(Sheet sheet, int startRow) {
        for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING &&
                        cell.getStringCellValue().trim().equalsIgnoreCase(ExcelHeaderConstants.GLOBAL_API_FIELDNAME)) {
                    return i;
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
                    if (text.toLowerCase().contains(ExcelHeaderConstants.REQUEST_MARKER.toLowerCase())) {
                        return text;
                    }
                }
            }
        }
        return null;
    }

    private boolean rowContainsKeyword(Row row, String keyword) {
        if (row == null) return false;
        for (Cell cell : row) {
            if (cell.getCellType() == CellType.STRING &&
                    cell.getStringCellValue().toLowerCase().contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
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