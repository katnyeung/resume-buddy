package com.resumebuddy.repository;

import com.resumebuddy.model.graph.Skill;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Neo4j Repository for Skill nodes.
 *
 * Skills use generated ID based on normalized name (e.g., "skill-java").
 */
@Repository
public interface SkillGraphRepository extends Neo4jRepository<Skill, String> {

    /**
     * Find skill by normalized name.
     *
     * @param name Skill name (case-insensitive)
     * @return Skill if found
     */
    @Query("MATCH (s:Skill) WHERE toLower(s.name) = toLower($name) RETURN s")
    Optional<Skill> findByNameIgnoreCase(@Param("name") String name);

    /**
     * Find all skills in a category.
     *
     * @param category Category name
     * @return List of skills
     */
    List<Skill> findByCategory(String category);

    /**
     * Find all technical skills.
     *
     * @return List of technical skills
     */
    List<Skill> findByIsTechnicalTrue();

    /**
     * Get all jobs that require this skill.
     */
    @Query("""
        MATCH (j:Job)-[:REQUIRES_SKILL]->(s:Skill {id: $skillId})
        RETURN j
        """)
    List<Object> findJobsBySkill(@Param("skillId") String skillId);

    /**
     * Get skill usage statistics across all jobs.
     */
    @Query("""
        MATCH (j:Job)-[r:REQUIRES_SKILL]->(s:Skill {id: $skillId})
        RETURN count(j) as jobCount,
               avg(r.proficiencyLevel) as avgProficiency,
               sum(r.mentionedCount) as totalMentions
        """)
    Object getSkillStatistics(@Param("skillId") String skillId);
}
