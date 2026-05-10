# Contributing to TerraScribe

Thanks for your interest. TerraScribe is a small, opinionated project — this guide is short on purpose.

## Before you start

1. Read [`docs/SPEC.md`](docs/SPEC.md). It is the source of truth for what the mod is and how it is structured.
2. Skim [`CLAUDE.md`](CLAUDE.md) for the current milestone, active design decisions, and known issues.
3. Check [open issues](https://github.com/sammasr/terrascribe/issues) and the `[Unreleased]` section of [`CHANGELOG.md`](CHANGELOG.md) to see what's already in flight.

## Setup

You need:

- **Java 21** (Temurin LTS recommended — version 21.0.11 is what we test against). Install via [SDKMAN!](https://sdkman.io/) for clean isolation from system Java.
- **Git**.

Then:

```bash
git clone https://github.com/sammasr/terrascribe.git
cd terrascribe
./gradlew build       # downloads MC + NeoForge on first run; takes a few minutes
./gradlew runClient   # launch a dev Minecraft instance with TerraScribe loaded
```

## Code style

See [`docs/SPEC.md`](docs/SPEC.md) §10 (Coding Standards). Highlights:

- **Java 21**, modern idioms (records, sealed types, pattern matching, `var`).
- **Immutability by default.** Records for data carriers; `final` on fields and parameters.
- **No `null` in public APIs** — use `Optional` or sentinel objects.
- **Codec-everything** for serializable data. Roundtrip-test new codecs the same day.
- **`ResourceLocation.fromNamespaceAndPath`** — never the deprecated constructor.
- **SLF4J logging** via `LogUtils.getLogger()`. No `System.out.println`.
- **Magic numbers** become named constants with a comment explaining range and meaning.
- **Worldgen math is pure** — `worldgen.{noise,terrain,river,biome.climate}` packages contain zero Minecraft API references and are unit-testable on the JVM.

## Commits & PRs

- **One logical change per commit.** Use [Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`, `perf:`. The body explains *why* when not obvious.
- **Run `./gradlew build test`** before pushing.
- **Update `CHANGELOG.md`** under `[Unreleased]` for any user-visible change.
- **Credit references in commit messages** when an idea originated in TerraForged or ReTerraForged (e.g., `feat: implement hydraulic erosion (inspired by TerraForged/won_ton_)`). Do not copy code — write your own.

## Testing

- Unit tests for pure math packages live in `src/test/java/...`. Run `./gradlew test`.
- Integration tests use Minecraft's GameTest framework. Run `./gradlew runGameTestServer`.
- See [`docs/PLAYTEST.md`](docs/PLAYTEST.md) for the manual playtest checklist updated each milestone.

## License

By contributing you agree your work is licensed under the [MIT License](LICENSE).
