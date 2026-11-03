package uk.gov.defra.mmocatchrecord.common.design

import org.junit.Assert.assertEquals
import org.junit.Test

class GdsNumericFieldFilterTests {
    @Test
    fun integerKindStripsEverythingButAsciiDigits() {
        assertEquals("123", filterNumericInput("", "1.2a3", GdsNumericFieldKind.Integer))
    }

    @Test
    fun decimalKindAcceptsASinglePointWithUnlimitedDigitsWhenUncapped() {
        assertEquals("1.234", filterNumericInput("1.23", "1.234", GdsNumericFieldKind.Decimal))
        assertEquals(".", filterNumericInput("", ".", GdsNumericFieldKind.Decimal))
    }

    @Test
    fun decimalKindRejectsASecondDecimalPoint() {
        assertEquals("1.2", filterNumericInput("1.2", "1.2.", GdsNumericFieldKind.Decimal))
        assertEquals("1.", filterNumericInput("1.", "1..", GdsNumericFieldKind.Decimal, maxDecimalPlaces = 1))
        assertEquals("", filterNumericInput("", "1.2.3", GdsNumericFieldKind.Decimal, maxDecimalPlaces = 1))
    }

    @Test
    fun decimalKindWithOneDecimalPlaceCapRejectsASecondFractionDigit() {
        assertEquals("5.5", filterNumericInput("5.5", "5.55", GdsNumericFieldKind.Decimal, maxDecimalPlaces = 1))
        assertEquals("5.5", filterNumericInput("5.", "5.5", GdsNumericFieldKind.Decimal, maxDecimalPlaces = 1))
        assertEquals("15.5", filterNumericInput("5.5", "15.5", GdsNumericFieldKind.Decimal, maxDecimalPlaces = 1))
    }

    @Test
    fun decimalKindRejectsNonDigitCharacters() {
        assertEquals("1", filterNumericInput("1", "1,5", GdsNumericFieldKind.Decimal, maxDecimalPlaces = 1))
        assertEquals("1", filterNumericInput("1", "1-", GdsNumericFieldKind.Decimal))
    }

    @Test
    fun clearingTheFieldIsAlwaysAllowed() {
        assertEquals("", filterNumericInput("5.5", "", GdsNumericFieldKind.Decimal, maxDecimalPlaces = 1))
    }
}
