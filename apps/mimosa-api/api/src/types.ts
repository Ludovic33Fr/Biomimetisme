export interface IpState {
    buckets: number[]; // compteur req/s par seconde
    pathsBySec: Map<number, Set<string>>; // diversité d’endpoints par seconde
    lastSec: number; // timestamp (sec) du dernier update
    trippedUntil: number; // epoch ms de fin de repli
    lastRps: number; // dernier RPS calculé
    lastReason?: string; // raison du repli
    }
    
    
export interface TripEvent {
    ip: string;
    ttlMs: number;
    reason: string;
}