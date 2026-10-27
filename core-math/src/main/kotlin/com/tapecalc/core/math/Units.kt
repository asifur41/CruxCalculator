package com.tapecalc.core.math

import com.tapecalc.core.precision.BigMath
import com.tapecalc.core.precision.Rational
import java.math.BigDecimal
import java.math.MathContext

enum class UnitCategory(val title: String) {
    LENGTH("Length"), MASS("Mass"), VOLUME("Volume"), TEMPERATURE("Temperature"),
    SPEED("Speed"), AREA("Area"), DATA("Data"), PRESSURE("Pressure"),
    ENERGY("Energy"), TIME("Time"), ANGLE("Angle"), FUEL("Fuel economy")
}

/**
 * A unit defined relative to its category's base unit:
 *   linear:      base = value * factor + offset
 *   reciprocal:  base = factor / value   (fuel economy: mpg <-> L/100km)
 */
class UnitDef internal constructor(
    val id: String,
    val category: UnitCategory,
    val name: String,
    val symbol: String,
    val aliases: List<String>,
    private val factor: Rational,
    private val offset: Rational = Rational.ZERO,
    private val reciprocal: Boolean = false
) {
    fun toBase(v: Rational): Rational {
        if (reciprocal) {
            if (v.isZero) throw CalcException(ErrorKind.DIVISION_BY_ZERO, "Cannot convert a value of zero in $symbol")
            return factor / v
        }
        return v * factor + offset
    }

    fun fromBase(b: Rational): Rational {
        if (reciprocal) {
            if (b.isZero) throw CalcException(ErrorKind.DIVISION_BY_ZERO, "This conversion is undefined for zero")
            return factor / b
        }
        return (b - offset) / factor
    }
}

/** Offline unit tables. Factors are exact rationals, so 1 km -> m is exactly 1000. */
object UnitRegistry {

    private fun r(s: String) = Rational.parse(s)

    private fun u(
        cat: UnitCategory, id: String, name: String, symbol: String, factor: Rational,
        vararg aliases: String, offset: Rational = Rational.ZERO, reciprocal: Boolean = false
    ): UnitDef = UnitDef(
        id, cat, name, symbol,
        (listOf(symbol, id) + aliases.toList()).distinct(),
        factor, offset, reciprocal
    )

    val all: List<UnitDef> by lazy { build() }

