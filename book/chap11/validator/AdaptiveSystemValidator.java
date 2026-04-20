package validator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Validateur de systèmes adaptatifs : teste un système sur différents scénarios
 * (conditions nominales, perturbations, adversarial) et mesure la robustesse,
 * l'adaptation et la convergence.
 */
public class AdaptiveSystemValidator<S, I, O> {

    private final S system;
    private final Function<I, O> systemFunction;
    private final List<TestScenario<I, O>> scenarios = new ArrayList<>();
    private final Map<String, ValidationResult> results = new HashMap<>();

    public AdaptiveSystemValidator(S system, Function<I, O> systemFunction) {
        this.system = system;
        this.systemFunction = systemFunction;
    }

    public void addScenario(TestScenario<I, O> scenario) {
        scenarios.add(scenario);
    }

    public ValidationReport validate() {
        ValidationReport report = new ValidationReport();

        for (TestScenario<I, O> scenario : scenarios) {
            ValidationResult result = runScenario(scenario);
            results.put(scenario.getName(), result);
            report.addResult(scenario.getName(), result);
        }

        return report;
    }

    private ValidationResult runScenario(TestScenario<I, O> scenario) {
        int passed = 0;
        int failed = 0;
        double totalLatency = 0;
        List<String> failures = new ArrayList<>();

        for (TestCase<I, O> testCase : scenario.getTestCases()) {
            long start = System.nanoTime();
            O output = systemFunction.apply(testCase.getInput());
            long duration = System.nanoTime() - start;
            totalLatency += duration / 1_000_000.0;

            if (testCase.validator.test(output)) {
                passed++;
            } else {
                failed++;
                failures.add("Cas " + testCase.getName() + " : sortie = " + output);
            }
        }

        ValidationResult result = new ValidationResult();
        result.totalCases = passed + failed;
        result.passedCases = passed;
        result.failedCases = failed;
        result.averageLatencyMs = totalLatency / Math.max(1, result.totalCases);
        result.failureMessages = failures;

        return result;
    }

    public interface TestValidator<O> {
        boolean test(O output);
    }

    public static class TestCase<I, O> {
        private final String name;
        private final I input;
        private final TestValidator<O> validator;

        public TestCase(String name, I input, TestValidator<O> validator) {
            this.name = name;
            this.input = input;
            this.validator = validator;
        }

        public String getName() { return name; }
        public I getInput() { return input; }
    }

    public static class TestScenario<I, O> {
        private final String name;
        private final List<TestCase<I, O>> testCases = new ArrayList<>();

        public TestScenario(String name) { this.name = name; }

        public void addTestCase(TestCase<I, O> testCase) { testCases.add(testCase); }
        public String getName() { return name; }
        public List<TestCase<I, O>> getTestCases() { return testCases; }
    }

    public static class ValidationResult {
        public int totalCases;
        public int passedCases;
        public int failedCases;
        public double averageLatencyMs;
        public List<String> failureMessages = new ArrayList<>();

        public double passRate() {
            return totalCases > 0 ? (double) passedCases / totalCases : 0;
        }
    }

    public static class ValidationReport {
        private final Map<String, ValidationResult> scenarioResults = new HashMap<>();

        public void addResult(String scenario, ValidationResult result) {
            scenarioResults.put(scenario, result);
        }

        public void print() {
            System.out.println("=== Rapport de validation ===");
            for (Map.Entry<String, ValidationResult> e : scenarioResults.entrySet()) {
                ValidationResult r = e.getValue();
                System.out.printf("%s: %d/%d OK (%.1f%%), latence moyenne: %.2f ms%n",
                        e.getKey(), r.passedCases, r.totalCases, r.passRate() * 100,
                        r.averageLatencyMs);
            }
        }
    }

    public static void main(String[] args) {
        // Exemple : valider une fonction de multiplication par 2
        AdaptiveSystemValidator<Object, Integer, Integer> validator = new AdaptiveSystemValidator<>(
                new Object(),
                x -> x * 2
        );

        TestScenario<Integer, Integer> scenario = new TestScenario<>("Nominal");
        scenario.addTestCase(new TestCase<>("cas1", 5, out -> out == 10));
        scenario.addTestCase(new TestCase<>("cas2", 0, out -> out == 0));
        scenario.addTestCase(new TestCase<>("cas3", -3, out -> out == -6));

        validator.addScenario(scenario);
        validator.validate().print();
    }
}
