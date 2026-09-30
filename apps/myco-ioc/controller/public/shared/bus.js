/* Client WebSocket partagé.
 *
 * Trois corrections par rapport à l'ancien code dupliqué dans chaque page :
 *  - reconnexion avec backoff exponentiel (avant : setInterval 3 s à vie,
 *    soit ~60 erreurs console par minute indéfiniment) ;
 *  - l'état de la connexion est une donnée publique, pas un console.log —
 *    un dashboard figé et un dashboard calme doivent être distinguables ;
 *  - envoyer renvoie false quand la socket est fermée, pour que l'appelant
 *    n'affiche pas un bouton actif sur une commande jamais partie.
 */

const DELAI_MIN = 1000;
const DELAI_MAX = 30_000;

export function connecterBus() {
  const abonnes = new Map(); // type -> Set<fn>
  let socket = null;
  let delai = DELAI_MIN;
  let timer = null;
  let etat = 'connecting'; // connecting | open | closed
  let derniereMaj = 0;

  function emettre(type, charge) {
    const set = abonnes.get(type);
    if (!set) return;
    for (const fn of set) {
      try {
        fn(charge);
      } catch (err) {
        console.error(`abonné "${type}" en échec`, err);
      }
    }
  }

  function majEtat(nouvel) {
    if (etat === nouvel) return;
    etat = nouvel;
    emettre('etat', { etat, derniereMaj });
  }

  function connecter() {
    const protocole = location.protocol === 'https:' ? 'wss:' : 'ws:';
    majEtat('connecting');
    socket = new WebSocket(`${protocole}//${location.host}/ws`);

    socket.onopen = () => {
      delai = DELAI_MIN;
      majEtat('open');
    };

    socket.onmessage = (evt) => {
      let message;
      try {
        message = JSON.parse(evt.data);
      } catch {
        // Un message illisible ne doit pas tuer le flux : on le compte et
        // on continue.
        emettre('erreur', { raison: 'message illisible' });
        return;
      }
      derniereMaj = Date.now();
      emettre(message.type, message.payload);
      emettre('*', message);
    };

    socket.onclose = () => {
      majEtat('closed');
      planifierReconnexion();
    };

    socket.onerror = () => {
      // onclose suit toujours : on n'y journalise rien pour éviter le
      // « [object Event] » répété des anciennes versions.
      majEtat('closed');
    };
  }

  function planifierReconnexion() {
    if (timer) return;
    timer = setTimeout(() => {
      timer = null;
      connecter();
    }, delai);
    delai = Math.min(delai * 2, DELAI_MAX);
  }

  connecter();

  return {
    /** @param {string} type  type de message, ou 'etat' / 'erreur' / '*' */
    sur(type, fn) {
      if (!abonnes.has(type)) abonnes.set(type, new Set());
      abonnes.get(type).add(fn);
      return () => abonnes.get(type).delete(fn);
    },

    /** @returns {boolean} true si la commande est réellement partie. */
    envoyer(commande) {
      if (!socket || socket.readyState !== WebSocket.OPEN) return false;
      socket.send(JSON.stringify({ type: 'command', ...commande }));
      return true;
    },

    get etat() {
      return etat;
    },

    get derniereMaj() {
      return derniereMaj;
    },
  };
}

/**
 * Câble un indicateur de connexion et renvoie une fonction de
 * rafraîchissement (« mis à jour il y a N s ») à appeler périodiquement.
 */
export function brancherIndicateur(bus, element, controlesASuspendre = []) {
  const texte = element.querySelector('[data-role="texte"]');

  function rendre() {
    const etat = bus.etat;
    element.dataset.etat = etat;
    const ecoule = bus.derniereMaj ? Math.round((Date.now() - bus.derniereMaj) / 1000) : null;

    if (etat === 'open') {
      texte.textContent = ecoule !== null && ecoule > 3
        ? `Connecté · données d'il y a ${ecoule} s`
        : 'Connecté';
    } else if (etat === 'connecting') {
      texte.textContent = 'Reconnexion…';
    } else {
      texte.textContent = ecoule !== null
        ? `Déconnecté · dernières données il y a ${ecoule} s`
        : 'Déconnecté — le controller ne répond pas';
    }

    // Une commande qui ne peut pas partir ne doit pas avoir l'air cliquable.
    const hs = etat !== 'open';
    for (const ctrl of controlesASuspendre) ctrl.disabled = hs;
  }

  bus.sur('etat', rendre);
  rendre();
  return rendre;
}
