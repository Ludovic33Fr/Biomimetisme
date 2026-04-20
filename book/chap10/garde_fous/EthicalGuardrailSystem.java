package garde_fous;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Système de garde-fous éthiques pour systèmes auto-adaptatifs.
 * Validation préalable, supervision d'exécution, détection d'anomalies,
 * demandes d'approbation humaine pour les décisions critiques.
 *
 * Code CONCEPTUEL : à brancher sur un système d'IA auto-adaptatif concret.
 */

public class EthicalGuardrailSystem {

    private final List<EthicalConstraint> constraints = new ArrayList<>();
    private final List<AnomalyDetector> anomalyDetectors = new ArrayList<>();
    private final Map<String, ApprovalRequest> pendingApprovals = new HashMap<>();
    private final AuditLog auditLog = new AuditLog();

    public void registerConstraint(EthicalConstraint constraint) {
        constraints.add(constraint);
        auditLog.log("Contrainte enregistrée: " + constraint.getName());
    }

    public void registerAnomalyDetector(AnomalyDetector detector) {
        anomalyDetectors.add(detector);
    }

    /**
     * Valide une action proposée avant son exécution.
     * Retourne un résultat avec violations éventuelles.
     */
    public ValidationResult validateAction(Action action, Context context) {
        List<ConstraintValidationResult> violations = new ArrayList<>();

        for (EthicalConstraint constraint : constraints) {
            ConstraintValidationResult result = constraint.validate(action, context);
            if (!result.isPassed()) {
                violations.add(result);
            }
        }

        boolean valid = violations.isEmpty();
        auditLog.log("Validation de " + action.getDescription() + ": " + (valid ? "OK" : "VIOLATIONS"));

        return new ValidationResult(valid, violations);
    }

    /**
     * Demande une approbation humaine pour une action sensible.
     */
    public ApprovalRequest requestApproval(Action action, Context context, String reason) {
        String requestId = "req-" + System.currentTimeMillis();
        ApprovalRequest request = new ApprovalRequest(requestId, action, context, reason, Instant.now());
        pendingApprovals.put(requestId, request);
        auditLog.log("Approbation demandée: " + requestId + " (" + reason + ")");
        return request;
    }

    public ApprovalResult processApprovalResponse(String requestId, boolean approved, String humanComment) {
        ApprovalRequest request = pendingApprovals.remove(requestId);
        if (request == null) {
            return new ApprovalResult(requestId, false, "Requête introuvable");
        }

        ApprovalResult result = new ApprovalResult(requestId, approved, humanComment);
        auditLog.log("Approbation " + requestId + ": " + (approved ? "ACCORDÉE" : "REFUSÉE") + " - " + humanComment);
        return result;
    }

    /**
     * Exécute une action sous supervision, avec détection d'anomalies en cours de route.
     */
    public ExecutionResult executeWithSupervision(Action action, Context context) {
        // 1. Valider avant exécution
        ValidationResult validation = validateAction(action, context);
        if (!validation.isValid()) {
            return new ExecutionResult(false, "Action invalide", validation.getViolations());
        }

        // 2. Détecter les anomalies en préparation
        List<Anomaly> preAnomalies = detectAnomalies(action, context, ExecutionPhase.PRE);
        if (!preAnomalies.isEmpty()) {
            return new ExecutionResult(false, "Anomalies pré-exécution détectées", preAnomalies);
        }

        // 3. Exécuter
        auditLog.log("Exécution de " + action.getDescription());
        action.execute(context);

        // 4. Détecter les anomalies post-exécution
        List<Anomaly> postAnomalies = detectAnomalies(action, context, ExecutionPhase.POST);

        return new ExecutionResult(true, "Exécution réussie", postAnomalies);
    }

    private List<Anomaly> detectAnomalies(Action action, Context context, ExecutionPhase phase) {
        List<Anomaly> anomalies = new ArrayList<>();
        for (AnomalyDetector detector : anomalyDetectors) {
            anomalies.addAll(detector.detect(action, context, phase));
        }
        return anomalies;
    }

    // === Classes internes ===

    public static class ConstraintValidationResult {
        private final String constraintName;
        private final boolean passed;
        private final String message;

        public ConstraintValidationResult(String constraintName, boolean passed, String message) {
            this.constraintName = constraintName;
            this.passed = passed;
            this.message = message;
        }

        public String getConstraintName() { return constraintName; }
        public boolean isPassed() { return passed; }
        public String getMessage() { return message; }
    }

    public static class ValidationResult {
        private final boolean valid;
        private final List<ConstraintValidationResult> violations;

        public ValidationResult(boolean valid, List<ConstraintValidationResult> violations) {
            this.valid = valid;
            this.violations = violations;
        }

        public boolean isValid() { return valid; }
        public List<ConstraintValidationResult> getViolations() { return violations; }
    }

    public static class ApprovalRequest {
        final String id;
        final Action action;
        final Context context;
        final String reason;
        final Instant timestamp;

        public ApprovalRequest(String id, Action action, Context context, String reason, Instant timestamp) {
            this.id = id;
            this.action = action;
            this.context = context;
            this.reason = reason;
            this.timestamp = timestamp;
        }

        public String getId() { return id; }
    }

    public static class ApprovalResult {
        private final String requestId;
        private final boolean approved;
        private final String comment;

        public ApprovalResult(String requestId, boolean approved, String comment) {
            this.requestId = requestId;
            this.approved = approved;
            this.comment = comment;
        }

        public boolean isApproved() { return approved; }
    }

    public static class ExecutionResult {
        private final boolean success;
        private final String message;
        private final Object details;

        public ExecutionResult(boolean success, String message, Object details) {
            this.success = success;
            this.message = message;
            this.details = details;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Object getDetails() { return details; }
    }

    public static class Anomaly {
        private final String type;
        private final String description;
        private final double severity;

        public Anomaly(String type, String description, double severity) {
            this.type = type;
            this.description = description;
            this.severity = severity;
        }

        public String getType() { return type; }
        public double getSeverity() { return severity; }
    }

    public enum ExecutionPhase { PRE, DURING, POST }

    public interface EthicalConstraint {
        String getName();
        ConstraintValidationResult validate(Action action, Context context);
    }

    public interface AnomalyDetector {
        List<Anomaly> detect(Action action, Context context, ExecutionPhase phase);
    }

    public interface Action {
        String getDescription();
        void execute(Context context);
    }

    public interface Context { }

    public static class AuditLog {
        private final List<String> entries = new ArrayList<>();

        public void log(String msg) {
            entries.add(Instant.now() + ": " + msg);
            System.out.println("[AUDIT] " + msg);
        }

        public List<String> getEntries() { return entries; }
    }

    public static void main(String[] args) {
        EthicalGuardrailSystem system = new EthicalGuardrailSystem();

        system.registerConstraint(new EthicalConstraint() {
            @Override public String getName() { return "Pas de données personnelles"; }
            @Override
            public ConstraintValidationResult validate(Action action, Context context) {
                return new ConstraintValidationResult(getName(), true, "OK");
            }
        });

        Action action = new Action() {
            @Override public String getDescription() { return "Action de test"; }
            @Override public void execute(Context context) { System.out.println("Action exécutée"); }
        };

        Context ctx = new Context() {};
        ExecutionResult result = system.executeWithSupervision(action, ctx);
        System.out.println("Résultat: " + result.getMessage());
    }
}
