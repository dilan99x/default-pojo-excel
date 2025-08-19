package com.dbs.plugin;

import java.util.Objects;
import java.util.regex.Pattern;

public class ReverseConverter {

    // Strict ASCII digits (whole string)
    private static final Pattern DIGITS_ONLY = Pattern.compile("\\A[0-9]+\\z");
    // Strip zero-width format chars that might sneak in via copy/paste
    private static final Pattern INVISIBLE_FORMAT = Pattern.compile("[\\p{Cf}]");

    // -------------------- PUBLIC API --------------------
    public static void main(String[] args) {
        String prdRefNoKeyin = toPrdRefNoKeyin("011", "04380116480006");
        System.out.println(prdRefNoKeyin);
    }

    /** Reverse: prd_ref_no -> prd_ref_no_keyin (digits only). */
    public static String toPrdRefNoKeyin(String acctTypeCode, String prdRefNoRaw) {
        Objects.requireNonNull(acctTypeCode, "acctTypeCode");
        Objects.requireNonNull(prdRefNoRaw, "prd_ref_no");

        PrdRefNoConverter.AccountType type = PrdRefNoConverter.AccountType.fromCode(acctTypeCode);
        String prd = normalizeStrictDigits(prdRefNoRaw, "prd_ref_no");

        return switch (type) {
            case FCY_CURRENT -> { // 022: 13-digit PRD equals key-in
                requireLength(prd, 13, "Invalid 022 prd_ref_no: expected 13 digits.");
                yield prd;
            }
            case SAV_SAVPLUS -> rev010(prd);
            case POSB_SA     -> rev011(prd);
            case SS_CURRENT  -> rev020(prd);
            case AUTOSAVE    -> rev021(prd);
            case POSB_CA     -> rev025(prd);
        };
    }

    // -------------------- REVERSE (prd_ref_no -> keyin) --------------------

    private static String rev010(String prd) {
        requirePrd14Structure(prd, "010");
        String A  = prd.substring(1, 4);
        String B6 = prd.substring(4, 10);
        String C  = prd.substring(13);
        return A + C + B6; // 3-1-6
    }

    private static String rev011(String prd) {
        requirePrd14Structure(prd, "011");
        String A  = prd.substring(1, 4);
        String B6 = prd.substring(4, 10);
        String C  = prd.substring(13);
        String B5 = B6.substring(1); // last 5
        return A + B5 + C; // 3-5-1
    }

    /** 020: prd 0 AAA BBBBBB 000 C -> key-in AAA BBBBBB C (10) */
    private static String rev020(String prd) {
        requirePrd14Structure(prd, "020");
        String A  = prd.substring(1, 4);
        String B6 = prd.substring(4, 10);
        String C  = prd.substring(13);
        return A + B6 + C; // 3-6-1
    }

    private static String rev021(String prd) {
        requirePrd14Structure(prd, "021");
        String A  = prd.substring(1, 4);
        String B6 = prd.substring(4, 10);
        String C  = prd.substring(13);
        return A + B6 + C; // 3-6-1
    }

    private static String rev025(String prd) {
        requirePrd14Structure(prd, "025");
        String A  = prd.substring(1, 4);
        String B6 = prd.substring(4, 10);
        String C  = prd.substring(13);
        String B5 = B6.substring(1); // last 5
        return A + B5 + C; // 3-5-1
    }

    private static int expectedKeyinLength(PrdRefNoConverter.AccountType t) {
        return switch (t) {
            case SAV_SAVPLUS -> 10; // 3-1-6
            case POSB_SA, POSB_CA -> 9; // 3-5-1
            case SS_CURRENT, AUTOSAVE -> 10; // 3-6-1
            case FCY_CURRENT -> 13; // 4-6-2-1 (returned as-is)
        };
    }

    private static String normalizeStrictDigits(String raw, String label) {
        String s = INVISIBLE_FORMAT.matcher(raw).replaceAll("").trim();
        if (!DIGITS_ONLY.matcher(s).matches()) {
            throw new IllegalArgumentException(label + " must contain digits 0-9 only (no spaces/dashes).");
        }
        return s;
    }

    private static void requirePrd14Structure(String prd, String code) {
        requireLength(prd, 14, "Invalid " + code + " prd_ref_no: expected 14 digits.");
        if (prd.charAt(0) != '0' || !prd.startsWith("000", 10)) {
            throw new IllegalArgumentException(
                    "Invalid " + code + " prd_ref_no structure: must be '0' + 3-digit A + 6-digit B + '000' + 1-digit C.");
        }
    }

    private static void requireLength(String s, int expected, String msg) {
        if (s.length() != expected) throw new IllegalArgumentException(msg + " Got " + s.length() + ".");
    }

    private static String padLeft(String s, int len, char ch) {
        if (s.length() >= len) return s;
        StringBuilder sb = new StringBuilder(len);
        for (int i = s.length(); i < len; i++) sb.append(ch);
        return sb.append(s).toString();
    }

}
