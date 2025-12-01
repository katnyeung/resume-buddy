"""
Chaos Simulation Engine
Simulates architectural failures and calculates SLA impact
"""
from typing import Dict, Any, List
import copy


class ChaosSimulator:
    """
    Simulates chaos attacks on system architecture
    Calculates SLA metrics and detects cascading failures
    """

    def inject_attack(
        self,
        system_state: Dict[str, Any],
        attack_vector: str,
        attack_params: str,
        anti_pattern: str,
        is_protected: bool = False,
        attack_count: int = 1
    ) -> Dict[str, Any]:
        """
        Execute chaos attack on system

        Args:
            system_state: Current architecture + SLA
            attack_vector: Type of attack (concurrent_writes, latency_spike, etc.)
            attack_params: Attack parameters (severity, target, duration)
            anti_pattern: Initial flaw (shared_database, no_circuit_breaker, etc.)
            is_protected: Whether the corresponding fix has been deployed
            attack_count: Number of times this attack has been used (for escalation)

        Returns:
            {
                "sla_metrics": {uptime, latency, error_rate, requests_per_second},
                "failures": [{service, type, severity}],
                "cascading": [{from, to, impact}],
                "was_mitigated": bool (True if attack had minimal impact due to fix),
                "escalation_level": int (1, 2, 3, etc.)
            }
        """
        # Parse attack params
        params = self._parse_attack_params(attack_params)

        # If system is protected, attack has MINIMAL impact (BUT can still escalate!)
        if is_protected:
            print(f"   🛡️ System is PROTECTED against {attack_vector}!")
            if attack_count > 1:
                print(f"   ⚠️ ESCALATION: Attack intensity increased ({attack_count}x)!")
                return self._simulate_escalated_mitigated_attack(system_state, attack_vector, attack_count)
            else:
                print(f"   Attack mitigated at baseline level.")
                return self._simulate_mitigated_attack(system_state, attack_vector, attack_count)

        # Simulate based on attack vector + anti-pattern combination
        if attack_vector == "concurrent_writes" and anti_pattern == "shared_database":
            return self._simulate_concurrent_writes_shared_db(system_state, params)

        elif attack_vector == "latency_spike" and anti_pattern == "no_circuit_breaker":
            return self._simulate_latency_spike_no_cb(system_state, params)

        elif attack_vector == "traffic_surge" and anti_pattern == "no_rate_limiting":
            return self._simulate_traffic_surge_no_limit(system_state, params)

        elif attack_vector == "service_dependency" and anti_pattern == "sync_microservices":
            return self._simulate_sync_dependency_latency(system_state, params)

        elif attack_vector == "service_crash" and anti_pattern == "single_point_failure":
            return self._simulate_single_point_crash(system_state, params)

        else:
            # Generic attack simulation
            return self._simulate_generic_attack(system_state, attack_vector, params)

    def _simulate_mitigated_attack(
        self,
        system_state: Dict[str, Any],
        attack_vector: str,
        attack_count: int = 1
    ) -> Dict[str, Any]:
        """
        Simulate attack when system is PROTECTED by corresponding fix.
        Attack still happens, but has MINIMAL impact (system is resilient).
        """
        baseline = system_state.get("sla_baseline", {
            "uptime": 99.5,
            "latency": 200,
            "error_rate": 0.5,
            "requests_per_second": 1000
        })

        attack_descriptions = {
            "concurrent_writes": "1000 concurrent writes attempted, but database-per-service prevents locks",
            "latency_spike": "Service latency spike detected, but circuit breaker fast-fails to prevent cascade",
            "traffic_surge": "50,000 req/s traffic surge, but rate limiter queues excess traffic",
            "service_dependency": "Service latency detected, but async messaging prevents blocking",
            "service_crash": "Service crash detected, but replica promoted automatically"
        }

        return {
            "sla_metrics": {
                "uptime": max(98.0, baseline.get("uptime", 99.5) - 1.5),  # Minor degradation
                "latency": min(500, baseline.get("latency", 200) * 2),  # 2x latency (not 25x)
                "error_rate": min(2.0, baseline.get("error_rate", 0.5) + 1.5),  # Small increase
                "requests_per_second": baseline.get("requests_per_second", 1000) * 0.95  # 95% throughput
            },
            "failures": [
                {
                    "service": "system",
                    "type": "mitigated_attack",
                    "severity": "low",
                    "message": attack_descriptions.get(attack_vector, "Attack mitigated by resilience patterns")
                }
            ],
            "cascading": [],
            "was_mitigated": True,
            "escalation_level": attack_count
        }

    def _simulate_escalated_mitigated_attack(
        self,
        system_state: Dict[str, Any],
        attack_vector: str,
        attack_count: int
    ) -> Dict[str, Any]:
        """
        Simulate ESCALATED attack when system is protected.
        Attack intensity DOUBLES each time, eventually overwhelming even protected systems.
        """
        baseline = system_state.get("sla_baseline", {
            "uptime": 99.5,
            "latency": 200,
            "error_rate": 0.5,
            "requests_per_second": 1000
        })

        # Escalation factor (2x, 4x, 8x, etc.)
        escalation = 2 ** (attack_count - 1)

        # Attack descriptions show escalation
        escalated_descriptions = {
            "concurrent_writes": f"{1000 * escalation:,} concurrent writes (escalated {escalation}x!), rate limiter holding but stressed",
            "latency_spike": f"Service latency spiked to {200 * escalation}ms (escalated {escalation}x!), circuit breakers working overtime",
            "traffic_surge": f"{50000 * escalation:,} req/s traffic surge (escalated {escalation}x!), rate limiter queue growing",
            "service_dependency": f"Chain latency {escalation}x normal, async messaging preventing total cascade",
            "service_crash": f"Multiple service crashes ({escalation} instances), replicas struggling"
        }

        # SLA degradation increases with escalation (but doesn't collapse)
        degradation_factor = min(escalation * 0.5, 5.0)  # Cap at 5x degradation

        return {
            "sla_metrics": {
                "uptime": max(85.0, baseline.get("uptime", 99.5) - degradation_factor),  # Worse degradation
                "latency": min(2000, baseline.get("latency", 200) * (2 + degradation_factor)),  # Higher latency
                "error_rate": min(10.0, baseline.get("error_rate", 0.5) + degradation_factor),  # More errors
                "requests_per_second": baseline.get("requests_per_second", 1000) * (1.0 - (degradation_factor / 10))  # Lower throughput
            },
            "failures": [
                {
                    "service": "system",
                    "type": "escalated_attack",
                    "severity": "medium" if escalation < 4 else "high",
                    "message": escalated_descriptions.get(attack_vector, f"Escalated attack ({escalation}x) stressing resilience patterns")
                }
            ],
            "cascading": [],
            "was_mitigated": True,
            "escalation_level": attack_count,
            "escalation_factor": escalation
        }

    def _parse_attack_params(self, params_str: str) -> Dict[str, Any]:
        """
        Parse attack parameters from string
        Example: "severity: 1000 req/s, duration: 30s, target: database"
        """
        params = {}
        if not params_str:
            return params

        for part in params_str.split(','):
            if ':' in part:
                key, value = part.split(':', 1)
                params[key.strip()] = value.strip()

        return params

    def _simulate_concurrent_writes_shared_db(
        self,
        system_state: Dict[str, Any],
        params: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        Simulate concurrent writes to shared database
        Result: Database locks, timeout, cascading failures
        """
        baseline = system_state.get("sla_baseline", {})

        # Extract severity (default: high)
        severity = params.get("severity", "1000 writes/sec")

        # Database locks up
        return {
            "sla_metrics": {
                "uptime": 50.0,  # 50% uptime (half of requests fail)
                "latency": 5000,  # 5s (from 200ms baseline)
                "error_rate": 15.0,  # 15% errors (from 0.1%)
                "requests_per_second": baseline.get("requests_per_second", 1000) * 0.5
            },
            "failures": [
                {
                    "service": "database",
                    "type": "database_locks",
                    "severity": "critical",
                    "message": "500 write locks pending, deadlock detected"
                },
                {
                    "service": "order_service",
                    "type": "timeout",
                    "severity": "high",
                    "message": "Waiting for database locks (30s timeout)"
                },
                {
                    "service": "payment_service",
                    "type": "timeout",
                    "severity": "high",
                    "message": "Stuck waiting for locks (0 throughput)"
                }
            ],
            "cascading": [
                {
                    "from": "database",
                    "to": "order_service",
                    "impact": "Both services write to same table → locks block each other"
                },
                {
                    "from": "database",
                    "to": "payment_service",
                    "impact": "Payment confirmations blocked by order write locks"
                }
            ]
        }

    def _simulate_latency_spike_no_cb(
        self,
        system_state: Dict[str, Any],
        params: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        Simulate latency spike without circuit breaker
        Result: Cascading timeouts, thread exhaustion
        """
        baseline = system_state.get("sla_baseline", {})

        return {
            "sla_metrics": {
                "uptime": 0.0,  # Total outage
                "latency": 30000,  # 30s (all requests timeout)
                "error_rate": 100.0,  # 100% errors
                "requests_per_second": 0
            },
            "failures": [
                {
                    "service": "database",
                    "type": "latency_spike",
                    "severity": "critical",
                    "message": "Latency increased: 200ms → 3000ms"
                },
                {
                    "service": "video_service",
                    "type": "timeout",
                    "severity": "critical",
                    "message": "All threads blocked waiting for database (30s timeout)"
                },
                {
                    "service": "api_gateway",
                    "type": "thread_exhaustion",
                    "severity": "critical",
                    "message": "All worker threads blocked → new requests rejected"
                }
            ],
            "cascading": [
                {
                    "from": "database",
                    "to": "video_service",
                    "impact": "Video service waits 30s for each DB call → thread exhaustion"
                },
                {
                    "from": "video_service",
                    "to": "api_gateway",
                    "impact": "API gateway waits for video service → all threads blocked"
                }
            ]
        }

    def _simulate_traffic_surge_no_limit(
        self,
        system_state: Dict[str, Any],
        params: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        Simulate traffic surge without rate limiting
        Result: Resource exhaustion, OOM crash
        """
        return {
            "sla_metrics": {
                "uptime": 0.0,  # Service crashed
                "latency": 0,  # No response (crashed)
                "error_rate": 100.0,
                "requests_per_second": 0
            },
            "failures": [
                {
                    "service": "api_gateway",
                    "type": "resource_exhaustion",
                    "severity": "critical",
                    "message": "50,000 req/s (from 1,000 baseline)"
                },
                {
                    "service": "backend",
                    "type": "out_of_memory",
                    "severity": "critical",
                    "message": "Heap exhausted → JVM crash"
                }
            ],
            "cascading": [
                {
                    "from": "traffic_surge",
                    "to": "backend",
                    "impact": "No rate limiting → all requests reach backend → OOM"
                }
            ]
        }

    def _simulate_sync_dependency_latency(
        self,
        system_state: Dict[str, Any],
        params: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        Simulate synchronous microservice latency propagation
        Result: Total latency = sum of all service latencies
        """
        return {
            "sla_metrics": {
                "uptime": 20.0,  # Most requests timeout
                "latency": 3300,  # Sum: 100 + 3000 + 100 + 100
                "error_rate": 80.0,
                "requests_per_second": 200
            },
            "failures": [
                {
                    "service": "pricing_service",
                    "type": "latency_spike",
                    "severity": "high",
                    "message": "Latency: 100ms → 3000ms"
                },
                {
                    "service": "all_services",
                    "type": "synchronous_blocking",
                    "severity": "critical",
                    "message": "Total latency = sum(all services) → timeouts"
                }
            ],
            "cascading": [
                {
                    "from": "pricing_service",
                    "to": "entire_chain",
                    "impact": "One slow service blocks entire request chain"
                }
            ]
        }

    def _simulate_single_point_crash(
        self,
        system_state: Dict[str, Any],
        params: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        Simulate single point of failure crash
        Result: Total data unavailability
        """
        return {
            "sla_metrics": {
                "uptime": 0.0,
                "latency": 0,
                "error_rate": 100.0,
                "requests_per_second": 0
            },
            "failures": [
                {
                    "service": "database",
                    "type": "crash",
                    "severity": "critical",
                    "message": "Primary database crashed (no replicas)"
                },
                {
                    "service": "all_services",
                    "type": "data_unavailable",
                    "severity": "critical",
                    "message": "Total data unavailability → complete outage"
                }
            ],
            "cascading": [
                {
                    "from": "database",
                    "to": "entire_system",
                    "impact": "No failover → total outage"
                }
            ]
        }

    def _simulate_generic_attack(
        self,
        system_state: Dict[str, Any],
        attack_vector: str,
        params: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        Generic attack simulation (fallback)
        """
        baseline = system_state.get("sla_baseline", {})

        return {
            "sla_metrics": {
                "uptime": 70.0,
                "latency": baseline.get("latency", 200) * 5,
                "error_rate": 10.0,
                "requests_per_second": baseline.get("requests_per_second", 1000) * 0.7
            },
            "failures": [
                {
                    "service": "unknown",
                    "type": attack_vector,
                    "severity": "medium",
                    "message": f"Generic attack: {attack_vector}"
                }
            ],
            "cascading": []
        }


# Singleton instance
chaos_simulator = ChaosSimulator()
