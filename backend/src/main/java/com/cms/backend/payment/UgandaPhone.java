package com.cms.backend.payment;

public final class UgandaPhone {
    private UgandaPhone() {
    }

    public static String toMsisdn(String raw) {
        String digits = String.valueOf(raw == null ? "" : raw).replaceAll("\\D", "");
        if (digits.startsWith("256") && digits.length() == 12) {
            return digits;
        }
        if (digits.startsWith("0") && digits.length() == 10) {
            return "256" + digits.substring(1);
        }
        if (digits.length() == 9) {
            return "256" + digits;
        }
        throw new IllegalArgumentException("Enter a Ugandan mobile number such as 0770 123 456.");
    }

    public static String national(String raw) {
        String msisdn = toMsisdn(raw);
        return msisdn.substring(3);
    }

    public static boolean looksLikeMtn(String msisdn) {
        String national = toMsisdn(msisdn).substring(3);
        return national.startsWith("76") || national.startsWith("77") || national.startsWith("78") || national.startsWith("39");
    }

    public static boolean looksLikeAirtel(String msisdn) {
        String national = toMsisdn(msisdn).substring(3);
        return national.startsWith("70") || national.startsWith("74") || national.startsWith("75") || national.startsWith("20");
    }
}
