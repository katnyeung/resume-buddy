"""
System Architect AI Service
Auto-deploys fixes and generates teaching explanations
"""
from typing import Dict, Any
import copy


class SystemArchitect:
    """
    AI System Architect that responds to chaos attacks
    Deploys architectural fixes and teaches patterns
    """

    # Fix patterns mapped to anti-patterns
    FIX_PATTERNS = {
        "shared_database": {
            "fix": "database_per_service",
            "name": "Database per Service",
            "explanation": "Each microservice now owns its data. Order Service has its own database, Payment Service has its own. No more cross-service locks. This enables independent scaling and prevents cascading failures.",
            "why": "Prevents coupling between services. Each team owns their data, enabling independent deployments and scaling.",
            "real_world": "Amazon and Netflix use this pattern. Each team has their own database.",
            "diagram_change": "Split database into separate instances per service"
        },
        "no_circuit_breaker": {
            "fix": "circuit_breaker",
            "name": "Circuit Breaker Pattern (Hystrix)",
            "explanation": "Circuit breakers fail fast when downstream services are unhealthy. Open circuit = fast fail in 200ms instead of 30s timeout. This gives the slow service time to recover.",
            "why": "Prevents cascading failures by isolating failures. Thread exhaustion is prevented.",
            "real_world": "Netflix uses Hystrix for this. AWS ALB has built-in circuit breakers.",
            "diagram_change": "Add circuit breaker component between services"
        },
        "no_rate_limiting": {
            "fix": "rate_limiting",
            "name": "Rate Limiting (Token Bucket)",
            "explanation": "Rate limiting prevents resource exhaustion. 1000 req/s per IP, burst up to 5000. Fair queuing ensures everyone gets a chance.",
            "why": "Protects against abuse (accidental or malicious). Ensures fair resource allocation.",
            "real_world": "GitHub, Twitter, Stripe all use rate limiting on their APIs.",
            "diagram_change": "Add rate limiter at API gateway"
        },
        "sync_microservices": {
            "fix": "async_messaging",
            "name": "Async Messaging (Kafka/RabbitMQ)",
            "explanation": "Services now communicate via message queue. User Service publishes 'LocationUpdated' event. Pricing Service consumes when ready. Latency doesn't propagate.",
            "why": "Decouples services. One slow service doesn't block the entire chain.",
            "real_world": "Uber, LinkedIn use Kafka for async event streaming.",
            "diagram_change": "Add message queue between services"
        },
        "single_point_failure": {
            "fix": "redundancy",
            "name": "Redundancy + Failover",
            "explanation": "Multi-region replication with automatic failover. Read replicas in 3 regions. If primary fails, replica promoted automatically.",
            "why": "CAP theorem: Can't have perfect availability without redundancy. Accept eventual consistency.",
            "real_world": "AWS RDS Multi-AZ, Google Spanner use this.",
            "diagram_change": "Add replica nodes with failover arrows"
        },
        "no_retry_logic": {
            "fix": "retry_with_backoff",
            "name": "Retry Logic (Exponential Backoff)",
            "explanation": "Retry failed requests with exponential backoff: 100ms, 200ms, 400ms, 800ms. Prevents retry storms.",
            "why": "Handles transient failures without overwhelming the service.",
            "real_world": "AWS SDK, Google Cloud libraries all use exponential backoff.",
            "diagram_change": "Add retry logic with backoff timings"
        },
        "tight_coupling": {
            "fix": "loose_coupling",
            "name": "Loose Coupling (API Gateway)",
            "explanation": "API Gateway decouples frontend from backend. Services register themselves. Frontend doesn't need to know about all backends.",
            "why": "Enables independent deployment. Services can change without breaking clients.",
            "real_world": "Kong, AWS API Gateway provide this.",
            "diagram_change": "Add API Gateway layer"
        }
    }

    def analyze_and_fix(
        self,
        anti_pattern: str,
        chaos_result: Dict[str, Any],
        system_state: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        Analyze failure and deploy appropriate fix

        Args:
            anti_pattern: Initial architectural flaw
            chaos_result: Results from chaos simulation
            system_state: Current system architecture

        Returns:
            {
                "fix": "database_per_service",
                "name": "Database per Service",
                "explanation": "...",
                "why": "...",
                "real_world": "...",
                "config": {...},
                "new_system_state": {...}
            }
        """
        fix_pattern = self.FIX_PATTERNS.get(anti_pattern)

        if not fix_pattern:
            # Fallback for unknown anti-pattern
            return self._generic_fix(anti_pattern, chaos_result, system_state)

        # Apply fix to system state
        new_system_state = self._apply_fix(
            system_state,
            fix_pattern["fix"],
            anti_pattern
        )

        # Calculate post-fix SLA (improved but not perfect)
        post_fix_sla = self._calculate_post_fix_sla(
            chaos_result["sla_metrics"],
            fix_pattern["fix"]
        )

        return {
            "fix": fix_pattern["fix"],
            "name": fix_pattern["name"],
            "explanation": fix_pattern["explanation"],
            "why": fix_pattern["why"],
            "real_world": fix_pattern["real_world"],
            "diagram_change": fix_pattern["diagram_change"],
            "post_fix_sla": post_fix_sla,
            "new_system_state": new_system_state
        }

    def _apply_fix(
        self,
        system_state: Dict[str, Any],
        fix: str,
        anti_pattern: str
    ) -> Dict[str, Any]:
        """
        Apply architectural fix to system state
        """
        new_state = copy.deepcopy(system_state)

        # Modify architecture based on fix type
        if fix == "database_per_service":
            # Split shared database into separate databases
            services = new_state.get("services", [])
            for service in services:
                if service.get("type") == "database":
                    service["name"] = service["name"] + " (per service)"

        elif fix == "circuit_breaker":
            # Add circuit breaker components
            services = new_state.get("services", [])
            services.append({
                "id": "circuit_breaker",
                "name": "Circuit Breaker (Hystrix)",
                "type": "resilience_pattern"
            })

        elif fix == "rate_limiting":
            # Add rate limiter at API gateway
            services = new_state.get("services", [])
            for service in services:
                if service.get("type") == "gateway":
                    service["rate_limiter"] = "1000 req/s per IP"

        elif fix == "async_messaging":
            # Add message queue
            services = new_state.get("services", [])
            services.append({
                "id": "kafka",
                "name": "Kafka Message Queue",
                "type": "message_queue"
            })

        elif fix == "redundancy":
            # Add replica nodes
            services = new_state.get("services", [])
            db_count = sum(1 for s in services if s.get("type") == "database")
            services.append({
                "id": "db_replica",
                "name": f"Database Replica ({db_count + 1}x redundancy)",
                "type": "database_replica"
            })

        return new_state

    def _calculate_post_fix_sla(
        self,
        sla_after_attack: Dict[str, Any],
        fix: str
    ) -> Dict[str, Any]:
        """
        Calculate SLA after fix is deployed
        Fix improves metrics but doesn't fully restore (shows degraded but stable)
        """
        # Fix improves uptime significantly
        uptime_improvement = {
            "database_per_service": 0.95,  # 95% recovery
            "circuit_breaker": 0.95,
            "rate_limiting": 0.90,
            "async_messaging": 0.92,
            "redundancy": 0.98
        }

        improvement = uptime_improvement.get(fix, 0.90)

        return {
            "uptime": min(99.5, sla_after_attack.get("uptime", 0) + (100 - sla_after_attack.get("uptime", 0)) * improvement),
            "latency": max(250, sla_after_attack.get("latency", 5000) * 0.1),  # 10x improvement
            "error_rate": max(0.5, sla_after_attack.get("error_rate", 15) * 0.05),  # 95% reduction
            "requests_per_second": sla_after_attack.get("requests_per_second", 0) * 1.8  # Near baseline
        }

    def _generic_fix(
        self,
        anti_pattern: str,
        chaos_result: Dict[str, Any],
        system_state: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        Fallback fix for unknown anti-patterns
        """
        return {
            "fix": "generic_resilience",
            "name": "Generic Resilience Pattern",
            "explanation": f"Deployed resilience measures to address {anti_pattern}.",
            "why": "Improves system robustness.",
            "real_world": "Industry standard practice.",
            "diagram_change": "Add resilience components",
            "post_fix_sla": {
                "uptime": 90.0,
                "latency": 500,
                "error_rate": 2.0,
                "requests_per_second": chaos_result["sla_metrics"].get("requests_per_second", 0) * 1.5
            },
            "new_system_state": system_state
        }


# Singleton instance
system_architect = SystemArchitect()
