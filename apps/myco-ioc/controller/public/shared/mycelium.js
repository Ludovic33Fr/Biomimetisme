/* Topologie mycélienne.
 *
 * Ce module existe pour une seule raison : *montrer* la propagation. C'est
 * l'unique chose que la démo a à prouver, et c'était exactement ce que
 * l'ancien graphe ne dessinait pas (arêtes grises immobiles, aucun sens de
 * circulation, pas de controller représenté).
 *
 * Choix de rendu :
 *  - layout radial déterministe (thalle au centre, arbres en couronne) au
 *    lieu d'une force-simulation : stable, lisible, sans agitation
 *    permanente qui se lit comme de l'activité alors qu'il ne se passe rien ;
 *  - une seule scène animée — le voyage de l'IOC. Le reste est immobile ;
 *  - l'arbre ne change de couleur qu'à l'arrivée de son ACK réel.
 */

import { ech, LIBELLE_ETAT } from './format.js';

const L = 1200;              // largeur du viewBox
const H = 560;               // hauteur du viewBox
const CX = L / 2;
const CY = H / 2;
const R_NOEUD = 30;
const R_ANNEAU = 39;
const CIRCONFERENCE = 2 * Math.PI * R_ANNEAU;
const SVG_NS = 'http://www.w3.org/2000/svg';

const mouvementReduit = () =>
  window.matchMedia('(prefers-reduced-motion: reduce)').matches;

function el(nom, attrs = {}) {
  const noeud = document.createElementNS(SVG_NS, nom);
  for (const [k, v] of Object.entries(attrs)) noeud.setAttribute(k, v);
  return noeud;
}

/* Couronne elliptique : les panneaux de dashboard sont larges et peu hauts.
   Un cercle y laisserait deux grands vides sur les côtés. */
const RX = 460;
const RY = 195;

function position(i, n) {
  if (n === 1) return { x: CX, y: CY - RY };
  // On part du haut et on tourne : l'ordre alphabétique des nœuds devient
  // un ordre stable à l'écran, reconnaissable d'une démo à l'autre.
  const angle = -Math.PI / 2 + (i * 2 * Math.PI) / n;
  return { x: CX + RX * Math.cos(angle), y: CY + RY * Math.sin(angle) };
}

/** Hyphe légèrement courbée entre le thalle et un arbre. */
function cheminHyphe(p) {
  const mx = (CX + p.x) / 2;
  const my = (CY + p.y) / 2;
  // décalage perpendiculaire : donne le galbe organique
  const dx = p.x - CX;
  const dy = p.y - CY;
  const long = Math.hypot(dx, dy) || 1;
  const gx = mx + (-dy / long) * 26;
  const gy = my + (dx / long) * 26;
  return `M ${CX} ${CY} Q ${gx} ${gy} ${p.x} ${p.y}`;
}

