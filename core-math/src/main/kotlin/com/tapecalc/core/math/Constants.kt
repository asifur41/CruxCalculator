package com.tapecalc.core.math

import com.tapecalc.core.precision.BigMath
import java.math.BigDecimal

/** A physical constant that can be typed by name (`c0`, `hP`, `NA`, ...). */
class NamedConstant(
    val name: String,
    val symbol: String,
    val description: String,
    val unit: String,
    internal val make: () -> CalcValue
) {
    val value: CalcValue by lazy(make)
}

/**
 * Scientific constants (CODATA 2018, the SI-defining ones exact). Names are case-sensitive, and a variable the
 * person has defined with the same name always wins, so `c0 = 5` still works.
 */
object Constants {
    private fun lit(text: String): () -> CalcValue = { CalcValue.parseLiteral(text) }

    val all: List<NamedConstant> = listOf(
        NamedConstant("c0", "c", "Speed of light in vacuum", "m/s", lit("299792458")),
        NamedConstant("hP", "h", "Planck constant", "J·s", lit("6.62607015E-34")),
        NamedConstant("hbar", "ħ", "Reduced Planck constant h/2π", "J·s",
            { CalcValue.ofDecimal(BigDecimal("6.62607015E-34").divide(BigMath.pi.multiply(BigDecimal(2)), com.tapecalc.core.precision.Precision.INTERNAL)) }),
        NamedConstant("qe", "e", "Elementary charge", "C", lit("1.602176634E-19")),
        NamedConstant("kB", "k", "Boltzmann constant", "J/K", lit("1.380649E-23")),
        NamedConstant("NA", "N_A", "Avogadro constant", "1/mol", lit("6.02214076E23")),
        NamedConstant("Rgas", "R", "Molar gas constant", "J/(mol·K)", lit("8.31446261815324")),
        NamedConstant("Fara", "F", "Faraday constant", "C/mol", lit("96485.33212331")),
        NamedConstant("Gn", "G", "Newtonian constant of gravitation", "m³/(kg·s²)", lit("6.67430E-11")),
        NamedConstant("g0", "g", "Standard gravity", "m/s²", lit("9.80665")),
        NamedConstant("me", "m_e", "Electron mass", "kg", lit("9.1093837015E-31")),
        NamedConstant("mp", "m_p", "Proton mass", "kg", lit("1.67262192369E-27")),
        NamedConstant("mn", "m_n", "Neutron mass", "kg", lit("1.67492749804E-27")),
        NamedConstant("amu", "u", "Atomic mass constant", "kg", lit("1.66053906660E-27")),
        NamedConstant("eps0", "ε₀", "Vacuum permittivity", "F/m", lit("8.8541878128E-12")),
        NamedConstant("mu0", "μ₀", "Vacuum permeability", "N/A²", lit("1.25663706212E-6")),
        NamedConstant("ke", "k_e", "Coulomb constant 1/(4πε₀)", "N·m²/C²", lit("8.9875517923E9")),
        NamedConstant("alpha", "α", "Fine-structure constant", "", lit("7.2973525693E-3")),
        NamedConstant("Ry", "R∞", "Rydberg constant", "1/m", lit("10973731.568160")),
        NamedConstant("a0", "a₀", "Bohr radius", "m", lit("5.29177210903E-11")),
        NamedConstant("muB", "μ_B", "Bohr magneton", "J/T", lit("9.2740100783E-24")),
        NamedConstant("sigmaSB", "σ", "Stefan-Boltzmann constant", "W/(m²·K⁴)", lit("5.670374419E-8")),
        NamedConstant("atm", "atm", "Standard atmosphere", "Pa", lit("101325")),
        NamedConstant("T0", "T₀", "Zero of the Celsius scale", "K", lit("273.15")),
        NamedConstant("Vm", "V_m", "Molar volume of an ideal gas (273.15 K, 101.325 kPa)", "m³/mol", lit("0.022413969545014"))
    )

    private val byName: Map<String, NamedConstant> = all.associateBy { it.name }

    fun find(name: String): NamedConstant? = byName[name]
    fun valueOf(name: String): CalcValue? = byName[name]?.value
    fun names(): Set<String> = byName.keys
}
