package com.dbs.plugin;

import java.util.Objects;

import static com.dbs.plugin.PrdRefNoConverter.AccountType.SAV_SAVPLUS;

/**
 * Converts prd_ref_no_keyin -> prd_ref_no for DBS/POSB account families.
 * Accepts inputs with digits, spaces or dashes; rejects other characters.
 */
public final class PrdRefNoConverter {

    public enum AccountType {
        SAV_SAVPLUS("010"),     // S$ SAVINGS / SAVINGS PLUS
        POSB_SA("011"),
        SS_CURRENT("020"),
        AUTOSAVE("021"),
        FCY_CURRENT("022"),
        POSB_CA("025");

        public final String code;
        AccountType(String code) { this.code = code; }
        public static AccountType fromCode(String code) {
            for (AccountType t : values()) if (t.code.equals(code)) return t;
            throw new IllegalArgumentException("Unsupported account type code: " + code);
        }
    }

    public static void main(String[] args) {
       String result =  toPrdRefNo("010", "5546247474");
        System.out.println(result);
    }

    /** Public entry point: dispatch by account type code. */
    public static String toPrdRefNo(String acctTypeCode, String keyinRaw) {
        Objects.requireNonNull(acctTypeCode, "acctTypeCode");
        Objects.requireNonNull(keyinRaw, "prd_ref_no_keyin");
        String digits = digitsOnly(keyinRaw);
        AccountType type = AccountType.fromCode(acctTypeCode);

        return switch (type) {
            case SAV_SAVPLUS -> convert010SavingsPlus(digits);
            case POSB_SA     -> convert011PosbSa(digits);
            case SS_CURRENT  -> convert020SgdCurrent(digits);
            case AUTOSAVE    -> convert021Autosave(digits);
            case FCY_CURRENT -> convert022ForeignCurrencyCurrent(digits);
            case POSB_CA     -> convert025PosbCa(digits);
        };
    }

    // ----------------- Per-type converters -----------------

    /** 010: key-in AAA C BBBBBB  -> 0 AAA BBBBBB 000 C (14 digits). */
    private static String convert010SavingsPlus(String keyin) {
        requireLength(keyin, 10, "010 S$ SAV/SAVPLUS expects 10 digits (3-1-6).");
        String branch = keyin.substring(0, 3);
        String suffix = keyin.substring(3, 4);      // C (1)
        String body6  = keyin.substring(4);         // B (6)
        return "0" + branch + body6 + "000" + suffix;
    }

    /** 011: key-in AAA BBBBB C  -> 0 AAA (BBBBB→6) 000 C (14 digits). */
    private static String convert011PosbSa(String keyin) {
        requireLength(keyin, 9, "011 POSB SA expects 9 digits (3-5-1).");
        String branch = keyin.substring(0, 3);
        String body5  = keyin.substring(3, 8);
        String suffix = keyin.substring(8);         // C (1)
        String body6  = padLeft(body5, 6, '0');
        return "0" + branch + body6 + "000" + suffix;
    }

    /** 020: key-in AAA BBBBBB C -> 0 AAA BBBBBB 000 C (14 digits). */
    private static String convert020SgdCurrent(String keyin) {
        requireLength(keyin, 10, "020 S$ CURRENT expects 10 digits (3-6-1).");
        String branch = keyin.substring(0, 3);
        String body6  = keyin.substring(3, 9);
        String suffix = keyin.substring(9);
        return "0" + branch + body6 + "000" + suffix;
    }

    /** 021: key-in AAA BBBBBB C -> 0 AAA BBBBBB 000 C (14 digits). */
    private static String convert021Autosave(String keyin) {
        requireLength(keyin, 10, "021 AUTOSAVE expects 10 digits (3-6-1).");
        String branch = keyin.substring(0, 3);
        String body6  = keyin.substring(3, 9);
        String suffix = keyin.substring(9);
        return "0" + branch + body6 + "000" + suffix;
    }

    /** 022: key-in is already the 13-digit prd_ref_no. */
    private static String convert022ForeignCurrencyCurrent(String keyin) {
        requireLength(keyin, 13, "022 FCY CURRENT expects 13 digits.");
        return keyin;
    }

    /** 025: key-in AAA BBBBB C -> 0 AAA (BBBBB→6) 000 C (14 digits). */
    private static String convert025PosbCa(String keyin) {
        requireLength(keyin, 9, "025 POSB CA expects 9 digits (3-5-1).");
        String branch = keyin.substring(0, 3);
        String body5  = keyin.substring(3, 8);
        String suffix = keyin.substring(8);
        String body6  = padLeft(body5, 6, '0');
        return "0" + branch + body6 + "000" + suffix;
    }

    // ----------------- Helpers & validation -----------------

    /** Keep digits, normalize unicode digits; allow spaces/dashes/underscores as separators. */
    private static String digitsOnly(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') {
                out.append(ch);
            } else if (Character.isWhitespace(ch) || ch == '-' || ch == '_') {
                // skip separators
            } else if (Character.getType(ch) == Character.DECIMAL_DIGIT_NUMBER) {
                out.append(Character.getNumericValue(ch)); // normalize e.g., Arabic-Indic digits
            } else {
                throw new IllegalArgumentException("Invalid char in key-in: '" + ch + "'");
            }
        }
        if (out.length() == 0) {
            throw new IllegalArgumentException("Key-in contains no digits.");
        }
        return out.toString();
    }

    private static void requireLength(String s, int expected, String msg) {
        if (s.length() != expected) {
            throw new IllegalArgumentException(msg + " Got " + s.length() + " digits.");
        }
    }

    private static String padLeft(String s, int len, char ch) {
        if (s.length() >= len) return s;
        StringBuilder sb = new StringBuilder(len);
        for (int i = s.length(); i < len; i++) sb.append(ch);
        return sb.append(s).toString();
    }

    private PrdRefNoConverter() {}
}

