package com.tapecalc.core.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** The 47 constants and 40 conversions of the Casio fx-991EX list must all exist and be right. */
class CasioListTest {

    private fun value(name: String): BigDecimal = Constants.valueOf(name)!!.decimal

    private fun close(expected: String, actual: BigDecimal, relative: Double = 1e-9) {
        val e = BigDecimal(expected)
        val diff = actual.subtract(e).abs().toDouble()
        assertTrue("expected $expected but was $actual", diff <= Math.abs(e.toDouble()) * relative)
    }

    private fun show(text: String): String {
        val o = Calculator.evaluate(text, EvalContext())
        return (o as? EvalOutcome.Success)?.result?.display ?: throw AssertionError("'$text' failed: ${(o as EvalOutcome.Failure).error.message}")
    }

    // ------------------------------------------------------------------ constants

    private val casioNames = listOf(
        "hP", "hbar", "c0", "eps0", "mu0", "Z0", "Gn", "lP", "tP",
        "muN", "muB", "qe", "Phi0", "G0", "KJos", "RK",
        "mp", "mn", "me", "mmu", "a0", "alpha", "relec", "lamC", "lamCp", "Ry", "mup", "mue", "mun", "mumu", "mtau", "lamCn", "gammap",
        "amu", "Fara", "NA", "kB", "Vm", "Rgas", "cr1", "cr2", "sigmaSB",
        "g0", "atm", "KJos90", "RK90", "T0"
    )

    @Test fun allFortySevenCasioConstantsExist() {
        assertEquals(47, casioNames.size)
        assertEquals(47, casioNames.toSet().size)
        for (n in casioNames) assertNotNull("constant $n", Constants.find(n))
    }

    @Test fun everyConstantHasUniqueNameAndKnownGroup() {
        assertEquals(Constants.all.size, Constants.all.map { it.name }.toSet().size)
        for (c in Constants.all) assertTrue(c.name, c.group in Constants.groups)
    }

    @Test fun derivedConstantsMatchCodata() {
        close("2.067833848461929E-15", value("Phi0"))
        close("7.748091729863649E-5", value("G0"))
        close("4.835978484169836E14", value("KJos"))
        close("25812.80745930", value("RK"), 1e-11)
        close("3.741771852E-16", value("cr1"), 1e-9)
        close("1.438776877E-2", value("cr2"), 1e-9)
    }

    @Test fun measuredConstantsAreStoredWithTheirSign() {
        assertTrue(value("mue").signum() < 0)
        assertTrue(value("mun").signum() < 0)
        assertTrue(value("mumu").signum() < 0)
        assertTrue(value("mup").signum() > 0)
    }

    @Test fun newConstantsWorkInsideCalculations() {
        assertEquals("376.730313668", show("Z0"))
        assertEquals("25812.807", show("RK90"))
        assertTrue(show("2*lP").startsWith("3.23251"))
        // Phi0 * KJos = 1 exactly (h/2e times 2e/h)
        assertEquals("1", show("Phi0*KJos"))
    }

    // ------------------------------------------------------------------ conversions

    private fun convert(text: String) = show(text)

    @Test fun parsecConversions() {
        assertTrue(convert("1 pc to km").startsWith("30856775814913."))
        assertTrue(convert("1 km to pc").startsWith("3.2407792"))
        assertEquals("1", convert("1 pc to pc"))
    }

    @Test fun kgfPerSquareCentimeterConversions() {
        assertEquals("98066.5", convert("1 kgf/cm2 to Pa"))
        assertEquals("1", convert("98066.5 Pa to kgf/cm2"))
        assertEquals("98.0665", convert("1 kgf/cm2 to kPa"))
    }

    @Test fun powerConversions() {
        assertEquals("745.69987158227", convert("1 hp to W"))
        assertTrue(convert("1 hp to kW").startsWith("0.745699871"))
        assertTrue(convert("1 kW to hp").startsWith("1.3410220"))
        assertEquals("1000", convert("1 kW to W"))
    }

    @Test fun kilogramForceMeterConversions() {
        assertEquals("9.80665", convert("1 kgf_m to J"))
        assertEquals("1", convert("9.80665 J to kgf_m"))
        assertEquals("9.80665", convert("1 kgfm to J"))
    }

    @Test fun theOtherThirtyTwoDirectionsStillWork() {
        assertEquals("2.54", convert("1 in to cm"))
        assertEquals("0.3048", convert("1 ft to m"))
        assertEquals("0.9144", convert("1 yd to m"))
        assertEquals("1.609344", convert("1 mi to km"))
        assertEquals("1852", convert("1 n_mile to m"))
        assertEquals("4046.8564224", convert("1 acre to m2"))
        assertEquals("3.785411784", convert("1 gal to L"))
        assertEquals("4.54609", convert("1 impgal to L"))
        assertEquals("28.349523125", convert("1 oz to g"))
        assertEquals("0.45359237", convert("1 lb to kg"))
        assertEquals("1", convert("3.6 km/h to m/s"))
        assertEquals("101325", convert("1 atm to Pa"))
        assertEquals("133.322387415", convert("1 mmHg to Pa"))
        assertEquals("6.894757293168", convert("1 psi to kPa"))
        assertEquals("4.184", convert("1 cal to J"))
        assertEquals("32", convert("0 C to F"))
        assertEquals("-40", convert("-40 F to C"))
    }

    @Test fun powerCategoryIsListed() {
        assertTrue(UnitRegistry.byCategory(UnitCategory.POWER).size >= 5)
        assertNotNull(UnitRegistry.find("hp"))
        assertNotNull(UnitRegistry.find("pc"))
    }
}
