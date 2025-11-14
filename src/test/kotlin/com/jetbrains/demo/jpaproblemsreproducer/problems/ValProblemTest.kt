package com.jetbrains.demo.jpaproblemsreproducer.problems

import jakarta.persistence.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.test.annotation.Rollback

// ───────────────────────────────────────────────────────────────────────────────
// Repositories
// ───────────────────────────────────────────────────────────────────────────────

interface PersonValProblemTestRepo : JpaRepository<PersonValProblemTest, Long>
interface PersonValSolutionTestRepo : JpaRepository<PersonValSolutionTest, Long>

// ───────────────────────────────────────────────────────────────────────────────
// Entities
// ───────────────────────────────────────────────────────────────────────────────

/**
 * PROBLEM: Using 'val' for entity fields
 * KTIJ-33476
 * Issue:
 * - 'val' is compiled to Java's 'final'
 * - JPA specification requires persistent instance variables to be non-final
 * - Persistence providers populate entity fields through reflection, bypassing 'val' immutability
 * - This contradicts Kotlin's immutability guarantees
 */
@Entity
@Table(name = "person_val_problem_test")
class PersonValProblemTest(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    val name: String? = null  // PROBLEM: val will be modified by Hibernate
)

/**
 * SOLUTION: Use 'var' for all entity fields
 */
@Entity
@Table(name = "person_val_solution_test")
class PersonValSolutionTest(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var name: String? = null  // SOLUTION: var allows proper JPA handling
)

// ───────────────────────────────────────────────────────────────────────────────
// Tests
// ───────────────────────────────────────────────────────────────────────────────

@DataJpaTest
class ValProblemTest(
    @Autowired private val personValProblemRepo: PersonValProblemTestRepo,
    @Autowired private val personValSolutionRepo: PersonValSolutionTestRepo,
) {

    // 1) Val field is modified by Hibernate via reflection
    @Test
    @Rollback
    fun valProblem_fieldModifiedByHibernate() {
        val person = personValProblemRepo.save(PersonValProblemTest(name = "John"))
        val loaded = personValProblemRepo.findById(person.id!!).orElseThrow()

        assertNotNull(
            loaded.name,
            "Val field was modified by Hibernate via reflection, violating Kotlin's immutability guarantee"
        )
        assertEquals("John", loaded.name)
    }

    // 2) Var field works correctly with Hibernate
    @Test
    @Rollback
    fun varSolution_worksCorrectly() {
        val person = personValSolutionRepo.save(PersonValSolutionTest(name = "Jane"))
        val loaded = personValSolutionRepo.findById(person.id!!).orElseThrow()

        assertNotNull(loaded.name)
        assertEquals("Jane", loaded.name)
    }
}
