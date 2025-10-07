package com.resumebuddy.repository;

import com.resumebuddy.model.graph.Occupation;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Neo4j Repository for Occupation nodes.
 *
 * Occupations use SOC code as ID (e.g., "15-1252.00").
 */
@Repository
public interface OccupationGraphRepository extends Neo4jRepository<Occupation, String> {

    /**
     * Find occupation by SOC code.
     *
     * @param code SOC code (e.g., "15-1252.00")
     * @return Occupation if found
     */
    Optional<Occupation> findByCode(String code);

    /**
     * Find occupation by title (case-insensitive).
     *
     * @param title Occupation title
     * @return Occupation if found
     */
    @Query("MATCH (o:Occupation) WHERE toLower(o.title) = toLower($title) RETURN o")
    Optional<Occupation> findByTitleIgnoreCase(@Param("title") String title);

    /**
     * Find all occupations in a category.
     *
     * @param category Category name
     * @return List of occupations
     */
    List<Occupation> findByCategory(String category);

    /**
     * Get all jobs mapped to this occupation.
     */
    @Query("""
        MATCH (j:Job)-[:MAPS_TO]->(o:Occupation {code: $socCode})
        RETURN j
        """)
    List<Object> findJobsByOccupation(@Param("socCode") String socCode);
}
