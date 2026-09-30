/* Formatage + échappement partagés par les deux dashboards. */

/**
 * Échappe une valeur avant insertion dans du HTML.
 * Indispensable : les IOCs (valeur, User-Agent, chemin) viennent du réseau
 * via le bus NATS. Sans ça, une apostrophe casse le markup et une balise
 * s'exécute.
 */
export function ech(valeur) {
  return String(valeur ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/** Heure de l'événement lui-même, jamais l'heure de rendu. */
export function heure(ts) {
  if (!ts) return '—';
  return new Date(ts).toLocaleTimeString('fr-FR');
}

export function dateHeure(ts) {
  if (!ts) return '—';
  return new Date(ts).toLocaleString('fr-FR');
}

/** Durée courte et lisible : 4 s, 1 min 12 s, 2 h 05. */
export function duree(secondes) {
  const s = Math.max(0, Math.round(secondes));
  if (s < 60) return `${s} s`;
  if (s < 3600) {
    const m = Math.floor(s / 60);
    const r = s % 60;
    return r ? `${m} min ${String(r).padStart(2, '0')} s` : `${m} min`;
  }
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  return `${h} h ${String(m).padStart(2, '0')}`;
}

/** Millisecondes → « 1 240 ms » ou « 3,2 s » selon l'ordre de grandeur. */
export function millis(ms) {
  if (ms == null || Number.isNaN(ms)) return '—';
  if (ms < 1000) return `${Math.round(ms)} ms`;
  return `${(ms / 1000).toFixed(1).replace('.', ',')} s`;
}

export function pluriel(n, singulier, plurielMot = singulier + 's') {
  return `${n} ${n > 1 ? plurielMot : singulier}`;
}

/** Libellés français des états, utilisés partout à l'identique. */
export const LIBELLE_ETAT = {
  ok: 'sain',
  protected: 'vacciné',
  detector: 'vient de détecter',
  isolated: 'coupé du mycélium',
};
