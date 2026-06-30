package co.xenastudios.neoskript.testkit;

import co.xenastudios.neoskript.core.expression.VariableExpression;
import co.xenastudios.neoskript.core.parser.ScriptParser;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.runtime.EventRegistry;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;

/**
 * A small, dependency-free parser throughput benchmark — run manually with
 * {@code java -cp ... co.xenastudios.neoskript.testkit.ParseBenchmark}. It measures how fast the
 * indexed-dispatch parser turns a large synthetic script into triggers. A proper JMH suite, and
 * runtime/event-dispatch benchmarks, are tracked for the broader performance work.
 */
public final class ParseBenchmark {

    private ParseBenchmark() {
    }

    public static void main(String[] args) {
        DefaultSyntaxRegistry registry = new DefaultSyntaxRegistry();
        registry.registerEffect("set %object% to %object%", arguments -> {
            VariableExpression target = (VariableExpression) arguments.get(0);
            var value = arguments.get(1);
            return ctx -> target.set(ctx, value.getSingle(ctx));
        });
        registry.registerCondition("%object% is %object%", arguments -> ctx -> true);

        EventRegistry events = new EventRegistry();
        events.register(Object.class, "join");
        ScriptParser parser = new ScriptParser(registry, events, new FunctionRegistry());

        String script = generate(200, 20);

        for (int i = 0; i < 50; i++) {
            parser.parse(script);
        }

        int iterations = 200;
        long start = System.nanoTime();
        long triggers = 0;
        for (int i = 0; i < iterations; i++) {
            triggers += parser.parse(script).size();
        }
        long elapsedNanos = System.nanoTime() - start;

        double msPerParse = elapsedNanos / 1_000_000.0 / iterations;
        double triggersPerSecond = triggers / (elapsedNanos / 1_000_000_000.0);
        System.out.printf("Parsed a %d-trigger script %d times: %.3f ms/parse, %,.0f triggers/sec%n",
                200, iterations, msPerParse, triggersPerSecond);
    }

    private static String generate(int triggers, int statementsPerTrigger) {
        StringBuilder sb = new StringBuilder();
        for (int t = 0; t < triggers; t++) {
            sb.append("on join:\n");
            for (int s = 0; s < statementsPerTrigger; s++) {
                sb.append("    set {_v").append(s).append("} to ")
                        .append(s).append(" + ").append(s * 2).append(" * 3\n");
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
