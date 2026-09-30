/* Briques d'interface communes aux deux dashboards. */

import { ech, heure } from './format.js';
import { icone } from './icons.js';

/* --- Bande de narration de la démo guidée ------------------------------- */

export function creerNarration(element) {
  const etape = element.querySelector('[data-role="etape"]');
  const message = element.querySelector('[data-role="message"]');
  const jauge = element.querySelector('[data-role="jauge"]');
  let timer = null;

  return {
    montrer({ step, total, message: texte, running }) {
      etape.textContent = total > 0 ? `Étape ${step} sur ${total}` : 'Démo guidée';
      message.textContent = texte;
      jauge.style.transform = `scaleX(${total > 0 ? step / total : 0})`;
      element.classList.add('visible');

      if (timer) clearTimeout(timer);
      if (!running) {
        timer = setTimeout(() => element.classList.remove('visible'), 12_000);
      }
      return running;
    },
    cacher() {
      if (timer) clearTimeout(timer);
      element.classList.remove('visible');
    },
  };
}

/* --- Traduction d'un événement en phrase française ---------------------- */

const PHRASES = {
  hello: e => ({ icone: 'arbre', ton: 'ok', texte: `${e.nodeId} a rejoint le mycélium` }),
  alert: e => ({
    icone: 'alerte',
    ton: 'attaque',
    texte:
      `${e.nodeId} a détecté une attaque` +
      (e.src_ip ? ` depuis ${e.src_ip}` : '') +
      (e.count_5s ? ` (${e.count_5s} tentatives)` : ''),
  }),
  drop: e => ({
    icone: 'bouclier',
    ton: 'protege',
    texte: `${e.nodeId} a bloqué une requête${e.ip ? ` de ${e.ip}` : ''}`,
  }),
  'ioc.local': e => ({
    icone: 'cible',
    ton: 'signal',
    texte:
      `${e.source} propose de bloquer ${e.value} — ` +
      `${e.voteCount} voix sur ${e.quorumRequired} requises`,
  }),
  'ioc.share': e => ({
    icone: 'diffusion',
    ton: 'signal',
    texte:
      `Quorum atteint : ${e.value} est propagé à ` +
      `${(e.targets || []).length} arbre${(e.targets || []).length > 1 ? 's' : ''}`,
  }),
  ack: e => ({ icone: 'validation', ton: 'protege', texte: `${e.nodeId} applique le blocage` }),
  node_isolated: e => ({
    icone: 'prise',
    ton: 'isole',
    texte: `${e.nodeId} est coupé du mycélium${e.reason ? ` — ${e.reason}` : ''}`,
  }),
  node_reconnected: e => ({
    icone: 'arbre',
    ton: 'ok',
    texte: `${e.nodeId} a retrouvé le mycélium`,
  }),
  node_gone: e => ({
    icone: 'croix',
    ton: 'isole',
    texte: `${e.nodeId} a disparu du réseau (silencieux depuis ${e.silenceSec} s) — retiré de la topologie`,
  }),
  slo_alert: e => ({ icone: 'alerte', ton: 'attaque', texte: e.message }),
  reset: () => ({
    icone: 'reinitialiser',
    ton: 'signal',
    texte: 'Remise à zéro : toutes les protections ont été levées, le trafic est arrêté',
  }),
};

export function phraseEvenement(evenement) {
  const fabrique = PHRASES[evenement.kind];
  if (!fabrique) return null;
  return fabrique(evenement);
}

export function rendreEvenements(conteneur, evenements, limite = 40) {
  const lignes = evenements.slice(0, limite).map(ev => ({ ...phraseEvenement(ev), ts: ev.ts }));
  const utiles = lignes.filter(l => l && l.texte);

  if (utiles.length === 0) {
    conteneur.innerHTML =
      '<li><p class="vide">Rien ne s\'est encore passé. Lancez la démo guidée ' +
      'ou déclenchez une attaque pour voir le mycélium réagir.</p></li>';
    return;
  }

  conteneur.innerHTML = utiles
    .map(
      l => `<li class="evenement" data-ton="${l.ton}">
        <span class="evenement-icone">${icone(l.icone, { taille: 16 })}</span>
        <span class="evenement-texte">${ech(l.texte)}</span>
        <time class="evenement-heure donnee">${ech(heure(l.ts))}</time>
      </li>`,
    )
    .join('');
}

/* --- Contrôles de trafic ------------------------------------------------ */

/**
 * Câble les boutons [data-trafic] et le sélecteur de cible.
 * Le bouton ne prend l'état actif que si la commande est *réellement* partie :
 * afficher un bouton allumé sur une socket fermée est un mensonge.
 */
export function cablerTrafic(bus, racine) {
  const boutons = [...racine.querySelectorAll('[data-trafic]')];
  const cible = racine.querySelector('[data-role="cible"]');

  for (const bouton of boutons) {
    bouton.addEventListener('click', () => {
      const commande = bouton.dataset.trafic;
      const message = { action: 'trafficControl', command: commande };
      if (commande === 'attack' && cible && cible.value) message.targetNodeId = cible.value;
      if (!bus.envoyer(message)) return;

      if (commande !== 'attack') {
        for (const b of boutons) {
          if (b.dataset.trafic !== 'attack') b.setAttribute('aria-pressed', String(b === bouton));
        }
      }
    });
  }

  return {
    majCibles(noeuds) {
      if (!cible) return;
      const precedent = cible.value;
      const ids = noeuds.map(n => n.id).sort();
      cible.innerHTML =
        '<option value="">Cible : au hasard</option>' +
        ids.map(id => `<option value="${ech(id)}">${ech(id)}</option>`).join('');
      if (ids.includes(precedent)) cible.value = precedent;
    },
  };
}

/* --- Confirmation en deux temps pour les actions destructrices ---------- */

/**
 * Premier clic : le bouton demande confirmation pendant 4 s.
 * Deuxième clic : l'action part. Pas de modale, pas d'alert() natif.
 */
const LIBELLES = new WeakMap();

export function confirmerPuis(bouton, action) {
  if (bouton.dataset.confirme === 'oui') {
    bouton.dataset.confirme = 'non';
    bouton.innerHTML = LIBELLES.get(bouton);
    action();
    return;
  }
  // innerHTML et non textContent : les boutons portent une icône SVG que
  // textContent effacerait sans pouvoir la restaurer.
  LIBELLES.set(bouton, bouton.innerHTML);
  bouton.dataset.confirme = 'oui';
  bouton.textContent = 'Confirmer ?';
  setTimeout(() => {
    if (bouton.dataset.confirme !== 'oui') return;
    bouton.dataset.confirme = 'non';
    bouton.innerHTML = LIBELLES.get(bouton);
  }, 4000);
}
