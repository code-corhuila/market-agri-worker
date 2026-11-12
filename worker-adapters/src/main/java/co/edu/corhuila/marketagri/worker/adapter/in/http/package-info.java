/**
 * Inbound HTTP adapter: only the liveness endpoint. The worker exposes no business interface
 * (norm 5.7.1, ADR-016); its real entry adapters are the queue consumers.
 */
package co.edu.corhuila.marketagri.worker.adapter.in.http;