export function creerMycelium(conteneur, options = {}) {
  const compact = options.compact === true;

  const svg = el('svg', {
    viewBox: `0 0 ${L} ${H}`,
    preserveAspectRatio: 'xMidYMid meet',
    role: 'img',
    class: compact ? 'mycelium mycelium-compact' : 'mycelium',
  });
  const titre = el('title');
  titre.textContent = 'Topologie du mycélium';
  const desc = el('desc');
  desc.textContent = 'Aucun arbre connecté pour le moment.';
  svg.append(titre, desc);

  const cThalle = el('g', { class: 'myc-thalle' });
  const cHyphes = el('g', { class: 'myc-hyphes' });
  const cSpores = el('g', { class: 'myc-spores' });
  const cNoeuds = el('g', { class: 'myc-noeuds' });
  svg.append(cHyphes, cThalle, cSpores, cNoeuds);

  // Le thalle : la masse mycélienne qui relaie les IOCs (le controller).
  cThalle.append(
    el('circle', { cx: CX, cy: CY, r: 74, class: 'myc-thalle-halo' }),
    // Masse mycélienne : un contour irrégulier mais sans angle, pour que le
    // relais se lise comme un tissu vivant et non comme un nœud de plus.
    el('path', {
      class: 'myc-thalle-corps',
      d: `M ${CX - 52} ${CY - 6}
          C ${CX - 56} ${CY - 34}, ${CX - 32} ${CY - 52}, ${CX - 6} ${CY - 50}
          C ${CX + 22} ${CY - 54}, ${CX + 50} ${CY - 34}, ${CX + 52} ${CY - 8}
          C ${CX + 58} ${CY + 18}, ${CX + 36} ${CY + 46}, ${CX + 8} ${CY + 48}
          C ${CX - 20} ${CY + 52}, ${CX - 50} ${CY + 24}, ${CX - 52} ${CY - 6} Z`,
    }),
  );
  const texteThalle = el('text', {
    x: CX,
    y: CY + 4,
    class: 'myc-thalle-texte',
    'text-anchor': 'middle',
  });
  texteThalle.textContent = 'mycélium';
  cThalle.append(texteThalle);

  const vide = el('text', {
    x: CX,
    y: CY + 150,
    class: 'myc-vide',
    'text-anchor': 'middle',
  });
  vide.textContent = 'En attente des arbres…';
  svg.append(vide);

  conteneur.append(svg);

  // Résumé textuel : le SVG est décoratif pour un lecteur d'écran, cette
  // liste porte l'information réelle.
  const resume = document.createElement('ul');
  resume.className = 'sr-only';
  resume.setAttribute('aria-live', 'polite');
  conteneur.append(resume);

  /** @type {Map<string, {g: SVGGElement, p: {x:number,y:number}, hyphe: SVGPathElement}>} */
  const dessines = new Map();
  let ordre = [];

  function reconstruire(noeuds) {
    cHyphes.textContent = '';
    cNoeuds.textContent = '';
    dessines.clear();
    ordre = noeuds.map(n => n.id);

    noeuds.forEach((noeud, i) => {
      const p = position(i, noeuds.length);

      const hyphe = el('path', { class: 'myc-hyphe', d: cheminHyphe(p) });
      cHyphes.append(hyphe);

      const g = el('g', {
        class: 'myc-noeud',
        transform: `translate(${p.x} ${p.y})`,
        tabindex: '-1',
      });
      g.append(
        el('circle', { r: R_ANNEAU, class: 'myc-ttl', 'stroke-dasharray': `0 ${CIRCONFERENCE}` }),
        el('circle', { r: R_NOEUD + 6, class: 'myc-detecteur' }),
        el('circle', { r: R_NOEUD, class: 'myc-disque' }),
        el('circle', { r: R_NOEUD, class: 'myc-impulsion' }),
      );
      const label = el('text', {
        y: R_ANNEAU + 26,
        class: 'myc-label',
        'text-anchor': 'middle',
      });
      label.textContent = noeud.id;
      g.append(label);

      cNoeuds.append(g);
      dessines.set(noeud.id, { g, p, hyphe });
    });
  }

  function fractionTTL(noeud, iocsParCle, maintenant) {
    let meilleure = 0;
    for (const cle of noeud.appliedIOCs || []) {
      const ioc = iocsParCle.get(cle);
      if (!ioc) continue;
      const total = ioc.endTime - ioc.startTime;
      if (total <= 0) continue;
      meilleure = Math.max(meilleure, (ioc.endTime - maintenant) / total);
    }
    return Math.max(0, Math.min(1, meilleure));
  }

  function majResume(noeuds) {
    const parEtat = { ok: 0, protected: 0, isolated: 0 };
    for (const n of noeuds) parEtat[n.health] = (parEtat[n.health] || 0) + 1;
    desc.textContent = noeuds.length === 0
      ? 'Aucun arbre connecté pour le moment.'
      : `${noeuds.length} arbres : ${parEtat.protected} vaccinés, ` +
        `${parEtat.ok} sains, ${parEtat.isolated} coupés du mycélium.`;
    resume.innerHTML = noeuds
      .map(n => {
        const role = n.isDetector ? LIBELLE_ETAT.detector : LIBELLE_ETAT[n.health];
        return `<li>${ech(n.id)} : ${role}, ${n.alerts} alertes, ${n.drops} blocages</li>`;
      })
      .join('');
  }

  return {
    element: svg,

    /** @param {{nodes: Array, activeIOCs: Array}} etat */
    majEtat(etat) {
      const noeuds = [...etat.nodes].sort((a, b) => a.id.localeCompare(b.id));
      const ids = noeuds.map(n => n.id);
      if (ids.length !== ordre.length || ids.some((id, i) => id !== ordre[i])) {
        reconstruire(noeuds);
      }

      vide.style.display = noeuds.length ? 'none' : '';

      const iocsParCle = new Map((etat.activeIOCs || []).map(i => [i.key, i]));
      const maintenant = Date.now();

      for (const noeud of noeuds) {
        const dessin = dessines.get(noeud.id);
        if (!dessin) continue;
        const { g } = dessin;

        g.dataset.etat = noeud.health;
        g.dataset.detecteur = noeud.isDetector ? 'oui' : 'non';
        dessin.hyphe.dataset.etat = noeud.health;

        const fraction = fractionTTL(noeud, iocsParCle, maintenant);
        const arc = g.querySelector('.myc-ttl');
        arc.setAttribute(
          'stroke-dasharray',
          `${(fraction * CIRCONFERENCE).toFixed(1)} ${CIRCONFERENCE}`,
        );
        arc.style.opacity = fraction > 0 ? '1' : '0';

        const label = g.querySelector('.myc-label');
        label.textContent = noeud.id;
      }

      majResume(noeuds);
    },

    /**
     * Anime le trajet d'un IOC : arbre détecteur → thalle → arbres voisins.
     * Le repeint des cibles n'a pas lieu ici : il attend l'ACK réel.
     */
    propager({ source, targets = [] }) {
      const depart = dessines.get(source);
      if (!depart) return;

      const reduit = mouvementReduit();
      depart.hyphe.classList.add('actif');
      setTimeout(() => depart.hyphe.classList.remove('actif'), reduit ? 600 : 1400);

      if (reduit) {
        for (const cible of targets) {
          const d = dessines.get(cible);
          if (d) d.hyphe.classList.add('actif');
        }
        setTimeout(() => {
          for (const cible of targets) {
            const d = dessines.get(cible);
            if (d) d.hyphe.classList.remove('actif');
          }
        }, 600);
        return;
      }

      // La chorégraphie est séquencée par des minuteurs, pas par onfinish :
      // quand l'onglet n'est pas au premier plan, le navigateur bride les
      // timelines d'animation et onfinish arrive avec plusieurs secondes de
      // retard — ou jamais. Un écran de démo doit rester juste dans ce cas.
      const MONTEE = 420;
      const DESCENTE = 520;
      const DECALAGE = 70;

      const spore = el('circle', { r: 7, class: 'myc-spore', cx: depart.p.x, cy: depart.p.y });
      cSpores.append(spore);
      spore.animate(
        [
          { transform: 'translate(0px, 0px)' },
          { transform: `translate(${CX - depart.p.x}px, ${CY - depart.p.y}px)` },
        ],
        { duration: MONTEE, easing: 'cubic-bezier(0.16, 1, 0.3, 1)', fill: 'forwards' },
      );
      setTimeout(() => spore.remove(), MONTEE);

      setTimeout(() => {
        targets.forEach((cible, i) => {
          const arrivee = dessines.get(cible);
          if (!arrivee) return;
          arrivee.hyphe.classList.add('actif');

          const grain = el('circle', { r: 6, class: 'myc-spore', cx: CX, cy: CY });
          cSpores.append(grain);
          grain.animate(
            [
              { transform: 'translate(0px, 0px)' },
              { transform: `translate(${arrivee.p.x - CX}px, ${arrivee.p.y - CY}px)` },
            ],
            {
              duration: DESCENTE,
              delay: i * DECALAGE,
              easing: 'cubic-bezier(0.16, 1, 0.3, 1)',
              fill: 'forwards',
            },
          );
          setTimeout(() => {
            grain.remove();
            arrivee.hyphe.classList.remove('actif');
          }, DESCENTE + i * DECALAGE);
        });
      }, MONTEE);
    },

    /** L'arbre a réellement appliqué l'IOC : c'est le moment de le montrer. */
    accuser(nodeId) {
      const dessin = dessines.get(nodeId);
      if (!dessin) return;
      const impulsion = dessin.g.querySelector('.myc-impulsion');
      impulsion.classList.remove('bat');
      // reflow : relance l'animation même si l'ACK précédent était récent
      void impulsion.getBoundingClientRect();
      impulsion.classList.add('bat');
    },
  };
}
