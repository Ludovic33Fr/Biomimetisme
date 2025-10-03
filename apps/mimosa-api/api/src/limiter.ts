import type { IpState, TripEvent } from './types.js';


const nowMs = () => Date.now();
const nowSec = () => Math.floor(nowMs() / 1000);


export interface LimiterConfig {
windowS: number; // taille de la fenêtre pour le RPS
thresholdRps: number; // seuil RPS
pathDiversity: number; // nb de chemins distincts en 1s déclenchant le repli
tripMs: number; // durée de repli
}

export class MimosaLimiter {
    private states = new Map<string, IpState>();
    private cfg: LimiterConfig;
    onTrip?: (ev: TripEvent) => void;
    onRecover?: (ip: string) => void;

    constructor(config: LimiterConfig) {
        this.cfg = config;
    }

    private state(ip: string): IpState {
        if (!this.states.has(ip)) {
            this.states.set(ip, {
                buckets: new Array(this.cfg.windowS).fill(0),
                lastSec: nowSec(),
                lastRps: 0,
                trippedUntil: 0,
                lastReason: undefined,
                pathsBySec: new Map()
            });
        }
        return this.states.get(ip)!;
    }

    private rollBuckets(st: IpState, curSec: number): void {
        const diff = curSec - st.lastSec;
        if (diff <= 0) return;
        const len = this.cfg.windowS;
        for (let i = 1; i <= diff && i <= len; i++) {
            // décale et purge les secondes anciennes
            st.buckets.shift();
            st.buckets.push(0);
        }
        // nettoyage des entrées paths anciennes
        for (const sec of [...st.pathsBySec.keys()]) {
            if (sec < curSec - 1) st.pathsBySec.delete(sec);
        }
        st.lastSec = curSec;
    }
    
    
    private computeRps(st: IpState): number {
    const sum = st.buckets.reduce((a, b) => a + b, 0);
    // RPS moyen sur la fenêtre (approche lisible pour la démo)
    const rps = sum / this.cfg.windowS;
    st.lastRps = rps;
    return rps;
    }
    
    
    isTripped(ip: string): { tripped: boolean; ttlMs: number } {
    const st = this.state(ip);
    const ttl = Math.max(0, st.trippedUntil - nowMs());
    if (ttl <= 0 && st.trippedUntil > 0) {
    st.trippedUntil = 0;
    st.lastReason = undefined;
    this.onRecover?.(ip);
    return { tripped: false, ttlMs: 0 };
    }
    return { tripped: ttl > 0, ttlMs: ttl };
    }
    
    
    record(ip: string, path: string): { rps: number; trippedNow?: { ttlMs: number; reason: string } } {
    const st = this.state(ip);
    const curSec = nowSec();
    this.rollBuckets(st, curSec);
    
    
    // incrément bucket courant
    st.buckets[st.buckets.length - 1]++;
    // diversité de chemins sur la seconde courante
    if (!st.pathsBySec.has(curSec)) st.pathsBySec.set(curSec, new Set());
    st.pathsBySec.get(curSec)!.add(path);
    
    
    const rps = this.computeRps(st);
    const diverseNow = st.pathsBySec.get(curSec)?.size ?? 0;
    
    
    const already = this.isTripped(ip);
    if (already.tripped) {
    return { rps };
    }
    
    
    let reason = '';
    if (rps >= this.cfg.thresholdRps) {
    reason = `RPS>=${this.cfg.thresholdRps} (actuel≈${rps.toFixed(1)})`;
    } else if (diverseNow >= this.cfg.pathDiversity) {
    reason = `Diversité chemins>=${this.cfg.pathDiversity} (actuel=${diverseNow})`;
    }
    
    
    if (reason) {
    st.trippedUntil = nowMs() + this.cfg.tripMs;
    st.lastReason = reason;
    const ev = { ip, ttlMs: this.cfg.tripMs, reason };
    this.onTrip?.(ev);
    return { rps, trippedNow: ev };
    }
    
    
    return { rps };
    }
    
    
    snapshot(limit = 20) {
    const items = [...this.states.entries()].map(([ip, st]) => {
    const ttl = Math.max(0, st.trippedUntil - nowMs());
    return { ip, rps: st.lastRps, tripped: ttl > 0, ttlMs: ttl, reason: st.lastReason ?? null };
    });
    return items
    .sort((a, b) => (b.tripped ? 1 : 0) - (a.tripped ? 1 : 0) || b.rps - a.rps)
    .slice(0, limit);
    }
    
    // Méthodes pour le dashboard
    getCurrentRps(): number {
        const now = nowSec();
        let totalRps = 0;
        let count = 0;
        
        for (const [ip, st] of this.states) {
            this.rollBuckets(st, now);
            const rps = this.computeRps(st);
            totalRps += rps;
            count++;
        }
        
        return count > 0 ? totalRps / count : 0;
    }
    
    getCurrentDiversity(): number {
        const now = nowSec();
        let totalDiversity = 0;
        let count = 0;
        
        for (const [ip, st] of this.states) {
            this.rollBuckets(st, now);
            const currentDiversity = st.pathsBySec.get(now)?.size ?? 0;
            totalDiversity += currentDiversity;
            count++;
        }
        
        return count > 0 ? totalDiversity / count : 0;
    }
}