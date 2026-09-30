/* Jeu d'icônes dessinées — trait unique 1.75, grille 24, currentColor.
 * Remplace les emojis qui servaient d'icônes : un emoji est rendu
 * différemment par chaque plateforme et un lecteur d'écran le vocalise
 * par son nom CLDR au milieu d'une phrase française. */

const TRACES = {
  // Marque : une feuille nervurée.
  feuille: '<path d="M4.5 19.5C3 14 6 5.5 19.5 4.5c1 12.5-7 16.5-13.5 15.5Z"/><path d="M12.5 11.5 5.5 18.5"/>',

  arret: '<rect x="6" y="6" width="12" height="12" rx="2"/>',
  lent: '<path d="M4 17h3"/><path d="M4 12h6"/><path d="M4 7h2"/><circle cx="17" cy="12" r="3"/>',
  normal: '<path d="M4 17h5"/><path d="M4 12h9"/><path d="M4 7h7"/><circle cx="19" cy="12" r="2"/>',
  foudre: '<path d="M13 2 4 14h7l-1 8 9-12h-7l1-8Z"/>',

  lecture: '<path d="M7 4.5v15l12-7.5-12-7.5Z"/>',
  stop: '<rect x="5" y="5" width="14" height="14" rx="2"/>',

  alerte: '<path d="M12 3.5 2.5 20h19L12 3.5Z"/><path d="M12 10v4.5"/><path d="M12 17.5h.01"/>',
  bouclier: '<path d="M12 3 4.5 6v6c0 4.5 3 7.7 7.5 9 4.5-1.3 7.5-4.5 7.5-9V6L12 3Z"/>',
  diffusion: '<circle cx="12" cy="12" r="2.5"/><path d="M6.5 6.5a7.8 7.8 0 0 0 0 11"/><path d="M17.5 6.5a7.8 7.8 0 0 1 0 11"/><path d="M3.5 3.5a12 12 0 0 0 0 17"/><path d="M20.5 3.5a12 12 0 0 1 0 17"/>',
  horloge: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
  validation: '<path d="M4.5 12.5 9.5 17.5 19.5 6.5"/>',
  prise: '<path d="M9 3v5"/><path d="M15 3v5"/><path d="M6 8h12v3a6 6 0 0 1-6 6 6 6 0 0 1-6-6V8Z"/><path d="M12 17v4"/>',
  vague: '<path d="M2.5 8c2.5-2.5 5-2.5 7.5 0s5 2.5 7.5 0 4-1.5 4-1.5"/><path d="M2.5 14c2.5-2.5 5-2.5 7.5 0s5 2.5 7.5 0 4-1.5 4-1.5"/>',
  fiole: '<path d="M9.5 3v6L4.5 19a1.8 1.8 0 0 0 1.6 2.5h11.8A1.8 1.8 0 0 0 19.5 19L14.5 9V3"/><path d="M8 3h8"/>',
  croix: '<path d="M6 6l12 12"/><path d="M18 6 6 18"/>',
  plus: '<path d="M12 5.5v13"/><path d="M5.5 12h13"/>',
  cible: '<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="3.5"/>',
  reinitialiser: '<path d="M20.5 12a8.5 8.5 0 1 1-2.6-6.1"/><path d="M20.5 3.5v5h-5"/>',
  arbre: '<path d="M12 3 6 11h3l-4 6h14l-4-6h3L12 3Z"/><path d="M12 17v4"/>',
};

/**
 * @param {string} nom  clé de TRACES
 * @param {{taille?: number, classe?: string}} [opts]
 * @returns {string} markup SVG, décoratif (aria-hidden) : le libellé
 *                   textuel adjacent porte le sens.
 */
export function icone(nom, opts = {}) {
  const trace = TRACES[nom];
  if (!trace) return '';
  const taille = opts.taille ?? 18;
  const classe = opts.classe ? ` class="${opts.classe}"` : '';
  return (
    `<svg${classe} width="${taille}" height="${taille}" viewBox="0 0 24 24" ` +
    'fill="none" stroke="currentColor" stroke-width="1.75" ' +
    'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" ' +
    'focusable="false">' +
    trace +
    '</svg>'
  );
}

/** Remplit tous les [data-icone] présents dans le document. */
export function poserIcones(racine = document) {
  for (const el of racine.querySelectorAll('[data-icone]')) {
    const taille = Number(el.dataset.iconeTaille) || 18;
    el.insertAdjacentHTML('afterbegin', icone(el.dataset.icone, { taille }));
    el.removeAttribute('data-icone');
  }
}