    private fun build(): List<UnitDef> {
        val L = UnitCategory.LENGTH
        val M = UnitCategory.MASS
        val V = UnitCategory.VOLUME
        val T = UnitCategory.TEMPERATURE
        val S = UnitCategory.SPEED
        val A = UnitCategory.AREA
        val D = UnitCategory.DATA
        val P = UnitCategory.PRESSURE
        val E = UnitCategory.ENERGY
        val TM = UnitCategory.TIME
        val AN = UnitCategory.ANGLE
        val F = UnitCategory.FUEL
        val radFactor = Rational.fromBigDecimal(BigDecimal(180).divide(BigMath.pi, MathContext(60)))
        val ninetyNine = r("459.67")
        val fiveNinths = Rational.of(5, 9)
        return listOf(
            // ---- length (base: meter)
            u(L, "m", "meter", "m", r("1"), "meter", "meters", "metre", "metres"),
            u(L, "km", "kilometer", "km", r("1000"), "kilometer", "kilometers", "kilometre", "kilometres"),
            u(L, "cm", "centimeter", "cm", r("0.01"), "centimeter", "centimeters", "centimetre", "centimetres"),
            u(L, "mm", "millimeter", "mm", r("0.001"), "millimeter", "millimeters", "millimetre", "millimetres"),
            u(L, "um", "micrometer", "µm", r("0.000001"), "um", "micron", "microns", "micrometer", "micrometers"),
            u(L, "nm", "nanometer", "nm", r("0.000000001"), "nanometer", "nanometers"),
            u(L, "mi", "mile", "mi", r("1609.344"), "mile", "miles"),
            u(L, "yd", "yard", "yd", r("0.9144"), "yard", "yards"),
            u(L, "ft", "foot", "ft", r("0.3048"), "foot", "feet"),
            u(L, "in", "inch", "in", r("0.0254"), "inch", "inches"),
            u(L, "nmi", "nautical mile", "nmi", r("1852"), "nautical mile", "nautical miles"),
            // ---- mass (base: kilogram)
            u(M, "kg", "kilogram", "kg", r("1"), "kilogram", "kilograms", "kilo", "kilos"),
            u(M, "g", "gram", "g", r("0.001"), "gram", "grams"),
            u(M, "mg", "milligram", "mg", r("0.000001"), "milligram", "milligrams"),
            u(M, "ug", "microgram", "µg", r("0.000000001"), "ug", "mcg", "microgram", "micrograms"),
            u(M, "t", "metric ton", "t", r("1000"), "tonne", "tonnes", "metric ton", "metric tons"),
            u(M, "lb", "pound", "lb", r("0.45359237"), "lbs", "pound", "pounds"),
            u(M, "oz", "ounce", "oz", r("0.028349523125"), "ounce", "ounces"),
            u(M, "st", "stone", "st", r("6.35029318"), "stone", "stones"),
            u(M, "ton", "US ton", "ton", r("907.18474"), "tons", "short ton", "short tons"),
            // ---- volume (base: liter)
            u(V, "L", "liter", "L", r("1"), "l", "liter", "liters", "litre", "litres"),
            u(V, "mL", "milliliter", "mL", r("0.001"), "ml", "milliliter", "milliliters", "millilitre", "millilitres"),
            u(V, "m3", "cubic meter", "m³", r("1000"), "m3", "cubic meter", "cubic meters"),
            u(V, "cm3", "cubic centimeter", "cm³", r("0.001"), "cm3", "cc", "cubic centimeter", "cubic centimeters"),
            u(V, "gal", "US gallon", "gal", r("3.785411784"), "gallon", "gallons", "us gal"),
            u(V, "qt", "US quart", "qt", r("0.946352946"), "quart", "quarts"),
            u(V, "pt", "US pint", "pt", r("0.473176473"), "pint", "pints"),
            u(V, "cup", "US cup", "cup", r("0.2365882365"), "cups"),
            u(V, "floz", "US fluid ounce", "fl oz", r("0.0295735295625"), "floz", "fluid ounce", "fluid ounces"),
            u(V, "tbsp", "tablespoon", "tbsp", r("0.01478676478125"), "tablespoon", "tablespoons"),
            u(V, "tsp", "teaspoon", "tsp", r("0.00492892159375"), "teaspoon", "teaspoons"),
            u(V, "impgal", "imperial gallon", "imp gal", r("4.54609"), "uk gal", "imperial gallon", "imperial gallons"),
            // ---- temperature (base: kelvin)
            u(T, "K", "kelvin", "K", r("1"), "k", "kelvin"),
            u(T, "C", "Celsius", "°C", r("1"), "C", "c", "celsius", "degC", "°c", offset = r("273.15")),
            u(T, "F", "Fahrenheit", "°F", fiveNinths, "F", "f", "fahrenheit", "degF", "°f", offset = ninetyNine * fiveNinths),
            // ---- speed (base: m/s)
            u(S, "mps", "meter per second", "m/s", r("1"), "mps", "meters per second"),
            u(S, "kmh", "kilometer per hour", "km/h", Rational.of(5, 18), "kph", "kmh", "km/hr", "kmph", "kilometers per hour"),
            u(S, "mph", "mile per hour", "mph", r("0.44704"), "mi/h", "miles per hour"),
            u(S, "kn", "knot", "kn", Rational.of(1852, 3600), "kt", "knot", "knots"),
            u(S, "fps", "foot per second", "ft/s", r("0.3048"), "fps", "feet per second"),
            // ---- area (base: square meter)
            u(A, "m2", "square meter", "m²", r("1"), "m2", "m^2", "sqm", "square meter", "square meters"),
            u(A, "km2", "square kilometer", "km²", r("1000000"), "km2", "km^2", "square kilometer", "square kilometers"),
            u(A, "cm2", "square centimeter", "cm²", r("0.0001"), "cm2", "cm^2", "square centimeter"),
            u(A, "mm2", "square millimeter", "mm²", r("0.000001"), "mm2", "mm^2"),
            u(A, "ha", "hectare", "ha", r("10000"), "hectare", "hectares"),
            u(A, "ac", "acre", "ac", r("4046.8564224"), "acre", "acres"),
            u(A, "ft2", "square foot", "ft²", r("0.09290304"), "ft2", "ft^2", "sqft", "square foot", "square feet"),
            u(A, "yd2", "square yard", "yd²", r("0.83612736"), "yd2", "yd^2", "square yard", "square yards"),
            u(A, "mi2", "square mile", "mi²", r("2589988.110336"), "mi2", "mi^2", "square mile", "square miles"),
            u(A, "in2", "square inch", "in²", r("0.00064516"), "in2", "in^2", "sqin", "square inch", "square inches"),
            // ---- data (base: byte)
            u(D, "bit", "bit", "bit", Rational.of(1, 8), "bits"),
            u(D, "B", "byte", "B", r("1"), "byte", "bytes"),
            u(D, "KB", "kilobyte", "KB", r("1000"), "kB", "kilobyte", "kilobytes"),
            u(D, "MB", "megabyte", "MB", r("1000000"), "megabyte", "megabytes"),
            u(D, "GB", "gigabyte", "GB", r("1000000000"), "gigabyte", "gigabytes"),
            u(D, "TB", "terabyte", "TB", r("1000000000000"), "terabyte", "terabytes"),
            u(D, "PB", "petabyte", "PB", r("1000000000000000"), "petabyte", "petabytes"),
            u(D, "KiB", "kibibyte", "KiB", r("1024"), "kibibyte", "kibibytes"),
            u(D, "MiB", "mebibyte", "MiB", r("1048576"), "mebibyte", "mebibytes"),
            u(D, "GiB", "gibibyte", "GiB", r("1073741824"), "gibibyte", "gibibytes"),
            u(D, "TiB", "tebibyte", "TiB", r("1099511627776"), "tebibyte", "tebibytes"),
            u(D, "Kb", "kilobit", "Kb", r("125"), "kbit", "kilobit", "kilobits"),
            u(D, "Mb", "megabit", "Mb", r("125000"), "Mbit", "megabit", "megabits"),
            u(D, "Gb", "gigabit", "Gb", r("125000000"), "Gbit", "gigabit", "gigabits"),
            // ---- pressure (base: pascal)
            u(P, "Pa", "pascal", "Pa", r("1"), "pascal", "pascals"),
            u(P, "hPa", "hectopascal", "hPa", r("100"), "hectopascal"),
            u(P, "kPa", "kilopascal", "kPa", r("1000"), "kilopascal", "kilopascals"),
            u(P, "MPa", "megapascal", "MPa", r("1000000"), "megapascal"),
            u(P, "bar", "bar", "bar", r("100000"), "bars"),
            u(P, "mbar", "millibar", "mbar", r("100"), "millibar", "millibars"),
            u(P, "atm", "atmosphere", "atm", r("101325"), "atmosphere", "atmospheres"),
            u(P, "psi", "pound per square inch", "psi", r("6894.757293168"), "pounds per square inch"),
            u(P, "mmHg", "millimeter of mercury", "mmHg", r("133.322387415")),
            u(P, "torr", "torr", "Torr", Rational.of(101325, 760), "Torr"),
            u(P, "inHg", "inch of mercury", "inHg", r("3386.389")),
            // ---- energy (base: joule)
            u(E, "J", "joule", "J", r("1"), "joule", "joules"),
            u(E, "kJ", "kilojoule", "kJ", r("1000"), "kilojoule", "kilojoules"),
            u(E, "MJ", "megajoule", "MJ", r("1000000"), "megajoule", "megajoules"),
            u(E, "cal", "calorie", "cal", r("4.184"), "calorie", "calories"),
            u(E, "kcal", "kilocalorie", "kcal", r("4184"), "Cal", "kilocalorie", "kilocalories"),
            u(E, "Wh", "watt hour", "Wh", r("3600"), "watt hour", "watt hours"),
            u(E, "kWh", "kilowatt hour", "kWh", r("3600000"), "kilowatt hour", "kilowatt hours"),
            u(E, "eV", "electronvolt", "eV", r("0.0000000000000000001602176634"), "electronvolt"),
            u(E, "BTU", "British thermal unit", "BTU", r("1055.05585262"), "btu"),
            // ---- time (base: second)
            u(TM, "s", "second", "s", r("1"), "sec", "secs", "second", "seconds"),
            u(TM, "ms", "millisecond", "ms", r("0.001"), "millisecond", "milliseconds"),
            u(TM, "us", "microsecond", "µs", r("0.000001"), "us", "microsecond", "microseconds"),
            u(TM, "ns", "nanosecond", "ns", r("0.000000001"), "nanosecond", "nanoseconds"),
            u(TM, "min", "minute", "min", r("60"), "mins", "minute", "minutes"),
            u(TM, "h", "hour", "h", r("3600"), "hr", "hrs", "hour", "hours"),
            u(TM, "d", "day", "d", r("86400"), "day", "days"),
            u(TM, "wk", "week", "wk", r("604800"), "week", "weeks"),
            u(TM, "mo", "month", "mo", r("2629746"), "month", "months"),
            u(TM, "yr", "year", "yr", r("31557600"), "y", "year", "years"),
            // ---- angle (base: degree)
            u(AN, "deg", "degree", "°", r("1"), "deg", "degree", "degrees"),
            u(AN, "rad", "radian", "rad", radFactor, "radian", "radians"),
            u(AN, "grad", "gradian", "grad", r("0.9"), "gon", "gradian", "gradians"),
            u(AN, "turn", "turn", "turn", r("360"), "turns", "rev", "revolution", "revolutions"),
            u(AN, "arcmin", "arcminute", "arcmin", Rational.of(1, 60), "arcminute", "arcminutes"),
            u(AN, "arcsec", "arcsecond", "arcsec", Rational.of(1, 3600), "arcsecond", "arcseconds"),
            // ---- fuel economy (base: liters per 100 km)
            u(F, "l100", "liters per 100 km", "L/100km", r("1"), "l/100km", "lp100km"),
            u(F, "mpg", "miles per gallon (US)", "mpg", r("378.5411784") / r("1.609344"), "mpg us", "mpg(us)", "mi/gal", reciprocal = true),
            u(F, "mpguk", "miles per gallon (UK)", "mpg UK", r("454.609") / r("1.609344"), "mpg uk", "mpg(uk)", "mpgi", reciprocal = true),
            u(F, "kml", "kilometers per liter", "km/L", r("100"), "km/l", "kmpl", reciprocal = true)
        )
    }

