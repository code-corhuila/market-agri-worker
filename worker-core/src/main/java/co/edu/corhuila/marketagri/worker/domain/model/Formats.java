package co.edu.corhuila.marketagri.worker.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Amounts and quantities as people read them, the same rules as the interface (design-system.md
 * "Formats", ADR-029): integer centavos split with integer arithmetic, centavos only when they are
 * not zero, Colombian grouping ({@code .} for thousands, {@code ,} for decimals).
 */
public final class Formats {

    private static final long CENTS_PER_PESO = 100;
    private static final int QUANTITY_DECIMALS = 2;

    /** Short symbol where there is one, the name otherwise: singular and plural. */
    private static final Map<String, String[]> UNITS = Map.of(
            "KILOGRAMO", new String[] {"kg", "kg"},
            "LIBRA", new String[] {"lb", "lb"},
            "ARROBA", new String[] {"arroba", "arrobas"},
            "BULTO", new String[] {"bulto", "bultos"},
            "CANASTA", new String[] {"canasta", "canastas"},
            "CAJA", new String[] {"caja", "cajas"},
            "DOCENA", new String[] {"docena", "docenas"},
            "MANOJO", new String[] {"manojo", "manojos"},
            "LITRO", new String[] {"L", "L"},
            "UNIDAD", new String[] {"unidad", "unidades"});

    private Formats() {
    }

    /** {@code 8500050} → {@code $ 85.000,50}; {@code 8500000} → {@code $ 85.000}. */
    public static String cents(long cents) {
        long rest = cents % CENTS_PER_PESO;
        String centavos = rest == 0 ? "" : String.format(",%02d", rest);
        return "$ " + grouped(Long.toString(cents / CENTS_PER_PESO)) + centavos;
    }

    /** {@code 12.5, KILOGRAMO} → {@code 12,5 kg}; an unknown unit is shown in lower case. */
    public static String quantity(BigDecimal quantity, String unit) {
        String plain = quantity.setScale(QUANTITY_DECIMALS, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        int dot = plain.indexOf('.');
        String number = dot < 0 ? grouped(plain) : grouped(plain.substring(0, dot)) + "," + plain.substring(dot + 1);
        String[] names = UNITS.getOrDefault(unit, new String[] {unit.toLowerCase(), unit.toLowerCase()});
        return number + " " + (quantity.compareTo(BigDecimal.ONE) == 0 ? names[0] : names[1]);
    }

    private static String grouped(String digits) {
        StringBuilder out = new StringBuilder(digits);
        for (int i = digits.length() - 3; i > 0; i -= 3) {
            out.insert(i, '.');
        }
        return out.toString();
    }
}
