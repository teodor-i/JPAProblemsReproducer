package com.jetbrains.demo.jpaproblemsreproducer.problems

import jakarta.persistence.*
import org.hibernate.Hibernate
import org.hibernate.collection.spi.PersistentCollection
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.test.annotation.Rollback

// ───────────────────────────────────────────────────────────────────────────────
// Repositories
// ───────────────────────────────────────────────────────────────────────────────

interface CompanyImmutableProblemTestRepo : JpaRepository<CompanyImmutableProblemTest, Long>
interface EmployeeProblemTestRepo : JpaRepository<EmployeeProblemTest, Long>
interface CompanyImmutableSolutionTestRepo : JpaRepository<CompanyImmutableSolutionTest, Long>
interface EmployeeSolutionTestRepo : JpaRepository<EmployeeSolutionTest, Long>
interface CompanyWithTrulyImmutableTestRepo : JpaRepository<CompanyWithTrulyImmutableTest, Long>
interface EmployeeImmutableTestRepo : JpaRepository<EmployeeImmutableTest, Long>

// ───────────────────────────────────────────────────────────────────────────────
// Entities
// ───────────────────────────────────────────────────────────────────────────────

/**
 * PROBLEM: Using immutable collection types (Set, List) instead of mutable collections
 * KTIJ-34851 and KTIJ-33946
 * Issue:
 * - JPA specification and implementations expect entity collections to be mutable
 * - Hibernate replaces collections with its own mutable implementation (PersistentSet) for lazy loading
 * - Kotlin's immutable collection types (Set, List) are replaced at runtime with mutable implementations
 * - This creates a semantic mismatch: declared type is immutable, but runtime type is mutable
 */
@Entity
@Table(name = "company_immutable_problem_test")
class CompanyImmutableProblemTest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    var name: String? = null

    @OneToMany(mappedBy = "company", fetch = FetchType.LAZY)
    var employees: Set<EmployeeProblemTest>? = null  // PROBLEM: Immutable Set
}

@Entity
@Table(name = "employee_problem_test")
class EmployeeProblemTest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    var name: String? = null

    @ManyToOne
    @JoinColumn(name = "company_id")
    var company: CompanyImmutableProblemTest? = null
}

/**
 * SOLUTION: Use MutableSet for collections
 */
@Entity
@Table(name = "company_immutable_solution_test")
class CompanyImmutableSolutionTest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    var name: String? = null

    @OneToMany(mappedBy = "company", fetch = FetchType.LAZY)
    var employees: MutableSet<EmployeeSolutionTest> = mutableSetOf()  // SOLUTION: MutableSet
}

@Entity
@Table(name = "employee_solution_test")
class EmployeeSolutionTest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    var name: String? = null

    @ManyToOne
    @JoinColumn(name = "company_id")
    var company: CompanyImmutableSolutionTest? = null
}

@Entity
@Table(name = "company_with_truly_immutable_test")
class CompanyWithTrulyImmutableTest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    var name: String? = null

    @OneToMany(mappedBy = "company", fetch = FetchType.LAZY)
    val employees: Set<EmployeeImmutableTest> = emptySet() // final + initialized
}

@Entity
@Table(name = "employee_immutable_test")
class EmployeeImmutableTest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    var name: String? = null

    @ManyToOne
    @JoinColumn(name = "company_id")
    var company: CompanyWithTrulyImmutableTest? = null
}

// ───────────────────────────────────────────────────────────────────────────────
// Tests
// ───────────────────────────────────────────────────────────────────────────────