    private val exactIndex: Map<String, UnitDef> by lazy {
        val m = LinkedHashMap<String, UnitDef>()
        for (unit in all) for (alias in unit.aliases) m.putIfAbsent(alias, unit)
        m
    }

    /** Lower-cased aliases that point to exactly one unit (ambiguous ones such as "mb" are dropped). */
    private val lowerIndex: Map<String, UnitDef> by lazy {
        val groups = HashMap<String, MutableSet<UnitDef>>()
        for (unit in all) for (alias in unit.aliases) groups.getOrPut(alias.lowercase()) { mutableSetOf() }.add(unit)
        groups.filterValues { it.size == 1 }.mapValues { it.value.first() }
    }

    private val exactAliasesByLength: List<String> by lazy { exactIndex.keys.sortedByDescending { it.length } }
    private val lowerAliasesByLength: List<String> by lazy { lowerIndex.keys.sortedByDescending { it.length } }

    fun byCategory(category: UnitCategory): List<UnitDef> = all.filter { it.category == category }

    fun find(text: String): UnitDef? {
        val t = text.trim()
        if (t.isEmpty()) return null
        return exactIndex[t] ?: lowerIndex[t.lowercase()]
    }

    private fun boundaryOk(text: String, aliasLength: Int): Boolean =
        text.length == aliasLength || !text[text.length - aliasLength - 1].isLetter()

