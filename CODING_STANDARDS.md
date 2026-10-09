# Coding standards

Judgement calls for review. Mechanical rules are Gradle checks instead (`./gradlew check`), such as
`checkGlossaryTerms` for the hard _Avoid_ terms of [GLOSSARY.md](GLOSSARY.md).

## Domain language
- Name things with the terms of [GLOSSARY.md](GLOSSARY.md). A concept it doesn't define gets a glossary entry
  before it gets a name in code (Nested Type came about that way).
- The softer _Avoid_ terms ("copy", "skip", "original") are fine as plain verbs, never as names of domain concepts.

## Docs agree with decisions
- A change that alters a decision of an ADR in [docs/adr](docs/adr) updates every KDoc, README line, catalogue comment
  and older ADR that states the old decision. Stale docs that contradict an ADR are a hard finding.
- A new decision gets a new ADR in the format of the existing ones: a decision title, a context paragraph,
  "Considered Options" and "Consequences".

## Tests
- Test names are backtick sentences that state the behaviour (`` `a missing Required Field returns a per-field 400` ``).
- A test helper copied into a third test class is extracted into a shared one.
- Behaviour a consumer sees is tested end to end in `test-app` (run by `TestAppFunctionalTest`), not only in processor unit tests.

## Code
- Annotations on constructor properties of `test-app` classes take explicit use-site targets (`@field:`, `@get:`).
- A comment states the why the code can't show, in one or two lines; KDoc on every public top-level declaration.
- Generated Mirrors compile without warnings: consumers may turn warnings into errors.
