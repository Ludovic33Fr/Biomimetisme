import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * GA-Firewall-Segmentation
 *
 * Objectif: trouver un compromis entre blocage (sécurité) et continuité (trafic légitime)
 * en optimisant (1) l'assignation de chaque noeud/service à un segment réseau
 * et (2) les paires de segments autorisées (politique default-deny, on autorise explicitement).
 *
 * Chromosome = [ nodeToSegment[], allowMatrix ]
 *  - nodeToSegment[i] ∈ {0..K-1}
 *  - allowMatrix[a][b] ∈ {false, true} signifie ALLOW du segment a vers b (default DENY)
 *
 * Fitness à MAXIMISER (pondérations configurables):
 *   fitness = + wTP * TP_blocked
 *             - wFN * FN_allowed_malicious
 *             - wFP * FP_blocked_legit
 *             - lambdaComplexity * (#paires ALLOW)  // règle courte = mieux pour l'exploitation
 *             - lambdaFragmentation * (#segments utilisés - cibleSegmentsUtilises)
 *   Toutes les sommes sont pondérées par la "volume" du flux (ex. nombre de req, débit, poids métier).
 *
 * Données d'entrée: CSV facultatif (src,dst,volume,malicious)
 *   - malicious ∈ {0,1}
 *   - volume: double >= 0 (ex: nombre de requêtes, ko, poids)
 *   Si aucun CSV fourni → dataset synthétique généré.
 *
 * Compilation:  javac GA-Firewall-Segmentation.java
 * Exécution:    java GA-Firewall-Segmentation --csv=flows.csv --segments=4 --gens=60 --pop=80
 *
 * Options (toutes facultatives):
 *   --csv=path.csv
 *   --segments=K            (default 4)
 *   --gens=G                (default 50)
 *   --pop=P                 (default 70)
 *   --seed=S                (default 42)
 *   --w_tp=1.0 --w_fn=2.0 --w_fp=3.0
 *   --lambda_complexity=0.05
 *   --lambda_fragmentation=0.05
 *   --target_used_segments=K   (cible de segments utilisés)
 */
public class GAFirewallSegmentation {
    public static void main(String[] args) { RealMain.main(args); }
}

class RealMain {
    // ==== Data structures ====
    static class Flow {
        final String src, dst;
        final double volume;
        final boolean malicious;
        Flow(String s, String d, double v, boolean m) { src=s; dst=d; volume=v; malicious=m; }
    }

    static class Problem {
        final List<String> nodes;                 // unique nodes
        final Map<String,Integer> idxOf;          // node -> index
        final List<Flow> flows;                   // flows list
        final int N;                              // node count
        Problem(List<Flow> flows) {
            this.flows = flows;
            Set<String> set = new LinkedHashSet<>();
            for (Flow f: flows) { set.add(f.src); set.add(f.dst); }
            this.nodes = new ArrayList<>(set);
            this.idxOf = new HashMap<>();
            for (int i=0;i<nodes.size();i++) idxOf.put(nodes.get(i), i);
            this.N = nodes.size();
        }
    }

    static class Individual {
        int[] seg;              // size N : node -> segment [0..K-1]
        boolean[][] allow;      // KxK allow matrix (default DENY). allow[a][a] is always true (self talks)
        double fitness;
        Metrics metrics;
    }

    static class Metrics {
        double tpBlocked;   // malicious blocked (good)
        double fnAllowed;   // malicious allowed (bad)
        double fpBlocked;   // legit blocked (bad)
        double tnAllowed;   // legit allowed (neutral, for info)
        int allowedPairs;   // number of allow edges (excluding self)
        int usedSegments;   // number of segments actually used by at least one node
    }

    // ==== GA params ====
    static class Params {
        int K = 4;                   // segments
        int POP = 70;                // population size
        int GENS = 50;               // generations
        int TOURN = 3;               // tournament size
        int ELITE = 2;               // elitism
        double MUT_NODE = 0.08;      // prob to mutate each node's segment
        double MUT_EDGE = 0.02;      // prob to flip each allow edge (a->b)
        long SEED = 42L;
        double W_TP = 1.0;
        double W_FN = 2.0;
        double W_FP = 3.0;
        double LAMBDA_COMPLEXITY = 0.05;
        double LAMBDA_FRAGMENT = 0.05;
        int TARGET_USED_SEGMENTS = -1; // default set to K later
    }

    static final Random RNG = new Random(42);

    public static void main(String[] args) {
        try {
            Params p = parseArgs(args);
            RNG.setSeed(p.SEED);

            List<Flow> flows;
            String csvPath = getArg(args, "--csv");
            if (csvPath != null) {
                flows = loadCsv(csvPath);
                System.out.println("Loaded flows from CSV: " + csvPath + " ("+flows.size()+" rows)");
            } else {
                flows = syntheticFlows();
                System.out.println("No CSV provided → using synthetic dataset ("+flows.size()+" rows)");
            }

            Problem problem = new Problem(flows);
            if (p.TARGET_USED_SEGMENTS < 0) p.TARGET_USED_SEGMENTS = p.K; // by default aim to use K

            // Run GA
            Individual best = runGA(problem, p);

            // Print summary
            printSummary(problem, p, best);

            // Export suggested rules (high-level)
            exportRules(problem, p, best, Paths.get("segmentation_rules.txt"));
            System.out.println("\nSuggested rules exported → segmentation_rules.txt");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("\nUsage: java RealMain --csv=flows.csv --segments=4 --gens=60 --pop=80 --seed=42 \n    [--w_tp=1.0 --w_fn=2.0 --w_fp=3.0 --lambda_complexity=0.05 --lambda_fragmentation=0.05 --target_used_segments=4]");
        }
    }

    // ==== GA Core ====
    static Individual runGA(Problem prob, Params p) {
        List<Individual> pop = new ArrayList<>();
        for (int i=0;i<p.POP;i++) pop.add(randomIndividual(prob, p));
        Individual best = null;

        for (int g=0; g<p.GENS; g++) {
            // Evaluate
            for (Individual ind : pop) evaluate(prob, p, ind);
            pop.sort(Comparator.comparingDouble((Individual x) -> -x.fitness));
            if (best==null || pop.get(0).fitness > best.fitness) best = deepCopy(pop.get(0));

            // Log
            Individual leader = pop.get(0);
            System.out.printf(Locale.ROOT,
                    "Gen %02d | fit=%.4f | TP=%.2f FN=%.2f FP=%.2f | allowPairs=%d usedSeg=%d%n",
                    g, leader.fitness, leader.metrics.tpBlocked, leader.metrics.fnAllowed,
                    leader.metrics.fpBlocked, leader.metrics.allowedPairs, leader.metrics.usedSegments);

            // Next gen (elitism + offspring)
            List<Individual> next = new ArrayList<>();
            for (int e=0;e<p.ELITE;e++) next.add(deepCopy(pop.get(e)));
            while (next.size() < p.POP) {
                Individual a = tournament(pop, p.TOURN);
                Individual b = tournament(pop, p.TOURN);
                Individual child = crossover(prob, p, a, b);
                mutate(prob, p, child);
                next.add(child);
            }
            pop = next;
        }
        // Final evaluation of best
        evaluate(prob, p, best);
        return best;
    }

    static Individual randomIndividual(Problem prob, Params p) {
        Individual ind = new Individual();
        ind.seg = new int[prob.N];
        for (int i=0;i<prob.N;i++) ind.seg[i] = RNG.nextInt(p.K);
        ind.allow = new boolean[p.K][p.K];
        for (int a=0;a<p.K;a++){
            for (int b=0;b<p.K;b++){
                if (a==b) ind.allow[a][b] = true; // self always allowed (not counted in complexity)
                else ind.allow[a][b] = RNG.nextDouble() < 0.3; // sparse allow
            }
        }
        return ind;
    }

    static Individual deepCopy(Individual x) {
        Individual y = new Individual();
        y.seg = Arrays.copyOf(x.seg, x.seg.length);
        y.allow = new boolean[x.allow.length][x.allow[0].length];
        for (int i=0;i<x.allow.length;i++) y.allow[i] = Arrays.copyOf(x.allow[i], x.allow[i].length);
        y.fitness = x.fitness;
        y.metrics = x.metrics; // metrics are immutable for logging, ok to share
        return y;
    }

    static Individual tournament(List<Individual> pop, int k) {
        Individual best = null;
        for (int i=0;i<k;i++) {
            Individual cand = pop.get(RNG.nextInt(pop.size()));
            if (best==null || cand.fitness > best.fitness) best = cand;
        }
        return best;
    }

    static Individual crossover(Problem prob, Params p, Individual a, Individual b) {
        Individual c = new Individual();
        c.seg = new int[prob.N];
        for (int i=0;i<prob.N;i++) c.seg[i] = (RNG.nextBoolean()? a.seg[i] : b.seg[i]);
        c.allow = new boolean[p.K][p.K];
        for (int i=0;i<p.K;i++){
            for (int j=0;j<p.K;j++) {
                if (i==j) c.allow[i][j] = true;
                else c.allow[i][j] = (RNG.nextBoolean()? a.allow[i][j] : b.allow[i][j]);
            }
        }
        return c;
    }

    static void mutate(Problem prob, Params p, Individual x) {
        // mutate node segments
        for (int i=0;i<prob.N;i++) {
            if (RNG.nextDouble() < p.MUT_NODE) {
                int old = x.seg[i];
                int nu = RNG.nextInt(p.K);
                x.seg[i] = nu;
            }
        }
        // mutate allow edges (excluding self)
        for (int i=0;i<p.K;i++){
            for (int j=0;j<p.K;j++){
                if (i==j) continue;
                if (RNG.nextDouble() < p.MUT_EDGE) {
                    x.allow[i][j] = !x.allow[i][j];
                }
            }
        }
    }

    static void evaluate(Problem prob, Params p, Individual ind) {
        double tp=0, fn=0, fp=0, tn=0;
        for (Flow f: prob.flows) {
            int si = prob.idxOf.get(f.src);
            int di = prob.idxOf.get(f.dst);
            int a = ind.seg[si];
            int b = ind.seg[di];
            boolean allowed = ind.allow[a][b];
            if (f.malicious) {
                if (allowed) fn += f.volume; else tp += f.volume;
            } else {
                if (allowed) tn += f.volume; else fp += f.volume;
            }
        }
        // complexity (exclude self-edges)
        int allowPairs = 0; int usedSeg = usedSegments(ind.seg, p.K);
        for (int i=0;i<p.K;i++) for (int j=0;j<p.K;j++) if (i!=j && ind.allow[i][j]) allowPairs++;
        double complexityPenalty = p.LAMBDA_COMPLEXITY * allowPairs;
        int target = p.TARGET_USED_SEGMENTS;
        double fragmentPenalty = p.LAMBDA_FRAGMENT * Math.max(0, usedSeg - target);

        double fit = (p.W_TP * tp) - (p.W_FN * fn) - (p.W_FP * fp) - complexityPenalty - fragmentPenalty;
        ind.fitness = fit;
        Metrics m = new Metrics();
        m.tpBlocked = tp; m.fnAllowed = fn; m.fpBlocked = fp; m.tnAllowed = tn;
        m.allowedPairs = allowPairs; m.usedSegments = usedSeg;
        ind.metrics = m;
    }

    static int usedSegments(int[] seg, int K) {
        boolean[] used = new boolean[K];
        for (int s: seg) used[s] = true;
        int c=0; for (boolean u: used) if (u) c++; return c;
    }

    // ==== IO / Dataset ====
    static List<Flow> loadCsv(String path) throws IOException {
        List<Flow> rows = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(Paths.get(path))) {
            String line; boolean headerProcessed = false;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                // Try to auto-skip header if it matches names
                if (!headerProcessed) {
                    String low = Arrays.stream(parts).map(String::toLowerCase).collect(Collectors.joining(","));
                    if (low.contains("src") && low.contains("dst")) { headerProcessed = true; continue; }
                    headerProcessed = true; // first row is data
                }
                if (parts.length < 4) throw new IOException("Expected CSV columns: src,dst,volume,malicious");
                String src = parts[0].trim();
                String dst = parts[1].trim();
                double vol = Double.parseDouble(parts[2].trim());
                int mal = Integer.parseInt(parts[3].trim());
                rows.add(new Flow(src,dst,vol, mal!=0));
            }
        }
        return rows;
    }

    static List<Flow> syntheticFlows() {
        // Small realistic demo: web/api/db/cache/admin/jump workloads with some malicious attempts
        String[] nodes = {"web1","web2","api1","db1","cache1","admin1","jump1","batch1"};
        Map<String,Double> baseVol = new HashMap<>();
        // legit typical communications (src->dst: volume)
        baseVol.put("web1->api1", 800.0);
        baseVol.put("web2->api1", 750.0);
        baseVol.put("api1->db1", 600.0);
        baseVol.put("api1->cache1", 500.0);
        baseVol.put("batch1->api1", 200.0);
        baseVol.put("admin1->api1", 100.0);
        baseVol.put("admin1->db1", 50.0);
        baseVol.put("api1->web1", 80.0); // callbacks/websockets

        List<Flow> flows = new ArrayList<>();
        for (Map.Entry<String,Double> e : baseVol.entrySet()) {
            String[] sd = e.getKey().split("->");
            flows.add(new Flow(sd[0], sd[1], e.getValue(), false));
        }
        // noise legit
        flows.add(new Flow("web1","cache1", 50, false));
        flows.add(new Flow("web2","cache1", 50, false));
        flows.add(new Flow("batch1","db1", 60, false));

        // malicious attempts (scanner lateral moves, data exfil, admin brute, etc.)
        flows.add(new Flow("web1","db1", 120, true));
        flows.add(new Flow("web2","db1", 110, true));
        flows.add(new Flow("web1","admin1", 40, true));
        flows.add(new Flow("web2","admin1", 35, true));
        flows.add(new Flow("jump1","db1", 90, true));
        flows.add(new Flow("jump1","api1", 60, true));
        flows.add(new Flow("jump1","web1", 30, true));
        flows.add(new Flow("batch1","db1", 40, true)); // masquerade
        return flows;
    }

    // ==== Utils ====
    static Params parseArgs(String[] args) {
        Params p = new Params();
        String v;
        if ((v=getArg(args,"--segments"))!=null) p.K = Integer.parseInt(v);
        if ((v=getArg(args,"--gens"))!=null) p.GENS = Integer.parseInt(v);
        if ((v=getArg(args,"--pop"))!=null) p.POP = Integer.parseInt(v);
        if ((v=getArg(args,"--seed"))!=null) p.SEED = Long.parseLong(v);
        if ((v=getArg(args,"--w_tp"))!=null) p.W_TP = Double.parseDouble(v);
        if ((v=getArg(args,"--w_fn"))!=null) p.W_FN = Double.parseDouble(v);
        if ((v=getArg(args,"--w_fp"))!=null) p.W_FP = Double.parseDouble(v);
        if ((v=getArg(args,"--lambda_complexity"))!=null) p.LAMBDA_COMPLEXITY = Double.parseDouble(v);
        if ((v=getArg(args,"--lambda_fragmentation"))!=null) p.LAMBDA_FRAGMENT = Double.parseDouble(v);
        if ((v=getArg(args,"--target_used_segments"))!=null) p.TARGET_USED_SEGMENTS = Integer.parseInt(v);
        if (p.TARGET_USED_SEGMENTS <= 0) p.TARGET_USED_SEGMENTS = p.K;
        return p;
    }

    static String getArg(String[] args, String key) {
        for (String a: args) if (a.startsWith(key+"=")) return a.substring(key.length()+1);
        return null;
    }

    static void printSummary(Problem prob, Params p, Individual best) {
        System.out.println("\n==== BEST SOLUTION ====");
        System.out.printf(Locale.ROOT, "Fitness=%.3f | TP=%.2f FN=%.2f FP=%.2f | allowPairs=%d | usedSeg=%d\n",
                best.fitness, best.metrics.tpBlocked, best.metrics.fnAllowed, best.metrics.fpBlocked,
                best.metrics.allowedPairs, best.metrics.usedSegments);
        // Segment mapping
        System.out.println("\nNode → Segment:");
        for (int i=0;i<prob.N;i++) {
            System.out.printf("  %-10s → S%d%n", prob.nodes.get(i), best.seg[i]);
        }
        // Allowed matrix
        System.out.println("\nAllowed segment pairs (Sx→Sy), excluding self:");
        for (int a=0;a<p.K;a++){
            for (int b=0;b<p.K;b++){
                if (a!=b && best.allow[a][b]) {
                    System.out.printf("  S%d → S%d%n", a,b);
                }
            }
        }
        // Quick confusion-like metrics by scanning flows
        long cntMal=0,cntLeg=0; for (Flow f: prob.flows) if (f.malicious) cntMal++; else cntLeg++;
        System.out.printf("\nFlows count: legitimate=%d malicious=%d total=%d\n", cntLeg, cntMal, prob.flows.size());
    }

    static void exportRules(Problem prob, Params p, Individual best, Path out) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(out))) {
            pw.println("# Suggested high-level segmentation policy (default DENY)");
            pw.println("# Segments assignments:");
            for (int i=0;i<prob.N;i++) pw.printf("segment S%d contains %s%n", best.seg[i], prob.nodes.get(i));

            pw.println("\n# Allowed flows between segments (Sx -> Sy):");
            for (int a=0;a<p.K;a++){
                for (int b=0;b<p.K;b++){
                    if (a!=b && best.allow[a][b]) {
                        pw.printf("allow S%d -> S%d%n", a,b);
                    }
                }
            }

            // Optional: expand to node pairs for operators
            pw.println("\n# Expanded allowed node pairs:");
            for (Flow f: prob.flows) {
                int a = best.seg[prob.idxOf.get(f.src)];
                int b = best.seg[prob.idxOf.get(f.dst)];
                if (best.allow[a][b]) pw.printf("ALLOW %s -> %s  # S%d->S%d%s%n", f.src, f.dst, a,b, f.malicious?" (malicious sample present)":"");
            }
        }
    }
}
