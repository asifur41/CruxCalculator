# Constants and unit conversions

Crux Calculator contains every constant and conversion of the Casio fx-991EX list (47 constants, 40 conversion
directions), all offline.

## How to use them
* **Constants**: tap SHIFT then 7 (CONST) on the scientific keypad, or open Tools > Constants. Tap a row to use it.
  You can also type the short name in any calculation: `2*c0`, `Z0/RK`. Names are case-sensitive, and a variable you
  define with the same name wins (`c0 = 5` still works).
* **Conversions**: open the Convert tab (or SHIFT then 8 on the scientific keypad), or type them in a calculation:
  `1 pc to km`, `1 hp to kW`, `98066.5 Pa to kgf/cm2`.

## Constants (short name = Casio symbol)
| Name | Casio | Meaning | Name | Casio | Meaning |
|---|---|---|---|---|---|
| `hP` | h | Planck constant | `amu` | u | Atomic mass constant |
| `hbar` | ħ | Reduced Planck constant | `Fara` | F | Faraday constant |
| `c0` | c | Speed of light | `NA` | N_A | Avogadro constant |
| `eps0` | ε₀ | Vacuum permittivity | `kB` | k | Boltzmann constant |
| `mu0` | μ₀ | Vacuum permeability | `Vm` | V_m | Molar volume of ideal gas |
| `Z0` | Z₀ | Impedance of vacuum | `Rgas` | R | Molar gas constant |
| `Gn` | G | Gravitation constant | `cr1` | c₁ | First radiation constant |
| `lP` | l_P | Planck length | `cr2` | c₂ | Second radiation constant |
| `tP` | t_P | Planck time | `sigmaSB` | σ | Stefan-Boltzmann constant |
| `muN` | μ_N | Nuclear magneton | `g0` | g | Standard gravity |
| `muB` | μ_B | Bohr magneton | `atm` | atm | Standard atmosphere |
| `qe` | e | Elementary charge | `KJos90` | K_J-90 | Conventional Josephson constant |
| `Phi0` | Φ₀ | Magnetic flux quantum | `RK90` | R_K-90 | Conventional von Klitzing constant |
| `G0` | G₀ | Conductance quantum | `T0` | t | Celsius zero (273.15 K) |
| `KJos` | K_J | Josephson constant | `mp` | m_p | Proton mass |
| `RK` | R_K | von Klitzing constant | `mn` | m_n | Neutron mass |
| `me` | m_e | Electron mass | `mmu` | m_μ | Muon mass |
| `mtau` | m_τ | Tau mass | `a0` | a₀ | Bohr radius |
| `alpha` | α | Fine-structure constant | `relec` | r_e | Classical electron radius |
| `lamC` | λ_C | Electron Compton wavelength | `lamCp` | λ_C,p | Proton Compton wavelength |
| `lamCn` | λ_C,n | Neutron Compton wavelength | `Ry` | R∞ | Rydberg constant |
| `mup` | μ_p | Proton magnetic moment | `mue` | μ_e | Electron magnetic moment |
| `mun` | μ_n | Neutron magnetic moment | `mumu` | μ_μ | Muon magnetic moment |
| `gammap` | γ_p | Proton gyromagnetic ratio | `ke` | k_e | Coulomb constant (extra, not on the Casio list) |

Values are CODATA 2018. Phi0, G0, KJos, RK, cr1 and cr2 follow exactly from the SI-defining constants, so they are
calculated, not typed in. `relec` is used instead of `re` because `re(...)` is the real-part function.

## Conversions added in update 5
| Conversion | Type it as |
|---|---|
| parsec <-> kilometre | `1 pc to km`, `1 km to pc` (also light-year `ly`, astronomical unit `au`) |
| kgf/cm² <-> pascal | `1 kgf/cm2 to Pa`, `1 Pa to kgf/cm2` |
| horsepower <-> kilowatt | `1 hp to kW`, `1 kW to hp` (new Power category: W, kW, MW, hp, PS) |
| kgf·m <-> joule | `1 kgf_m to J`, `1 J to kgf_m` |

The other 32 directions (inch, foot, yard, mile, nautical mile, acre, gallon US/UK, ounce, pound, km/h, atm, mmHg,
psi, calorie, °C/°F ...) were already in the app. `hp` is the mechanical horsepower (745.69987158 W), the same one
Casio uses; `PS` is the metric horsepower (735.49875 W).
