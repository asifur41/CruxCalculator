package com.tapecalc.core.math

import com.tapecalc.core.precision.BigMath
import java.math.BigDecimal

/** A physical constant that can be typed by name (`c0`, `hP`, `NA`, ...). */
class NamedConstant(
    val name: String,
    val symbol: String,
    val description: String,
    val unit: String,
    /** Section it is listed under in the Constants tool (same sections as the Casio fx-991EX list). */
    val group: String,
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

    private const val UNIVERSAL = "Universal"
    private const val ELECTRO = "Electromagnetic"
    private const val ATOMIC = "Atomic and nuclear"
    private const val PHYSCHEM = "Physico-chemical"
    private const val ADOPTED = "Adopted values"

    private val H = BigDecimal("6.62607015E-34")
    private val QE = BigDecimal("1.602176634E-19")
    private val C = BigDecimal("299792458")
    private val KB = BigDecimal("1.380649E-23")
    private val MC = com.tapecalc.core.precision.Precision.INTERNAL

    /** For constants that follow exactly from the defining ones (h, e, c, k): worked out, not typed in. */
    private fun derived(f: () -> BigDecimal): () -> CalcValue = { CalcValue.ofDecimal(f()) }

    val all: List<NamedConstant> = listOf(
        // ---- universal
        NamedConstant("hP", "h", "Planck constant", "J·s", UNIVERSAL, lit("6.62607015E-34")),
        NamedConstant("hbar", "ħ", "Reduced Planck constant h/2π", "J·s", UNIVERSAL,
            derived { H.divide(BigMath.pi.multiply(BigDecimal(2)), MC) }),
        NamedConstant("c0", "c", "Speed of light in vacuum", "m/s", UNIVERSAL, lit("299792458")),
        NamedConstant("eps0", "ε₀", "Vacuum permittivity", "F/m", UNIVERSAL, lit("8.8541878128E-12")),
        NamedConstant("mu0", "μ₀", "Vacuum permeability", "N/A²", UNIVERSAL, lit("1.25663706212E-6")),
        NamedConstant("Z0", "Z₀", "Characteristic impedance of vacuum", "Ω", UNIVERSAL, lit("376.730313668")),
        NamedConstant("Gn", "G", "Newtonian constant of gravitation", "m³/(kg·s²)", UNIVERSAL, lit("6.67430E-11")),
        NamedConstant("lP", "l_P", "Planck length", "m", UNIVERSAL, lit("1.616255E-35")),
        NamedConstant("tP", "t_P", "Planck time", "s", UNIVERSAL, lit("5.391247E-44")),
        NamedConstant("ke", "k_e", "Coulomb constant 1/(4πε₀)", "N·m²/C²", UNIVERSAL, lit("8.9875517923E9")),

        // ---- electromagnetic
        NamedConstant("muN", "μ_N", "Nuclear magneton", "J/T", ELECTRO, lit("5.0507837461E-27")),
        NamedConstant("muB", "μ_B", "Bohr magneton", "J/T", ELECTRO, lit("9.2740100783E-24")),
        NamedConstant("qe", "e", "Elementary charge", "C", ELECTRO, lit("1.602176634E-19")),
        NamedConstant("Phi0", "Φ₀", "Magnetic flux quantum h/2e", "Wb", ELECTRO, derived { H.divide(QE.multiply(BigDecimal(2)), MC) }),
        NamedConstant("G0", "G₀", "Conductance quantum 2e²/h", "S", ELECTRO,
            derived { QE.multiply(QE).multiply(BigDecimal(2)).divide(H, MC) }),
        NamedConstant("KJos", "K_J", "Josephson constant 2e/h", "Hz/V", ELECTRO, derived { QE.multiply(BigDecimal(2)).divide(H, MC) }),
        NamedConstant("RK", "R_K", "von Klitzing constant h/e²", "Ω", ELECTRO, derived { H.divide(QE.multiply(QE), MC) }),

        // ---- atomic and nuclear
        NamedConstant("mp", "m_p", "Proton rest mass", "kg", ATOMIC, lit("1.67262192369E-27")),
        NamedConstant("mn", "m_n", "Neutron rest mass", "kg", ATOMIC, lit("1.67492749804E-27")),
        NamedConstant("me", "m_e", "Electron rest mass", "kg", ATOMIC, lit("9.1093837015E-31")),
        NamedConstant("mmu", "m_μ", "Muon rest mass", "kg", ATOMIC, lit("1.883531627E-28")),
        NamedConstant("a0", "a₀", "Bohr radius", "m", ATOMIC, lit("5.29177210903E-11")),
        NamedConstant("alpha", "α", "Fine-structure constant", "", ATOMIC, lit("7.2973525693E-3")),
        NamedConstant("relec", "r_e", "Classical electron radius", "m", ATOMIC, lit("2.8179403262E-15")),
        NamedConstant("lamC", "λ_C", "Compton wavelength (electron)", "m", ATOMIC, lit("2.42631023867E-12")),
        NamedConstant("lamCp", "λ_C,p", "Proton Compton wavelength", "m", ATOMIC, lit("1.32140985539E-15")),
        NamedConstant("Ry", "R∞", "Rydberg constant", "1/m", ATOMIC, lit("10973731.568160")),
        NamedConstant("mup", "μ_p", "Proton magnetic moment", "J/T", ATOMIC, lit("1.41060679736E-26")),
        NamedConstant("mue", "μ_e", "Electron magnetic moment", "J/T", ATOMIC, lit("-9.2847647043E-24")),
        NamedConstant("mun", "μ_n", "Neutron magnetic moment", "J/T", ATOMIC, lit("-9.6623651E-27")),
        NamedConstant("mumu", "μ_μ", "Muon magnetic moment", "J/T", ATOMIC, lit("-4.49044830E-26")),
        NamedConstant("mtau", "m_τ", "Tau rest mass", "kg", ATOMIC, lit("3.16754E-27")),
        NamedConstant("lamCn", "λ_C,n", "Neutron Compton wavelength", "m", ATOMIC, lit("1.31959090581E-15")),
        NamedConstant("gammap", "γ_p", "Proton gyromagnetic ratio", "1/(s·T)", ATOMIC, lit("2.6752218744E8")),

        // ---- physico-chemical
        NamedConstant("amu", "u", "Atomic mass constant", "kg", PHYSCHEM, lit("1.66053906660E-27")),
        NamedConstant("Fara", "F", "Faraday constant", "C/mol", PHYSCHEM, lit("96485.33212331")),
        NamedConstant("NA", "N_A", "Avogadro constant", "1/mol", PHYSCHEM, lit("6.02214076E23")),
        NamedConstant("kB", "k", "Boltzmann constant", "J/K", PHYSCHEM, lit("1.380649E-23")),
        NamedConstant("Vm", "V_m", "Molar volume of an ideal gas (273.15 K, 101.325 kPa)", "m³/mol", PHYSCHEM, lit("0.022413969545014")),
        NamedConstant("Rgas", "R", "Molar gas constant", "J/(mol·K)", PHYSCHEM, lit("8.31446261815324")),
        NamedConstant("cr1", "c₁", "First radiation constant 2πhc²", "W·m²", PHYSCHEM,
            derived { BigMath.pi.multiply(BigDecimal(2)).multiply(H).multiply(C).multiply(C) }),
        NamedConstant("cr2", "c₂", "Second radiation constant hc/k", "m·K", PHYSCHEM, derived { H.multiply(C).divide(KB, MC) }),
        NamedConstant("sigmaSB", "σ", "Stefan-Boltzmann constant", "W/(m²·K⁴)", PHYSCHEM, lit("5.670374419E-8")),

        // ---- adopted values
        NamedConstant("g0", "g", "Standard acceleration of gravity", "m/s²", ADOPTED, lit("9.80665")),
        NamedConstant("atm", "atm", "Standard atmosphere", "Pa", ADOPTED, lit("101325")),
        NamedConstant("KJos90", "K_J-90", "Conventional value of the Josephson constant", "Hz/V", ADOPTED, lit("4.835979E14")),
        NamedConstant("RK90", "R_K-90", "Conventional value of the von Klitzing constant", "Ω", ADOPTED, lit("25812.807")),
        NamedConstant("T0", "T₀", "Zero of the Celsius scale (t = 273.15 K)", "K", ADOPTED, lit("273.15"))
    )

    /** The groups in display order. */
    val groups: List<String> = listOf(UNIVERSAL, ELECTRO, ATOMIC, PHYSCHEM, ADOPTED)

    private val byName: Map<String, NamedConstant> = all.associateBy { it.name }

    fun find(name: String): NamedConstant? = byName[name]
    fun valueOf(name: String): CalcValue? = byName[name]?.value
    fun names(): Set<String> = byName.keys
}
