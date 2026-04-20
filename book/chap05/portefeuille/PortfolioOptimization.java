package portefeuille;

import java.util.Arrays;
import pso.ParticleSwarmOptimization;

public class PortfolioOptimization {

    private double[] expectedReturns;
    private double[][] covarianceMatrix;
    private double riskAversion;

    private int swarmSize = 50;
    private int maxIterations = 1000;
    private double w = 0.729;
    private double c1 = 1.49445;
    private double c2 = 1.49445;

    public PortfolioOptimization(double[] expectedReturns, double[][] covarianceMatrix, double riskAversion) {
        this.expectedReturns = expectedReturns;
        this.covarianceMatrix = covarianceMatrix;
        this.riskAversion = riskAversion;
    }

    public double[] optimizePortfolio() {
        int dimension = expectedReturns.length;

        double[] lowerBounds = new double[dimension];
        double[] upperBounds = new double[dimension];
        Arrays.fill(lowerBounds, 0.0);
        Arrays.fill(upperBounds, 1.0);

        ParticleSwarmOptimization.ObjectiveFunction objectiveFunction = weights -> {
            double sum = 0.0;
            for (double weight : weights) sum += weight;
            if (sum == 0) return Double.MAX_VALUE;

            double[] normalizedWeights = new double[dimension];
            for (int i = 0; i < dimension; i++) normalizedWeights[i] = weights[i] / sum;

            double expectedReturn = 0.0;
            for (int i = 0; i < dimension; i++) {
                expectedReturn += normalizedWeights[i] * expectedReturns[i];
            }

            double risk = 0.0;
            for (int i = 0; i < dimension; i++) {
                for (int j = 0; j < dimension; j++) {
                    risk += normalizedWeights[i] * normalizedWeights[j] * covarianceMatrix[i][j];
                }
            }

            return -(expectedReturn - riskAversion * risk);
        };

        ParticleSwarmOptimization pso = new ParticleSwarmOptimization(
            swarmSize, dimension, maxIterations, lowerBounds, upperBounds, w, c1, c2, objectiveFunction);

        double[] optimalWeights = pso.optimize();

        double sum = 0.0;
        for (double weight : optimalWeights) sum += weight;

        for (int i = 0; i < dimension; i++) optimalWeights[i] /= sum;

        return optimalWeights;
    }

    public static void main(String[] args) {
        double[] expectedReturns = {0.10, 0.15, 0.12, 0.08};

        double[][] covarianceMatrix = {
            {0.0100, 0.0018, 0.0011, 0.0014},
            {0.0018, 0.0400, 0.0010, 0.0009},
            {0.0011, 0.0010, 0.0250, 0.0020},
            {0.0014, 0.0009, 0.0020, 0.0160}
        };

        double riskAversion = 0.5;

        PortfolioOptimization optimizer = new PortfolioOptimization(
            expectedReturns, covarianceMatrix, riskAversion);

        double[] optimalWeights = optimizer.optimizePortfolio();

        System.out.println("Optimal portfolio weights:");
        for (int i = 0; i < optimalWeights.length; i++) {
            System.out.printf("Asset %d: %.2f%%\n", i + 1, optimalWeights[i] * 100);
        }

        double portfolioReturn = 0.0;
        double portfolioRisk = 0.0;

        for (int i = 0; i < optimalWeights.length; i++) {
            portfolioReturn += optimalWeights[i] * expectedReturns[i];
            for (int j = 0; j < optimalWeights.length; j++) {
                portfolioRisk += optimalWeights[i] * optimalWeights[j] * covarianceMatrix[i][j];
            }
        }

        System.out.printf("Expected portfolio return: %.2f%%\n", portfolioReturn * 100);
        System.out.printf("Portfolio risk (variance): %.4f\n", portfolioRisk);
        System.out.printf("Portfolio risk (std dev): %.2f%%\n", Math.sqrt(portfolioRisk) * 100);
    }
}
