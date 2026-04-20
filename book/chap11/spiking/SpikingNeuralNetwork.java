package spiking;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Réseau de neurones à impulsions (spiking neural network).
 * Modèle simplifié de neurones LIF (Leaky Integrate-and-Fire)
 * avec apprentissage STDP (Spike-Timing-Dependent Plasticity).
 *
 * Base pour l'informatique neuromorphique.
 */
public class SpikingNeuralNetwork {

    private final List<SpikingNeuron> neurons;
    private final List<Synapse> synapses;
    private double currentTime = 0.0;
    private final Random random = new Random();

    public SpikingNeuralNetwork() {
        this.neurons = new ArrayList<>();
        this.synapses = new ArrayList<>();
    }

    public SpikingNeuron addNeuron(String id) {
        SpikingNeuron n = new SpikingNeuron(id);
        neurons.add(n);
        return n;
    }

    public Synapse connect(SpikingNeuron pre, SpikingNeuron post, double weight) {
        Synapse s = new Synapse(pre, post, weight);
        synapses.add(s);
        pre.outgoing.add(s);
        post.incoming.add(s);
        return s;
    }

    /**
     * Simulation d'un pas de temps : chaque neurone intègre ses entrées,
     * déclenche un spike si son potentiel dépasse le seuil, puis propage.
     */
    public void simulateStep(double dt) {
        currentTime += dt;

        for (SpikingNeuron n : neurons) {
            // Intégration avec fuite (leaky integration)
            n.potential *= Math.exp(-dt / n.tauMembrane);

            // Entrées synaptiques
            for (Synapse s : n.incoming) {
                if (s.recentSpike) {
                    n.potential += s.weight;
                    s.recentSpike = false;
                }
            }

            // Seuil : déclenche un spike si dépassé
            if (n.potential >= n.threshold) {
                n.fire(currentTime);
                for (Synapse s : n.outgoing) s.recentSpike = true;

                // Apprentissage STDP : renforcer les synapses récemment actives en amont
                for (Synapse s : n.incoming) {
                    if (s.lastPreSpike > 0 && currentTime - s.lastPreSpike < 20) {
                        double deltaT = currentTime - s.lastPreSpike;
                        s.weight += 0.01 * Math.exp(-deltaT / 20);
                    }
                }
            }
        }

        // Mettre à jour les temps de spike pré-synaptiques
        for (Synapse s : synapses) {
            if (s.pre.justFired) s.lastPreSpike = currentTime;
        }

        for (SpikingNeuron n : neurons) n.justFired = false;
    }

    public static class SpikingNeuron {
        public final String id;
        public double potential = 0.0;
        public double threshold = 1.0;
        public double tauMembrane = 20.0;
        public boolean justFired = false;
        public double lastFireTime = -1;

        public final List<Synapse> incoming = new ArrayList<>();
        public final List<Synapse> outgoing = new ArrayList<>();

        public SpikingNeuron(String id) { this.id = id; }

        public void fire(double time) {
            potential = 0;
            justFired = true;
            lastFireTime = time;
        }

        public void inject(double current) {
            potential += current;
        }
    }

    public static class Synapse {
        public final SpikingNeuron pre;
        public final SpikingNeuron post;
        public double weight;
        public boolean recentSpike = false;
        public double lastPreSpike = -1;

        public Synapse(SpikingNeuron pre, SpikingNeuron post, double weight) {
            this.pre = pre;
            this.post = post;
            this.weight = weight;
        }
    }

    public static void main(String[] args) {
        SpikingNeuralNetwork net = new SpikingNeuralNetwork();

        SpikingNeuron input = net.addNeuron("input");
        SpikingNeuron hidden = net.addNeuron("hidden");
        SpikingNeuron output = net.addNeuron("output");

        net.connect(input, hidden, 0.8);
        net.connect(hidden, output, 0.7);

        for (int step = 0; step < 100; step++) {
            if (step % 10 == 0) input.inject(1.2);
            net.simulateStep(1.0);

            if (output.justFired) {
                System.out.printf("t=%.1f: OUTPUT spike!%n", net.currentTime);
            }
        }
    }
}
