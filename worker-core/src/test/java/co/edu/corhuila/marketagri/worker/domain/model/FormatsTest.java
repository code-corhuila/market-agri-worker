package co.edu.corhuila.marketagri.worker.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Amounts and quantities as people read them, the same as the interface (design-system.md
 * "Formats", ADR-029): integer centavos, centavos only when they are not zero, Colombian grouping.
 */
class FormatsTest {

    @Test
    void centsShowPesosWithColombianGroupingAndCentavosOnlyWhenNotZero() {
        assertThat(Formats.cents(8_500_000)).isEqualTo("$ 85.000");
        assertThat(Formats.cents(8_500_050)).isEqualTo("$ 85.000,50");
        assertThat(Formats.cents(123_456_789)).isEqualTo("$ 1.234.567,89");
        assertThat(Formats.cents(850_000)).isEqualTo("$ 8.500");
        assertThat(Formats.cents(100)).isEqualTo("$ 1");
        assertThat(Formats.cents(5)).isEqualTo("$ 0,05");
    }

    @Test
    void quantitiesUseADecimalCommaAndTheUnitNameInSingularOrPlural() {
        assertThat(Formats.quantity(new BigDecimal("12.50"), "KILOGRAMO")).isEqualTo("12,5 kg");
        assertThat(Formats.quantity(new BigDecimal("1.00"), "ARROBA")).isEqualTo("1 arroba");
        assertThat(Formats.quantity(new BigDecimal("3"), "ARROBA")).isEqualTo("3 arrobas");
        assertThat(Formats.quantity(new BigDecimal("1234.5"), "UNIDAD")).isEqualTo("1.234,5 unidades");
        assertThat(Formats.quantity(new BigDecimal("2"), "NUEVA")).isEqualTo("2 nueva");
    }
}
