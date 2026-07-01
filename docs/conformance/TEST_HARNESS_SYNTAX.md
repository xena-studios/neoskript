# Skript test-runner syntax (excluded from conformance denominator)

These syntaxes live under `ch/njol/skript/test/runner/` in the Skript source. They exist only to
drive Skript's own JUnit/script test suite (assertions, parse-log capture, plural-classinfo probes,
internal test types). They are **not** part of the public scripting language that server scripts use,
so — like external-plugin syntax — they are excluded from the compatibility denominator.

| Entry | Skript source |
|-------|---------------|
| condition: check-junit | CondRunningJUnit.java |
| condition: method-exists | CondMethodExists.java |
| effect: assert | EffAssert.java |
| effect: debug | EffDebug.java |
| effect: objectives | EffObjectives.java |
| effect: test-plural-class-infos | EffTestPluralClassInfos.java |
| event: test-case | EvtTestCase.java |
| expression: experimental-only | ExprExperimentalOnly.java |
| expression: parse-logs | ExprParseLogs.java |
| expression: test-block / test-location / test-world / test-string-literal | Expr*.java |
| section: parse-section | SecParse.java |
| structure: parse-structure | StructParse.java |
| type: aardwolf / exemplus / hoof / testgui | EffTestPluralClassInfos.java (test-only class infos) |

## Experimental syntax (also excluded)

Syntax gated behind Skript `using <experiment>` flags is not stable scripting API and is excluded
from the denominator: the experimental `queue` type and its expressions, `is using experimental
feature`, `suppress type hints`, and the `using experimental feature` structure.

## Skript-internal machinery (also excluded)

Syntax that operates on Skript's own implementation rather than gameplay is excluded like the
test-runner and experimental syntax: the `classinfo`/`config`/`node` types and the `config`, `node`,
`value of subnode`, and `parse error` expressions (Skript config-file/AST introspection), the
`example` and `auto reload` structures (example/dev tooling), and the `catch runtime errors` section.
