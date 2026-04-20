package reparation;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class SystemeAutoReparateur {

    private static final Logger logger = Logger.getLogger(SystemeAutoReparateur.class.getName());

    private Map<String, List<ServiceInstance>> registreServices;
    private ScheduledExecutorService executeur;
    private Map<String, Integer> historiqueDefaillances;

    public SystemeAutoReparateur() {
        this.registreServices = new ConcurrentHashMap<>();
        this.executeur = Executors.newScheduledThreadPool(2);
        this.historiqueDefaillances = new ConcurrentHashMap<>();

        demarrerMonitoring();
        demarrerAutoReparation();
    }

    private void demarrerMonitoring() {
        executeur.scheduleAtFixedRate(() -> {
            logger.info("Vérification de l'état des services...");
            for (String serviceId : registreServices.keySet()) {
                verifierService(serviceId);
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    private void demarrerAutoReparation() {
        executeur.scheduleAtFixedRate(() -> {
            logger.info("Analyse et réparation du système...");
            for (String serviceId : registreServices.keySet()) {
                reparerServiceSiNecessaire(serviceId);
            }
            analyserHistoriqueDefaillances();
        }, 5, 30, TimeUnit.SECONDS);
    }

    public void enregistrerService(String serviceId, ServiceInstance instance) {
        registreServices.computeIfAbsent(serviceId, k -> new CopyOnWriteArrayList<>()).add(instance);
        logger.info("Service enregistré : " + serviceId + " - Instance : " + instance.getId());
    }

    private void verifierService(String serviceId) {
        List<ServiceInstance> instances = registreServices.get(serviceId);
        if (instances == null || instances.isEmpty()) {
            logger.warning("Aucune instance trouvée pour le service : " + serviceId);
            return;
        }

        Iterator<ServiceInstance> it = instances.iterator();
        while (it.hasNext()) {
            ServiceInstance instance = it.next();
            try {
                boolean estFonctionnelle = instance.verifierEtat();

                if (!estFonctionnelle) {
                    logger.warning("Défaillance détectée : " + serviceId + " - " + instance.getId());
                    instance.isoler();
                    historiqueDefaillances.merge(serviceId, 1, Integer::sum);
                    instances.remove(instance);
                    notifierDefaillance(serviceId, instance);
                }
            } catch (Exception e) {
                logger.severe("Erreur vérification : " + instance.getId() + " - " + e.getMessage());
                instances.remove(instance);
            }
        }
    }

    private void reparerServiceSiNecessaire(String serviceId) {
        List<ServiceInstance> instances = registreServices.get(serviceId);
        if (instances == null) return;

        int nombreMinInstances = determinerNombreMinInstances(serviceId);
        int nombreActuel = instances.size();

        if (nombreActuel < nombreMinInstances) {
            logger.info("Réparation : " + serviceId + " - " + nombreActuel + "/" + nombreMinInstances);

            for (int i = 0; i < nombreMinInstances - nombreActuel; i++) {
                try {
                    ServiceInstance nouvelleInstance = creerNouvelleInstance(serviceId);
                    enregistrerService(serviceId, nouvelleInstance);
                    logger.info("Nouvelle instance : " + nouvelleInstance.getId());
                } catch (Exception e) {
                    logger.severe("Échec création : " + e.getMessage());
                }
            }
        }
    }

    private int determinerNombreMinInstances(String serviceId) {
        int nombreBase = 2;
        int nombreDefaillances = historiqueDefaillances.getOrDefault(serviceId, 0);

        if (nombreDefaillances > 10) return nombreBase + 2;
        if (nombreDefaillances > 5) return nombreBase + 1;
        return nombreBase;
    }

    private ServiceInstance creerNouvelleInstance(String serviceId) {
        return new ServiceInstanceSimulee(serviceId, UUID.randomUUID().toString());
    }

    private void notifierDefaillance(String serviceId, ServiceInstance instance) {
        logger.info("Notification défaillance : " + serviceId + " - " + instance.getId());
    }

    private void analyserHistoriqueDefaillances() {
        historiqueDefaillances.entrySet().stream()
            .filter(entry -> entry.getValue() > 5)
            .forEach(entry -> logger.warning(
                "Service problématique : " + entry.getKey() + " - défaillances : " + entry.getValue()));
    }

    public interface ServiceInstance {
        String getId();
        String getServiceId();
        boolean verifierEtat();
        void isoler();
    }

    public static class ServiceInstanceSimulee implements ServiceInstance {
        private String serviceId;
        private String instanceId;
        private boolean fonctionnelle;
        private Random random;

        public ServiceInstanceSimulee(String serviceId, String instanceId) {
            this.serviceId = serviceId;
            this.instanceId = instanceId;
            this.fonctionnelle = true;
            this.random = new Random();
        }

        @Override public String getId() { return instanceId; }
        @Override public String getServiceId() { return serviceId; }

        @Override
        public boolean verifierEtat() {
            if (random.nextDouble() < 0.05) fonctionnelle = false;
            return fonctionnelle;
        }

        @Override
        public void isoler() {
            logger.info("Isolation : " + instanceId);
        }
    }

    public static void main(String[] args) {
        SystemeAutoReparateur systeme = new SystemeAutoReparateur();

        systeme.enregistrerService("service-authentification",
            new ServiceInstanceSimulee("service-authentification", "auth-1"));
        systeme.enregistrerService("service-authentification",
            new ServiceInstanceSimulee("service-authentification", "auth-2"));
        systeme.enregistrerService("service-paiement",
            new ServiceInstanceSimulee("service-paiement", "payment-1"));
        systeme.enregistrerService("service-paiement",
            new ServiceInstanceSimulee("service-paiement", "payment-2"));

        try {
            Thread.sleep(5 * 60 * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
