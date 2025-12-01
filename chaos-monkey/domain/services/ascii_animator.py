"""
ASCII Animation Generator
Creates visual system diagrams showing failures and fixes
Uses LLM to generate diagrams based on system state
"""
from typing import Dict, Any, List
from infrastructure.clients.grok_client import grok_client


class ASCIIAnimator:
    """
    Generates ASCII diagrams for system architecture
    Shows before/during/after states of chaos attacks
    """

    async def generate_round_animation(
        self,
        round_number: int,
        system_before: Dict[str, Any],
        chaos_result: Dict[str, Any],
        fix_result: Dict[str, Any],
        attack_vector: str
    ) -> str:
        """
        Generate SINGLE EVOLVING PANEL showing:
        - Current system state AFTER the fix (LLM-generated ASCII diagram)
        - Weakness annotation (what Chaos Monkey can exploit next)
        - SLA recovery metrics

        Args:
            round_number: Current round (1-5)
            system_before: System state before attack
            chaos_result: Chaos simulation results
            fix_result: System Architect fix results
            attack_vector: Type of attack

        Returns:
            Multi-line ASCII art string
        """
        sla_fixed = fix_result.get("post_fix_sla", {})
        fix_name = fix_result.get('name', 'Fix Deployed')

        # Generate ASCII diagram using LLM
        print(f"   🎨 Generating ASCII diagram with LLM...")
        ascii_diagram = await self._generate_system_diagram_llm(fix_result)

        # Determine next weakness based on current fix
        next_weakness = self._determine_next_weakness(fix_result.get('fix', ''))

        animation = f"""
╔══════════════════════════════════════════════════════════════════╗
║           ROUND {round_number} - SYSTEM STATE AFTER FIX                          ║
╚══════════════════════════════════════════════════════════════════╝

{ascii_diagram}

✅ FIX DEPLOYED: {fix_name}
   Uptime: {sla_fixed.get('uptime', 0):.1f}% | Latency: {sla_fixed.get('latency', 0):.0f}ms | Errors: {sla_fixed.get('error_rate', 0):.1f}%

{next_weakness}

📚 LEARNED: {fix_result.get('explanation', '')}
🌍 REAL WORLD: {fix_result.get('real_world', '')}
"""

        return animation

    async def _generate_system_diagram_llm(self, fix_result: Dict[str, Any]) -> str:
        """
        Use LLM to generate ASCII diagram showing CUMULATIVE system architecture
        AND explain HOW it handles the load
        """
        new_system_state = fix_result.get("new_system_state", {})
        services = new_system_state.get("services", [])
        post_fix_sla = fix_result.get("post_fix_sla", {})

        # Check if this was an escalated attack
        escalation_info = ""
        if "escalation_level" in fix_result:
            escalation_level = fix_result.get("escalation_level", 1)
            escalation_factor = fix_result.get("escalation_factor", 1)
            if escalation_level > 1:
                escalation_info = f"\nHandling ESCALATED load ({escalation_factor}x normal intensity!)"
                escalation_info += f"\nUptime: {post_fix_sla.get('uptime', 0):.1f}%, Latency: {post_fix_sla.get('latency', 0):.0f}ms"

        # Build description of current system state
        system_description = self._describe_system_state(services)

        system_prompt = """You are an expert at creating ASCII art diagrams of system architectures.

Generate a clean, readable ASCII diagram showing the COMPLETE system architecture AND how it handles load.

Rules:
1. Use box-drawing characters: ┌ ─ ┐ │ └ ┘ ├ ┤ ┬ ┴ ┼ ▼ ►
2. Show ALL components in the system (microservices, databases, middleware)
3. Show connections between components (arrows, lines)
4. Keep it CONCISE (max 20 lines tall, max 70 chars wide)
5. Label each component clearly
6. Show flow top-to-bottom (clients → services → databases)
7. **CRITICAL**: Add annotations explaining HOW the architecture handles high load
   - Example: "Rate Limiter: Queues 750k excess req/s, serves 50k/s"
   - Example: "Circuit Breakers: Fast-fail in 200ms (not 30s timeout)"
   - Show TRADE-OFFS: "Queue delay: 800ms, but no crashes"

Example format showing HOW it works:
```
┌────────────┐
│   Client   │ 800,000 req/s incoming!
└──────┬─────┘
       │
  ┌────▼────────┐
  │Rate Limiter │ Queues 750k, serves 50k/s
  │(Token Bucket)│ Queue delay: ~800ms
  └──────┬───────┘
         │ 50k req/s (throttled)
  ┌──────▼──────┐
  │  Services   │
  └─────────────┘

✅ No crashes (94.9% uptime)
⚠️ Trade-off: Higher latency (1400ms vs 200ms)
```

Your diagram should EXPLAIN the architecture, not just show boxes."""

        user_prompt = f"""Current System State:
{system_description}{escalation_info}

Generate an ASCII diagram showing this complete architecture.
**EXPLAIN HOW it handles the load** - show throughput numbers, queue sizes, trade-offs.
Make it educational - show WHY uptime is 94.9% despite 800k req/s attack.

Output ONLY the ASCII diagram with explanatory annotations."""

        print(f"      LLM Input: {system_description[:200]}...")

        response = await grok_client.chat(
            prompt=user_prompt,
            system_prompt=system_prompt,
            temperature=0.3,  # Low temperature for consistent diagrams
            max_tokens=500
        )

        print(f"      LLM Output: {len(response)} chars")

        return response.strip()

    def _describe_system_state(self, services: List[Dict[str, Any]]) -> str:
        """
        Describe the current system state in natural language for LLM
        """
        description_parts = []

        # Categorize services
        microservices = [s for s in services if s.get("type") == "microservice"]
        databases = [s for s in services if s.get("type") == "database"]
        resilience = [s for s in services if s.get("type") == "resilience_pattern"]
        queues = [s for s in services if s.get("type") == "message_queue"]
        replicas = [s for s in services if s.get("type") == "database_replica"]
        gateways = [s for s in services if s.get("type") == "gateway"]

        # Microservices
        if microservices:
            ms_names = ", ".join(s.get("name", "Service") for s in microservices[:3])
            description_parts.append(f"Microservices: {ms_names}")

        # Databases
        if databases:
            if any("per service" in s.get("name", "").lower() for s in databases):
                description_parts.append("Databases: SEPARATE per service (no shared database)")
            else:
                description_parts.append("Databases: SHARED single database")

        # Resilience patterns
        if resilience:
            res_names = ", ".join(s.get("name", "") for s in resilience)
            description_parts.append(f"Resilience: {res_names}")

        # Message queues
        if queues:
            queue_names = ", ".join(s.get("name", "") for s in queues)
            description_parts.append(f"Messaging: {queue_names}")

        # Replicas
        if replicas:
            description_parts.append(f"Redundancy: {len(replicas)} database replicas")

        # Rate limiting
        if any(s.get("rate_limiter") for s in gateways):
            description_parts.append("API Gateway: Rate limiting enabled (1000 req/s/IP)")

        return "\n".join(f"- {part}" for part in description_parts)

    def _draw_current_system(self, fix_result: Dict[str, Any]) -> str:
        """
        Draw CUMULATIVE system state showing ALL fixes applied so far
        This builds up the architecture over rounds, showing the full evolution
        """
        # Get the updated system state which contains all modifications
        new_system_state = fix_result.get("new_system_state", {})
        services = new_system_state.get("services", [])

        # Build the diagram by checking what's in the system
        has_separate_dbs = any("per service" in s.get("name", "").lower() for s in services if s.get("type") == "database")
        has_circuit_breaker = any(s.get("type") == "resilience_pattern" and "circuit" in s.get("name", "").lower() for s in services)
        has_rate_limiter = any(s.get("rate_limiter") for s in services)
        has_message_queue = any(s.get("type") == "message_queue" for s in services)
        has_replica = any(s.get("type") == "database_replica" for s in services)

        # Start with microservices tier (always present)
        diagram = """
┌────────────┐  ┌────────────┐  ┌────────────┐
│   Order    │  │  Payment   │  │ Inventory  │
│  Service   │  │  Service   │  │  Service   │
└──────┬─────┘  └──────┬─────┘  └──────┬─────┘"""

        # Add circuit breakers if deployed
        if has_circuit_breaker:
            diagram += """
       │                │                │
  ┌────▼────┐      ┌────▼────┐      ┌────▼────┐
  │Circuit  │      │Circuit  │      │Circuit  │
  │Breaker  │      │Breaker  │      │Breaker  │
  └────┬────┘      └────┬────┘      └────┬────┘"""

        # Add database tier
        if has_separate_dbs:
            # Separate databases per service
            diagram += """
       │                │                │
  ┌────▼────┐      ┌────▼────┐      ┌────▼────┐
  │ Order DB│      │  Pay DB │      │  Inv DB │
  └─────────┘      └─────────┘      └─────────┘"""

            # Add replicas if deployed
            if has_replica:
                diagram += """
       │                │                │
  ┌────▼────┐      ┌────▼────┐      ┌────▼────┐
  │Replica 1│      │Replica 2│      │Replica 3│
  └─────────┘      └─────────┘      └─────────┘"""
        else:
            # Shared database (initial state)
            diagram += """
       │                │                │
       └────────────────┴────────────────┘
                        │
                   ┌────▼────┐
                   │Shared DB│
                   └─────────┘"""

        # Add message queue if deployed
        if has_message_queue:
            diagram += """

┌─────────────────────────────────────────┐
│          Kafka Message Queue             │
│  (Async communication between services) │
└─────────────────────────────────────────┘"""

        # Add rate limiter annotation if deployed
        if has_rate_limiter:
            diagram += """

API Gateway → Rate Limiter (1000 req/s/IP, burst: 5000)"""

        # Add summary of protections
        protections = []
        if has_separate_dbs:
            protections.append("✅ Database per Service")
        if has_circuit_breaker:
            protections.append("✅ Circuit Breakers")
        if has_rate_limiter:
            protections.append("✅ Rate Limiting")
        if has_message_queue:
            protections.append("✅ Async Messaging")
        if has_replica:
            protections.append("✅ Database Redundancy")

        if protections:
            diagram += "\n\n" + "\n".join(protections)

        return diagram

    def _draw_current_system_OLD(self, fix_result: Dict[str, Any]) -> str:
        """
        OLD VERSION - Draw only the latest fix (WRONG!)
        Keeping this for reference
        """
        fix_type = fix_result.get("fix", "")

        if fix_type == "database_per_service":
            return """
┌────────────┐  ┌────────────┐  ┌────────────┐
│   Order    │  │  Payment   │  │ Inventory  │
│  Service   │  │  Service   │  │  Service   │
└──────┬─────┘  └──────┬─────┘  └──────┬─────┘
       │                │                │
  ┌────▼────┐      ┌────▼────┐      ┌────▼────┐
  │ Order DB│      │  Pay DB │      │  Inv DB │
  └─────────┘      └─────────┘      └─────────┘

✅ Each service owns its data
   No more shared database locks"""

        elif fix_type == "circuit_breaker":
            return """
┌────────────┐
│    API     │
│  Gateway   │
└──────┬─────┘
       │
  ┌────▼────────┐
  │ Circuit     │ 🔴 Fast-fail mode
  │ Breaker (CB)│    200ms timeout
  └──────┬──────┘
         │
  ┌──────▼──────┐
  │  Service    │ (Recovering)
  └─────────────┘

✅ Prevents cascading timeouts
   Fails fast instead of waiting"""

        elif fix_type == "rate_limiting":
            return """
┌────────────┐
│    API     │
│  Gateway   │
└──────┬─────┘
       │
  ┌────▼────────┐
  │ Rate Limiter│ 🚦 1000 req/s/IP
  │(Token Bucket)│   Burst: 5000
  └──────┬──────┘
         │
  ┌──────▼──────┐
  │  Backend    │
  └─────────────┘

✅ Prevents resource exhaustion
   Fair queuing"""

        elif fix_type == "async_messaging":
            return """
┌────────────┐      ┌────────────┐
│   Order    │      │  Payment   │
│  Service   │      │  Service   │
└──────┬─────┘      └──────▲─────┘
       │                   │
       └──────►┌────────┐◄─┘
               │ Kafka  │
               │ Queue  │
               └────────┘

✅ Decoupled services
   One slow service won't block others"""

        elif fix_type == "redundancy":
            return """
┌────────────┐
│  Primary   │ ✅ Active
│  Database  │
└──────┬─────┘
       │ Replication
  ┌────▼────┐  ┌──────────┐
  │Replica 1│  │Replica 2 │ ⏱ Standby
  └─────────┘  └──────────┘

✅ Multi-region failover
   Automatic promotion"""

        else:
            return """
┌────────────┐
│  System    │ ✅ Hardened
│  (Fixed)   │
└────────────┘

✅ Fix applied
   System more resilient"""

    def _determine_next_weakness(self, current_fix: str) -> str:
        """
        Determine what weakness to observe based on LATEST fix
        (Observes what the most recent fix introduced, showing progression)
        """
        weakness_map = {
            "database_per_service": """
⚡ CHAOS MONKEY'S OBSERVATION:
   "They split the databases. But services still call each other
   SYNCHRONOUSLY. If one service gets slow, the entire chain
   will block. Latency propagation awaits..."
""",
            "circuit_breaker": """
⚡ CHAOS MONKEY'S OBSERVATION:
   "Circuit breakers protect individual services. But they have
   NO RATE LIMITING at the API gateway. A traffic surge can
   still exhaust all resources. The gateway is vulnerable..."
""",
            "rate_limiting": """
⚡ CHAOS MONKEY'S OBSERVATION:
   "Rate limiting controls traffic. But the DATABASE itself
   has NO REDUNDANCY - just one primary. A single crash and
   the whole system goes down. Single point of failure..."
""",
            "async_messaging": """
⚡ CHAOS MONKEY'S OBSERVATION:
   "Message queues decouple services. But what if the queue
   itself gets overloaded? No backpressure handling. The queue
   is now the bottleneck..."
""",
            "redundancy": """
⚡ CHAOS MONKEY'S OBSERVATION:
   "Multi-region replication handles crashes. But there's no
   RETRY LOGIC with exponential backoff. Transient failures
   will still drop requests. Network blips are opportunities..."
""",
            "database_per_service_verified": """
⚡ CHAOS MONKEY'S OBSERVATION:
   "The database fix held. But the system has OTHER weaknesses.
   Let's find a different vulnerability to exploit..."
""",
            "circuit_breaker_verified": """
⚡ CHAOS MONKEY'S OBSERVATION:
   "Circuit breakers are working. But the system has OTHER
   weaknesses. Let's find a different attack vector..."
""",
            "rate_limiting_verified": """
⚡ CHAOS MONKEY'S OBSERVATION:
   "Rate limiting is effective. But the system has OTHER
   weaknesses. Let's find a different vulnerability..."
"""
        }

        return weakness_map.get(current_fix, """
⚡ CHAOS MONKEY'S OBSERVATION:
   "They think they're safe now. But every fix introduces
   new weaknesses. Let's see what breaks next..."
""")

    def _draw_healthy_system(self, system: Dict[str, Any]) -> str:
        """
        Draw healthy system diagram
        """
        services = system.get("services", [])

        if not services:
            return "┌────────────┐\n│   System   │ ✓\n└────────────┘"

        # Simple 2-tier architecture diagram
        microservices = [s for s in services if s.get("type") == "microservice"]
        databases = [s for s in services if s.get("type") == "database"]

        diagram = ""

        # Draw microservices tier
        if microservices:
            for i, service in enumerate(microservices[:3]):  # Limit to 3 for readability
                diagram += f"┌{'─' * 12}┐"
                if i < len(microservices) - 1:
                    diagram += "  "
            diagram += "\n"

            for i, service in enumerate(microservices[:3]):
                name = service.get("name", "Service")[:10]
                diagram += f"│ {name:^10} │"
                if i < len(microservices) - 1:
                    diagram += "  "
            diagram += " ✓\n"

            for i in range(min(3, len(microservices))):
                diagram += f"└{'─' * 6}┬{'─' * 5}┘"
                if i < min(3, len(microservices)) - 1:
                    diagram += "  "
            diagram += "\n"

            # Connections to database
            if databases:
                diagram += "       │         │         │\n"
                diagram += "       └─────────┴─────────┘\n"

        # Draw database tier
        if databases:
            diagram += "              │\n"
            diagram += "         ┌────▼────┐\n"
            db_name = databases[0].get("name", "Database")[:10]
            diagram += f"         │ {db_name:^8} │ ✓\n"
            diagram += "         └─────────┘\n"

        return diagram or "No services defined"

    def _draw_broken_system(
        self,
        system: Dict[str, Any],
        chaos_result: Dict[str, Any]
    ) -> str:
        """
        Draw system with failures
        """
        services = system.get("services", [])
        failures = chaos_result.get("failures", [])

        if not services:
            return "┌────────────┐\n│   System   │ ❌ FAILED\n└────────────┘"

        microservices = [s for s in services if s.get("type") == "microservice"]
        databases = [s for s in services if s.get("type") == "database"]

        diagram = ""

        # Draw microservices tier (with failures)
        if microservices:
            for i in range(min(3, len(microservices))):
                diagram += f"┌{'─' * 12}┐"
                if i < min(3, len(microservices)) - 1:
                    diagram += "  "
            diagram += "\n"

            for i, service in enumerate(microservices[:3]):
                name = service.get("name", "Service")[:10]
                status = self._get_failure_status(service.get("id", ""), failures)
                diagram += f"│ {name:^10} │"
                if i < min(3, len(microservices)) - 1:
                    diagram += "  "
            diagram += f" {status}\n"

            for i in range(min(3, len(microservices))):
                diagram += f"└{'─' * 6}┬{'─' * 5}┘"
                if i < min(3, len(microservices)) - 1:
                    diagram += "  "
            diagram += "\n"

            if databases:
                diagram += "       │ ⏱ TIMEOUT │ ⏱ TIMEOUT\n"
                diagram += "       └─────────┴───────────┘\n"

        # Draw database tier (with lock)
        if databases:
            diagram += "              │\n"
            diagram += "         ┌────▼────┐\n"
            db_name = databases[0].get("name", "Database")[:8]
            diagram += f"         │ {db_name:^8} │ 🔒 LOCKED\n"
            diagram += "         │ 30000ms  │ ❌\n"
            diagram += "         └─────────┘\n"

        return diagram

    def _draw_fixed_system(
        self,
        system: Dict[str, Any],
        fix_result: Dict[str, Any]
    ) -> str:
        """
        Draw system with fixes applied
        """
        services = system.get("services", [])
        fix_type = fix_result.get("fix", "")

        if not services:
            return "┌────────────┐\n│   System   │ ✓ FIXED\n└────────────┘"

        microservices = [s for s in services if s.get("type") == "microservice"]
        diagram = ""

        if fix_type == "database_per_service":
            # Show separate databases
            if microservices:
                for i in range(min(3, len(microservices))):
                    diagram += f"┌{'─' * 12}┐"
                    if i < min(3, len(microservices)) - 1:
                        diagram += "  "
                diagram += "\n"

                for i, service in enumerate(microservices[:3]):
                    name = service.get("name", "Service")[:10]
                    diagram += f"│ {name:^10} │"
                    if i < min(3, len(microservices)) - 1:
                        diagram += "  "
                diagram += " ✓ 99%\n"

                for i in range(min(3, len(microservices))):
                    diagram += f"└{'─' * 6}┬{'─' * 5}┘"
                    if i < min(3, len(microservices)) - 1:
                        diagram += "  "
                diagram += "\n"

                diagram += "       │         │         │\n"

                # Separate databases
                for i in range(min(3, len(microservices))):
                    diagram += f"  ┌────▼────┐"
                    if i < min(3, len(microservices)) - 1:
                        diagram += " "
                diagram += "\n"

                for i in range(min(3, len(microservices))):
                    diagram += f"  │ {['Order', 'Payment', 'User'][i]:^8} DB│"
                    if i < min(3, len(microservices)) - 1:
                        diagram += " "
                diagram += " ✓\n"

                for i in range(min(3, len(microservices))):
                    diagram += f"  │  250ms  │"
                    if i < min(3, len(microservices)) - 1:
                        diagram += " "
                diagram += "\n"

                for i in range(min(3, len(microservices))):
                    diagram += f"  └─────────┘"
                    if i < min(3, len(microservices)) - 1:
                        diagram += " "
                diagram += "\n"

        elif fix_type == "circuit_breaker":
            # Show circuit breaker component
            diagram += "┌────────────┐\n"
            diagram += "│ API Gateway│ ✓ 95%\n"
            diagram += "└──────┬─────┘\n"
            diagram += "       │\n"
            diagram += "  ┌────▼────┐\n"
            diagram += "  │   CB ⚡  │ 🔴 OPEN\n"
            diagram += "  └────┬────┘ (Fast fail)\n"
            diagram += "       │\n"
            diagram += "  ┌────▼────┐\n"
            diagram += "  │ Service │ ⚠ SLOW\n"
            diagram += "  └─────────┘ (Recovering)\n"

        else:
            # Generic fix visualization
            diagram += "┌────────────┐\n"
            diagram += f"│   System   │ ✓ {fix_result.get('name', 'Fixed')}\n"
            diagram += "└────────────┘\n"

        return diagram

    def _get_failure_status(self, service_id: str, failures: List[Dict]) -> str:
        """
        Get failure status icon for service
        """
        for failure in failures:
            if service_id in failure.get("service", ""):
                severity = failure.get("severity", "")
                if severity == "critical":
                    return "❌"
                elif severity == "high":
                    return "⚠"
                else:
                    return "⚡"
        return "✓"

    def _draw_failures(self, failures: List[Dict]) -> str:
        """
        Draw failure list
        """
        if not failures:
            return ""

        output = "FAILURES:\n"
        for i, failure in enumerate(failures[:3], 1):  # Limit to 3
            service = failure.get("service", "Unknown")
            msg = failure.get("message", "")
            output += f"  {i}. {service}: {msg}\n"

        return output


# Singleton instance
ascii_animator = ASCIIAnimator()
