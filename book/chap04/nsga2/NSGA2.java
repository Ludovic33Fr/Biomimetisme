package nsga2;

import java.util.ArrayList;
import java.util.List;

/**
 * Composants clés de NSGA-II (Non-dominated Sorting Genetic Algorithm II) :
 * tri non-dominé en fronts de Pareto et calcul de distance de crowding.
 */
public class NSGA2 {

    public static class Solution {
        public double[] objectifs;
        public int rang;
        public double distanceCrowding;

        public Solution(double[] objectifs) {
            this.objectifs = objectifs;
            this.rang = 0;
            this.distanceCrowding = 0.0;
        }

        public boolean domine(Solution autre) {
            boolean auMoinsUnMeilleur = false;

            for (int i = 0; i < objectifs.length; i++) {
                if (objectifs[i] > autre.objectifs[i]) {
                    return false;
                }
                if (objectifs[i] < autre.objectifs[i]) {
                    auMoinsUnMeilleur = true;
                }
            }

            return auMoinsUnMeilleur;
        }
    }

    public void triNonDomine(List<Solution> population) {
        List<List<Solution>> fronts = new ArrayList<>();

        List<Solution> front0 = new ArrayList<>();

        for (Solution p : population) {
            List<Solution> Sp = new ArrayList<>();
            int np = 0;

            for (Solution q : population) {
                if (q.domine(p)) {
                    np++;
                } else if (p.domine(q)) {
                    Sp.add(q);
                }
            }

            if (np == 0) {
                p.rang = 0;
                front0.add(p);
            }
        }

        fronts.add(front0);

        int i = 0;
        while (!fronts.get(i).isEmpty()) {
            List<Solution> frontSuivant = new ArrayList<>();

            for (Solution p : fronts.get(i)) {
                for (Solution q : population) {
                    if (p.domine(q)) {
                        q.rang = i + 1;
                        frontSuivant.add(q);
                    }
                }
            }

            i++;
            if (!frontSuivant.isEmpty()) {
                fronts.add(frontSuivant);
            }
        }

        for (List<Solution> front : fronts) {
            calculDistanceCrowding(front);
        }
    }

    private void calculDistanceCrowding(List<Solution> front) {
        int n = front.size();
        if (n <= 2) {
            for (Solution solution : front) {
                solution.distanceCrowding = Double.POSITIVE_INFINITY;
            }
            return;
        }

        int m = front.get(0).objectifs.length;

        for (Solution solution : front) {
            solution.distanceCrowding = 0.0;
        }

        for (int i = 0; i < m; i++) {
            final int objectif = i;

            front.sort((a, b) -> Double.compare(a.objectifs[objectif], b.objectifs[objectif]));

            double min = front.get(0).objectifs[i];
            double max = front.get(n-1).objectifs[i];

            front.get(0).distanceCrowding = Double.POSITIVE_INFINITY;
            front.get(n-1).distanceCrowding = Double.POSITIVE_INFINITY;

            for (int j = 1; j < n-1; j++) {
                if (max > min) {
                    front.get(j).distanceCrowding +=
                        (front.get(j+1).objectifs[i] - front.get(j-1).objectifs[i]) / (max - min);
                }
            }
        }
    }
}
