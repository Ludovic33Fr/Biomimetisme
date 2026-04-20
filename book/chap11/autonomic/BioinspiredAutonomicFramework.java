package autonomic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Framework autonome bio-inspiré réalisant la boucle MAPE-K
 * (Monitor, Analyze, Plan, Execute, Knowledge).
 * Inspiré du modèle de l'informatique autonome d'IBM avec principes biomimétiques.
 */
public class BioinspiredAutonomicFramework {

    private final KnowledgeBase knowledge = new KnowledgeBase();
    private final List<Sensor> sensors = new ArrayList<>();
    private final List<Effector> effectors = new ArrayList<>();
    private final Analyzer analyzer;
    private final Planner planner;
    private final Executor executor;
    private final ScheduledExecutorService scheduler;

    public BioinspiredAutonomicFramework() {
        this.analyzer = new Analyzer(knowledge);
        this.planner = new Planner(knowledge);
        this.executor = new Executor(effectors, knowledge);
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    public void addSensor(Sensor s) { sensors.add(s); }
    public void addEffector(Effector e) { effectors.add(e); }

    public void start(long periodMs) {
        scheduler.scheduleAtFixedRate(this::mapeKLoop, 0, periodMs, TimeUnit.MILLISECONDS);
    }

    private void mapeKLoop() {
        // M - Monitor : collecte des signaux
        Map<String, Object> observations = new HashMap<>();
        for (Sensor s : sensors) observations.put(s.getName(), s.read());
        knowledge.recordObservation(observations);

        // A - Analyze : détecter les symptômes
        List<Symptom> symptoms = analyzer.analyze(observations);

        if (!symptoms.isEmpty()) {
            // P - Plan : élaborer un plan d'action
            List<Action> plan = planner.plan(symptoms);

            // E - Execute : appliquer les actions
            executor.execute(plan);

            // K : la knowledge base apprend
            knowledge.recordCycle(observations, symptoms, plan);
        }
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public interface Sensor {
        String getName();
        Object read();
    }

    public interface Effector {
        String getName();
        void apply(Action action);
    }

    public static class Symptom {
        public final String type;
        public final double severity;
        public final Map<String, Object> context;

        public Symptom(String type, double severity, Map<String, Object> context) {
            this.type = type;
            this.severity = severity;
            this.context = context;
        }
    }

    public static class Action {
        public final String effectorName;
        public final String command;
        public final Map<String, Object> parameters;

        public Action(String effectorName, String command, Map<String, Object> parameters) {
            this.effectorName = effectorName;
            this.command = command;
            this.parameters = parameters;
        }
    }

    public static class KnowledgeBase {
        private final List<Map<String, Object>> observationHistory = new ArrayList<>();
        private final Map<String, Integer> symptomFrequency = new HashMap<>();

        public void recordObservation(Map<String, Object> obs) {
            observationHistory.add(obs);
            if (observationHistory.size() > 1000) observationHistory.remove(0);
        }

        public void recordCycle(Map<String, Object> obs, List<Symptom> symptoms, List<Action> plan) {
            for (Symptom s : symptoms) {
                symptomFrequency.merge(s.type, 1, Integer::sum);
            }
        }

        public List<Map<String, Object>> getRecentObservations(int count) {
            int start = Math.max(0, observationHistory.size() - count);
            return observationHistory.subList(start, observationHistory.size());
        }

        public int getSymptomCount(String type) {
            return symptomFrequency.getOrDefault(type, 0);
        }
    }

    public static class Analyzer {
        private final KnowledgeBase knowledge;

        public Analyzer(KnowledgeBase knowledge) { this.knowledge = knowledge; }

        public List<Symptom> analyze(Map<String, Object> observations) {
            List<Symptom> symptoms = new ArrayList<>();

            // Détection simple : CPU élevé = symptôme
            if (observations.containsKey("cpu")) {
                double cpu = ((Number) observations.get("cpu")).doubleValue();
                if (cpu > 80) {
                    symptoms.add(new Symptom("HIGH_CPU", cpu / 100.0, observations));
                }
            }

            if (observations.containsKey("memory")) {
                double mem = ((Number) observations.get("memory")).doubleValue();
                if (mem > 90) {
                    symptoms.add(new Symptom("HIGH_MEMORY", mem / 100.0, observations));
                }
            }

            return symptoms;
        }
    }

    public static class Planner {
        private final KnowledgeBase knowledge;

        public Planner(KnowledgeBase knowledge) { this.knowledge = knowledge; }

        public List<Action> plan(List<Symptom> symptoms) {
            List<Action> plan = new ArrayList<>();

            for (Symptom s : symptoms) {
                switch (s.type) {
                    case "HIGH_CPU":
                        plan.add(new Action("scaler", "scaleOut", Map.of("instances", 1)));
                        break;
                    case "HIGH_MEMORY":
                        plan.add(new Action("cache", "flush", Map.of()));
                        break;
                }
            }

            return plan;
        }
    }

    public static class Executor {
        private final List<Effector> effectors;
        private final KnowledgeBase knowledge;

        public Executor(List<Effector> effectors, KnowledgeBase knowledge) {
            this.effectors = effectors;
            this.knowledge = knowledge;
        }

        public void execute(List<Action> plan) {
            for (Action action : plan) {
                for (Effector e : effectors) {
                    if (e.getName().equals(action.effectorName)) {
                        e.apply(action);
                        break;
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        BioinspiredAutonomicFramework framework = new BioinspiredAutonomicFramework();

        framework.addSensor(new Sensor() {
            @Override public String getName() { return "cpu"; }
            @Override public Object read() { return Math.random() * 100; }
        });

        framework.addEffector(new Effector() {
            @Override public String getName() { return "scaler"; }
            @Override
            public void apply(Action action) {
                System.out.println("Scaler : " + action.command + " avec " + action.parameters);
            }
        });

        framework.start(500);
        Thread.sleep(5000);
        framework.shutdown();
    }
}
