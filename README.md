# Root Cause Analysis — Hack 2026 Level 1 Submission

A deterministic, headless ecological simulation engine built in **Java 26**, specialised for massive Monte Carlo optimisation. The engine models a 50 x 50 greenhouse as primitive 2D arrays (`short[][]`, `byte[][]`) to keep memory footprint tiny so that thousands of full-simulation rollouts can be evaluated in seconds.

> **Why "Root Cause Analysis"?** Every simulation result is fully attributable: identical inputs + identical seed = byte-for-byte identical `solution.json`. No wall-clock timing, no ambient randomness, no hidden mutable global state ever influences an outcome.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Prerequisites](#prerequisites)
3. [Quick Start](#quick-start)
4. [Judging Team: Reproduce the Solution Deterministically](#judging-team-reproduce-the-solution-deterministically)
5. [Monte Carlo Optimisation Strategy](#monte-carlo-optimisation-strategy)
6. [Solution Output Format](#solution-output-format)
7. [Project Layout](#project-layout)
8. [Test Suite](#test-suite)
9. [Submission Packaging](#submission-packaging)

---

## Architecture Overview

The engine is a **deterministic, headless tick-based simulator**. It never renders graphics and never consults the system clock; every number it produces is derived either from the input state file or from an explicitly provided integer seed.

### Core principle: primitive 2D arrays

The entire world state lives in flat primitive arrays allocated once at construction:

| Grid | Java type | Contents |
|---|---|---|
| `plantPopulationGrid` | `short[][]` | Plant index per cell (`0` = dead matter) |
| `plantAgeInTicksGrid` | `short[][]` | Maturation clock per cell |
| `cellularNutrientCapacityGrid` | `byte[][]` | Nutrient points per cell (`0..100`) |
| `environmentalSoilTypeGrid` | `byte[][]` | Soil classification per cell |
| `environmentalTerrainTypeGrid` | `byte[][]` | Terrain classification per cell |

A 50 x 50 world therefore occupies around **35 KB of contiguous-array memory**, far below what object-per-cell designs would require. That leanness is what makes **thousands of Monte Carlo rollouts** practical in a judging environment.

### Deterministic tick pipeline

Each simulation tick executes exactly three phases, in fixed order:

1. **Season update** — the `currentEnvironmentalSeason` (default `Spring`) is advanced from the `season` commands applicable up to the current tick (`Summer`@100, `Autumn`@200, `Winter`@300, `Spring`@400).
2. **Biological lifecycle** (`BiologicalLifecycleProcessor`) — every live plant drains exactly **one** nutrient point; a plant whose nutrients hit zero dies (index reset to `0`). Dead-matter cells regenerate **one** nutrient point per tick, capped at a strict maximum of `100`. Surviving plants increment their age.
3. **Spread phase** (`SimulationTickEngine`) — every mature plant resolves its spread targets by its propagation geometry and occupies them. **Collisions are resolved deterministically**: when two plants target the same cell, the plant that executes its spread action last in the sequential (row-major) loop overwrites the cell. A plant carrying the `no_winter_spread` weakness produces **no** targets while the season is `Winter`.

Because phase order, geometries, collision handling and season transitions are all canned rules, the same seed reproduces the same final world state with bit-level fidelity.

---

## Prerequisites

- **Java 26 JDK** (Temurin or OpenJDK), with **preview features enabled** at the runtime/compiler level.
- **Maven** 3.9 or newer.
- **`zip`** command-line utility (only required by the submission packaging script).

Verify the toolchain:

```bash
java -version   # must report a 26.x runtime
mvn -version    # must report Maven 3.9.x on Java 26
```

---

## Quick Start

### 1. Compile the engine

```bash
mvn -f level_1_greenhouse/pom.xml clean compile
```

### 2. Run the full test suite (42 tests)

```bash
mvn -f level_1_greenhouse/pom.xml test
```

Expected tail output:

```
Tests run: 42, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 3. Run the SimulationOptimizer (defaults)

```bash
mvn -f level_1_greenhouse/pom.xml clean compile exec:java
```

Default arguments used when none are supplied:

- Input state : `shared_data/level_one_state.json`
- Output state: `level_1_greenhouse/outputs/solution.json`
- Seeds compared: `32`

## Judging Team: Reproduce the Solution Deterministically

The archived submission is laid out as a single folder `Siphosakhe_Msimango_Level1/`. From the **repository root**, the reproducibility sequence is:

```bash
# 1. Compile
mvn -f level_1_greenhouse/pom.xml clean compile

# 2. Generate solution.json deterministically (explicit arguments shown for transparency)
mvn -f level_1_greenhouse/pom.xml exec:java \
    -Dexec.args="shared_data/level_one_state.json level_1_greenhouse/outputs/solution.json 32"
```

The `exec:java` goal is pre-configured in `level_1_greenhouse/pom.xml` with `mainClass=com.photospheria.engine.level1.simulation.SimulationOptimizer`, so no `-Dexec.mainClass` is needed.

Arguments to `SimulationOptimizer` (all optional, in order):

| Position | Parameter | Default |
|---|---|---|
| 1 | Path to the level state input JSON | `shared_data/level_one_state.json` |
| 2 | Path to write `solution.json` | `level_1_greenhouse/outputs/solution.json` |
| 3 | Number of Monte Carlo seeds to compare | `32` |

**Reproducibility guarantee:** running the command twice produces two `solution.json` files with **identical SHA-256 hashes**. The winning seed index, final score, living plant count and both exported grids are all printed to stdout on completion.

---

## Monte Carlo Optimisation Strategy

`SimulationOptimizer` explores the planting-strategy space without violating determinism:

1. For each seed `s` in `1..N` (default `32`), a fixed pseudo-random generator (`java.util.Random`) seeded purely from `s` places `8` starter seedlings at deterministic coordinates.
2. The full deterministic engine is advanced for all `500` ticks against the real Level 1 input.
3. Each run is scored deterministically:

   ```
   score = finalLivingPlantCount * 100 + finalTotalNutrientPoints
   ```

4. The highest-scoring run is serialised to `solution.json` alongside its seed index, so the exact winning rollout can be replayed and audited.

Raising the seed count scales linearly and is a trivial way to deepen the search:

```bash
mvn -f level_1_greenhouse/pom.xml exec:java \
    -Dexec.args="shared_data/level_one_state.json level_1_greenhouse/outputs/solution.json 4096"
```

---

## Solution Output Format

`solution.json` uses a fixed, insertion-ordered schema (no HashMap iteration order can leak non-determinism):

| Key | Type | Meaning |
|---|---|---|
| `level_number` | `int` | Always `1` |
| `deterministic_reproducibility` | `boolean` | Always `true` |
| `total_simulation_ticks_advanced` | `int` | Ticks simulated (from input) |
| `optimization_seed_count_compared` | `long` | Monte Carlo seeds evaluated |
| `winning_optimization_seed` | `long` | Seed owning the best run |
| `final_score` | `long` | Best run's fitness score |
| `final_living_plant_count` | `int` | Living cells in the winning final state |
| `final_total_nutrient_points` | `long` | Sum of nutrient points in the winning final state |
| `final_plant_population_grid` | `int[][]` | Row-major plant-index grid (`0` = dead) |
| `final_cellular_nutrient_grid` | `int[][]` | Row-major nutrient-point grid |

---

## Project Layout

```
.
├── README.md                                    # this document
├── create_submission_zip.sh                     # produces Siphosakhe_Msimango_Level1.zip
├── shared_data/
│   └── level_one_state.json                     # official Level 1 input fixture (verbatim)
└── level_1_greenhouse/
    ├── pom.xml                                  # single Maven module (Java 26, preview on)
    ├── inputs/                                  # unpacked judging inputs (scaffold)
    ├── outputs/
    │   └── solution.json                        # generated by SimulationOptimizer
    ├── .gitignore
    └── src/
        ├── main/java/com/photospheria/engine/level1/
        │   ├── configuration/
        │   │   ├── LevelState.java              # parsed root input state
        │   │   ├── CellConfiguration.java       # per-cell terrain/soil
        │   │   └── SimulationCommand.java       # tick-scheduled commands (season)
        │   └── simulation/
        │       ├── SimulationGridState.java     # primitive-array world state
        │       ├── BiologicalLifecycleProcessor.java
        │       ├── SimulationTickEngine.java    # seasons, geometries, collisions
        │       └── SimulationOptimizer.java     # entry point (main class)
        └── test/java/com/photospheria/engine/level1/
            ├── configuration/LevelStateParsingTest.java
            └── simulation/
                ├── SimulationGridStateTest.java
                ├── BiologicalCycleTest.java
                ├── SimulationTickEngineSpreadTest.java
                └── SeasonAndCollisionTest.java
```

---

## Test Suite

All tests are JUnit 5, executed by Maven Surefire (`ARGLINE=--enable-preview`). **42 tests, all green:**

| Test class | Covers | Tests |
|---|---|---|
| `LevelStateParsingTest` | JSON parsing, records, validation | 7 |
| `SimulationGridStateTest` | Primitive grids, bounds, unsigned reads, nutrient range | 8 |
| `BiologicalCycleTest` | Nutrient drain, death, regeneration, maturation gating | 8 |
| `SimulationTickEngineSpreadTest` | VonNeumann/Moore/Row/Column/CrossHatch geometry | 12 |
| `SeasonAndCollisionTest` | Season commands, `no_winter_spread`, collision resolution | 7 |

Run with:

```bash
mvn -f level_1_greenhouse/pom.xml test
```

---

## Submission Packaging

From the repository root, package the submission:

```bash
./create_submission_zip.sh
```

This produces **`Siphosakhe_Msimango_Level1.zip`** in the repository root. The archive:

- Runs `mvn clean package` (compiles + runs all 42 tests) first.
- Regenerates `solution.json` if it is missing.
- Includes `level_1_greenhouse/` (with `src/`, `pom.xml` and the generated `solution.json`), `shared_data/`, and `README.md`.
- **Excludes** `target/`, `.git/`, `.idea/`, and `.vscode/` trees to keep the file size minimal.

---

*Submitted by Siphosakhe Msimango — Hack 2026 Level 1 — Root Cause Analysis.*