package integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Moteur d'intégration multi-paradigmes bio-inspirés.
 * Permet de coordonner des modules évolutionnaires, neuronaux et multi-agents
 * via un bus d'échange de données commun.
 */
public class IntegrationEngine {

    private final List<BioInspiredModule> modules;
    private final ExecutorService executorService;
    private final DataExchangeBus dataBus;

    public IntegrationEngine() {
        this.modules = new ArrayList<>();
        this.executorService = Executors.newCachedThreadPool();
        this.dataBus = new SharedMemoryDataBus();
    }

    public void registerModule(BioInspiredModule module) {
        modules.add(module);
        module.setDataBus(dataBus);
        System.out.println("Module " + module.getName() + " enregistré.");
    }

    public void startModules() {
        System.out.println("Démarrage des modules...");
        for (BioInspiredModule module : modules) {
            executorService.submit(() -> {
                try {
                    module.initialize(Map.of("global_param", "value"));
                    module.executeCycle();
                } catch (Exception e) {
                    System.err.println("Erreur " + module.getName() + ": " + e.getMessage());
                }
            });
        }
    }

    public void shutdown() {
        for (BioInspiredModule module : modules) module.terminate();
        executorService.shutdown();
    }

    public interface BioInspiredModule {
        String getName();
        void initialize(Map<String, Object> params);
        void executeCycle();
        void terminate();
        void setDataBus(DataExchangeBus bus);
    }

    public interface DataExchangeBus {
        void publish(String topic, Object data);
        Object subscribe(String topic);
    }

    public static class SharedMemoryDataBus implements DataExchangeBus {
        private final Map<String, Object> sharedData = new ConcurrentHashMap<>();

        @Override
        public void publish(String topic, Object data) {
            sharedData.put(topic, data);
            System.out.println("Bus: " + topic + " → " + data);
        }

        @Override
        public Object subscribe(String topic) {
            return sharedData.get(topic);
        }
    }

    public static class EvolutionaryOptimizerModule implements BioInspiredModule {
        private final String name;
        private DataExchangeBus dataBus;
        public EvolutionaryOptimizerModule(String name) { this.name = name; }
        @Override public String getName() { return name; }
        @Override public void initialize(Map<String, Object> params) { System.out.println(name + ": init"); }
        @Override public void executeCycle() {
            dataBus.publish("optimization.result", "Meilleure solution trouvée");
        }
        @Override public void terminate() {}
        @Override public void setDataBus(DataExchangeBus bus) { this.dataBus = bus; }
    }

    public static class NeuralNetworkModule implements BioInspiredModule {
        private final String name;
        private DataExchangeBus dataBus;
        public NeuralNetworkModule(String name) { this.name = name; }
        @Override public String getName() { return name; }
        @Override public void initialize(Map<String, Object> params) {}
        @Override public void executeCycle() {
            dataBus.publish("perception.output", "Objet détecté");
        }
        @Override public void terminate() {}
        @Override public void setDataBus(DataExchangeBus bus) { this.dataBus = bus; }
    }

    public static class MultiAgentSystemModule implements BioInspiredModule {
        private final String name;
        private DataExchangeBus dataBus;
        public MultiAgentSystemModule(String name) { this.name = name; }
        @Override public String getName() { return name; }
        @Override public void initialize(Map<String, Object> params) {}
        @Override public void executeCycle() {
            Object optim = dataBus.subscribe("optimization.result");
            Object perc = dataBus.subscribe("perception.output");
            System.out.println("MAS décide : " + optim + " + " + perc);
        }
        @Override public void terminate() {}
        @Override public void setDataBus(DataExchangeBus bus) { this.dataBus = bus; }
    }

    public static void main(String[] args) throws InterruptedException {
        IntegrationEngine engine = new IntegrationEngine();
        engine.registerModule(new EvolutionaryOptimizerModule("OptimizerEVO-1"));
        engine.registerModule(new NeuralNetworkModule("PerceptionNN-Alpha"));
        engine.registerModule(new MultiAgentSystemModule("DecisionMAS-Beta"));

        engine.startModules();
        Thread.sleep(5000);
        engine.shutdown();
    }
}