    /** Finds a unit at the end of [text]; returns it with the remaining expression text. */
    fun matchSuffix(text: String): Pair<UnitDef, String>? {
        val t = text.trimEnd()
        if (t.isEmpty()) return null
        for (alias in exactAliasesByLength) {
            if (t.endsWith(alias) && boundaryOk(t, alias.length)) {
                return exactIndex.getValue(alias) to t.substring(0, t.length - alias.length).trimEnd()
            }
        }
        val lower = t.lowercase()
        if (lower.length == t.length) {
            for (alias in lowerAliasesByLength) {
                if (lower.endsWith(alias) && boundaryOk(lower, alias.length)) {
                    return lowerIndex.getValue(alias) to t.substring(0, t.length - alias.length).trimEnd()
                }
            }
        }
        return null
    }

    fun convert(value: CalcValue, from: UnitDef, to: UnitDef): CalcValue {
        if (from.category != to.category) {
            throw CalcException(
                ErrorKind.UNIT_MISMATCH,
                "Cannot convert ${from.category.title.lowercase()} (${from.symbol}) to ${to.category.title.lowercase()} (${to.symbol})"
            )
        }
        val v = value.exact ?: Rational.fromBigDecimal(value.decimal)
        return CalcValue.ofRational(to.fromBase(from.toBase(v)))
    }
}