@DataJpaTest
class CollectionsProblemTest(
    @Autowired private val entityManager: EntityManager,
) {

    // 1) Immutable Set type causes confusion with lazy loading
    @Test
    @Rollback
    fun immutableCollectionProblem_typeConfusion() {
        val company = CompanyImmutableProblemTest().apply { name = "Tech Corp" }
        entityManager.persist(company)

        entityManager.persist(EmployeeProblemTest().apply {
            name = "Alice"
            this.company = company
        })
        entityManager.persist(EmployeeProblemTest().apply {
            name = "Bob"
            this.company = company
        })
        entityManager.flush()
        entityManager.clear()

        val loadedCompany = entityManager.find(CompanyImmutableProblemTest::class.java, company.id)
        val employees = loadedCompany.employees

        assertTrue(
            employees is PersistentCollection<*>,
            "Hibernate injects PersistentSet even though field is declared as immutable Set"
        )

        assertEquals(2, employees?.size)
    }

    // 2) MutableSet enables lazy loading
    @Test
    @Rollback
    fun mutableCollectionSolution_lazyLoadingWorks() {
        val company = CompanyImmutableSolutionTest().apply { name = "Tech Corp" }
        entityManager.persist(company)

        entityManager.persist(EmployeeSolutionTest().apply {
            name = "Charlie"
            this.company = company
        })
        entityManager.persist(EmployeeSolutionTest().apply {
            name = "Dave"
            this.company = company
        })
        entityManager.flush()
        entityManager.clear()

        val loadedCompany = entityManager.find(CompanyImmutableSolutionTest::class.java, company.id)
        val employees = loadedCompany.employees

        assertTrue(
            employees is PersistentCollection<*>,
            "MutableSet IS replaced by Hibernate's PersistentCollection proxy"
        )

        assertFalse(
            Hibernate.isInitialized(employees),
            "Collection should be UNINITIALIZED before first access (lazy loading)"
        )

        val size = employees.size

        assertTrue(
            Hibernate.isInitialized(employees),
            "Collection should be INITIALIZED after accessing size()"
        )
        assertEquals(2, size)
    }

    // 3) Read-only type is proxied and lazy loads
    @Test
    @Rollback
    fun readOnlyType_isProxied_andLazyLoads() {
        val c = CompanyWithTrulyImmutableTest().apply { name = "ACME" }
        entityManager.persist(c)
        entityManager.persist(EmployeeImmutableTest().apply { name = "A"; company = c })
        entityManager.persist(EmployeeImmutableTest().apply { name = "B"; company = c })
        entityManager.flush()
        entityManager.clear()

        val loaded = entityManager.find(CompanyWithTrulyImmutableTest::class.java, c.id)
        val employees = loaded.employees

        // It's a Hibernate proxy even though the Kotlin type is read-only
        assertTrue(employees is PersistentCollection<*>)

        // Before touching it, it should be uninitialized
        assertFalse(Hibernate.isInitialized(employees))

        val count = employees.size // triggers lazy loading

        // After access, it should be initialized and reflect DB rows
        assertTrue(Hibernate.isInitialized(employees))
        assertEquals(2, count)
    }

    // 4) Immutable Set declaration becomes mutable at runtime
    @Test
    @Rollback
    fun immutableCollectionProblem_becomesMutableAtRuntime() {
        // Setup: Create company with 2 employees
        val company = CompanyImmutableProblemTest().apply { name = "Tech Corp" }
        entityManager.persist(company)

        entityManager.persist(EmployeeProblemTest().apply {
            name = "Alice"
            this.company = company
        })
        entityManager.persist(EmployeeProblemTest().apply {
            name = "Bob"
            this.company = company
        })
        entityManager.flush()
        entityManager.clear()

        // Load company from database - Hibernate replaces Set with PersistentSet
        val loadedCompany = entityManager.find(CompanyImmutableProblemTest::class.java, company.id)
        val employees = loadedCompany.employees

        // Verify it's a Hibernate proxy
        assertTrue(
            employees is PersistentCollection<*>,
            "Hibernate replaces immutable Set with PersistentSet"
        )

        // Initialize the collection by accessing it
        assertEquals(2, employees?.size, "Should have 2 employees initially")

        // THE KEY TEST: Can we mutate the "immutable" Set?
        // Cast to MutableSet (this works because runtime type is PersistentSet)
        val mutableEmployees = employees as? MutableSet<EmployeeProblemTest>
        assertNotNull(mutableEmployees, "Should be able to cast to MutableSet despite immutable declaration")

        // Create and add a new employee
        val newEmployee = EmployeeProblemTest().apply {
            name = "Charlie"
            this.company = loadedCompany
        }
        entityManager.persist(newEmployee)

        // Add to the "immutable" collection - THIS WORKS!
        val addResult = mutableEmployees!!.add(newEmployee)
        assertTrue(addResult, "Should be able to add to the collection")

        // Verify the collection now has 3 employees
        assertEquals(3, loadedCompany.employees?.size, "Collection should now have 3 employees")

        // Flush and verify persistence
        entityManager.flush()
        entityManager.clear()

        // Reload and verify the change persisted
        val reloadedCompany = entityManager.find(CompanyImmutableProblemTest::class.java, company.id)
        assertEquals(3, reloadedCompany.employees?.size, "Change should persist to database")
        assertTrue(
            reloadedCompany.employees?.any { it.name == "Charlie" } == true,
            "New employee should be in the collection"
        )
    }
}
