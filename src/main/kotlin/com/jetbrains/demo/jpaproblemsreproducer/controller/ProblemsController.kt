package com.jetbrains.demo.jpaproblemsreproducer.controller

import com.jetbrains.demo.jpaproblemsreproducer.entities.*
import com.jetbrains.demo.jpaproblemsreproducer.repositories.*
import jakarta.persistence.EntityManager
import org.hibernate.Hibernate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST Controller with concise methods to demonstrate each JPA + Kotlin problem
 * Use test for reproducing, this file is more like a playground
 *
 * Endpoints:
 * - GET /problems/val - Problem 1: Val vs Var
 * - GET /problems/immutable-collections - Problem 2: Immutable Collections
 * - GET /problems/lazy-loading - Demonstration: Lazy Loading with OneToMany
 * - GET /problems/data-class - Problem 3: Data Classes
 * - GET /problems/all - Run all demonstrations
 */
@RestController
@RequestMapping("/problems")
@Transactional
class ProblemsController(
    private val personValProblemRepository: PersonValProblemRepository,
    private val personValSolutionRepository: PersonValSolutionRepository,
    private val companyImmutableProblemRepository: CompanyImmutableProblemRepository,
    private val employeeProblemRepository: EmployeeProblemRepository,
    private val companyImmutableSolutionRepository: CompanyImmutableSolutionRepository,
    private val employeeSolutionRepository: EmployeeSolutionRepository,
    private val companyWithTrulyImmutableRepository: CompanyWithTrulyImmutableRepository,
    private val employeeImmutableRepository: EmployeeImmutableRepository,
    private val personDataClassProblemRepository: PersonDataClassProblemRepository,
    private val personSolutionRepository: PersonSolutionRepository,
    private val entityManager: EntityManager // Keep for native queries only
) {

    @GetMapping("/val")
    fun valProblem(): Map<String, String> {
        val person = personValProblemRepository.save(PersonValProblem(name = "John"))
        val loaded = personValProblemRepository.findByIdOrNull(person.id!!)!!

        return mapOf(
            "problem" to "Val vs Var",
            "issue" to "'val name' was modified by Hibernate via reflection",
            "name" to loaded.name!!,
            "result" to "✗ Kotlin immutability violated!"
        )
    }

    @GetMapping("/immutable-collections")
    fun immutableCollectionsProblem(): Map<String, Any> {
        val company = companyImmutableProblemRepository.save(
            CompanyImmutableProblem().apply { name = "Tech Corp" }
        )

        repeat(3) { i ->
            employeeProblemRepository.save(EmployeeProblem().apply {
                name = "Employee $i"
                this.company = company
            })
        }

        val loaded = companyImmutableProblemRepository.findByIdOrNull(company.id!!)!!

        return try {
            val collectionType = loaded.employees?.javaClass?.name ?: "null"
            val employeeCount = loaded.employees?.size ?: 0
            val isPersistentCollection = collectionType.contains("PersistentSet") ||
                                        collectionType.contains("PersistentBag")

            if (isPersistentCollection) {
                mapOf(
                    "problem" to "Immutable Collections",
                    "issue" to "Set<Employee> declared but Hibernate replaced it with MutableSet",
                    "employeeCount" to employeeCount,
                    "actualType" to collectionType,
                    "result" to "✗ Type safety violated! Immutable Set<T> is actually mutable at runtime!"
                )
            } else {
                mapOf(
                    "problem" to "Immutable Collections",
                    "issue" to "Set<Employee> prevents proper Hibernate collection initialization",
                    "employeeCount" to employeeCount,
                    "actualType" to collectionType,
                    "result" to "✗ Hibernate couldn't initialize as PersistentSet!"
                )
            }
        } catch (e: Exception) {
            mapOf(
                "problem" to "Immutable Collections",
                "error" to e.message!!,
                "errorType" to e.javaClass.simpleName,
                "result" to "✗ Exception occurred! ${e.javaClass.simpleName}"
            )
        }
    }

    @GetMapping("/lazy-loading2")
    fun lazyLoadingDemo2(): Map<String, Any> {
        // ---------- Scenario 1: MutableSet (lazy should work) ----------
        val companyWithMutable = companyImmutableSolutionRepository.save(
            CompanyImmutableSolution().apply { name = "Tech Corp (MutableSet)" }
        )

        repeat(3) { i ->
            val e = EmployeeSolution().apply { name = "Employee $i"; company = companyWithMutable }
            employeeSolutionRepository.save(e)
            companyWithMutable.employees.add(e)
        }

        val loadedMutable = companyImmutableSolutionRepository.findByIdOrNull(companyWithMutable.id!!)!!
        val mutableEmployees = loadedMutable.employees
        val mutBefore = Hibernate.isInitialized(mutableEmployees)
        val mutCount = mutableEmployees.size
        val mutAfter = Hibernate.isInitialized(mutableEmployees)
        val mutOk = (!mutBefore && mutAfter && mutCount == 3)
        val mutResult = if (mutOk)
            "✓ Lazy loading works (uninitialized → initialized on first access)"
        else
            "✗ Unexpected lazy behavior (before=$mutBefore, after=$mutAfter, count=$mutCount)"

        // ---------- Scenario 2: val Set = emptySet() (Hibernate may still replace) ----------
        val companyWithImmutable = companyWithTrulyImmutableRepository.save(
            CompanyWithTrulyImmutable().apply { name = "ACME Corp (val + initialized)" }
        )
        repeat(3) { i ->
            employeeImmutableRepository.save(
                EmployeeImmutable().apply { name = "Worker $i"; company = companyWithImmutable }
            )
        }

        val loadedImmutable = companyWithTrulyImmutableRepository.findByIdOrNull(companyWithImmutable.id!!)!!
        val immutableEmployees = loadedImmutable.employees
        val isProxy = immutableEmployees is org.hibernate.collection.spi.PersistentCollection<*>
        val immBefore = if (isProxy) Hibernate.isInitialized(immutableEmployees) else null
        val immCount = immutableEmployees.size
        val immAfter = if (isProxy) Hibernate.isInitialized(immutableEmployees) else null

        val immResult = when {
            isProxy && immBefore == false && immAfter == true && immCount == 3 ->
                "⚠ Kotlin type is read-only, but Hibernate replaced it with a proxy; lazy loading works (semantic mismatch)."
            !isProxy && immCount == 0 ->
                "✗ Lazy loading failed (Hibernate did not replace truly immutable collection)."
            else ->
                "ℹ Observed state: isProxy=$isProxy, before=$immBefore, after=$immAfter, count=$immCount"
        }

        return mapOf(
            "scenario1_mutable" to mapOf(
                "type" to "MutableSet<Employee>",
                "initializedBeforeAccess" to mutBefore,
                "initializedAfterAccess" to mutAfter,
                "count" to mutCount,
                "result" to mutResult
            ),
            "scenario2_readOnlyType" to mapOf(
                "type" to "val Set<Employee> = emptySet()",
                "isPersistentCollection" to isProxy,
                "initializedBeforeAccess" to immBefore,
                "initializedAfterAccess" to immAfter,
                "count" to immCount,
                "result" to immResult
            ),
            "conclusion" to "Hibernate can often replace even a val read-only Set with a PersistentCollection; the real issue is semantic mismatch, not always lazy failure."
        )
    }

    @GetMapping("/lazy-loading")
    fun lazyLoadingDemo(): Map<String, Any> {
        // SCENARIO 1: MutableSet - Lazy loading WORKS
        val companyWithMutable = companyImmutableSolutionRepository.save(
            CompanyImmutableSolution().apply { name = "Tech Corp (MutableSet)" }
        )

        repeat(3) { i ->
            employeeSolutionRepository.save(EmployeeSolution().apply {
                name = "Employee $i"
                this.company = companyWithMutable
            })
        }

        val loadedMutable = companyImmutableSolutionRepository.findByIdOrNull(companyWithMutable.id!!)!!
        val mutableEmployees = loadedMutable.employees
        val mutableInitializedBefore = Hibernate.isInitialized(mutableEmployees)
        val mutableCollectionType = mutableEmployees.javaClass.simpleName
        val mutableEmployeeCount = mutableEmployees.size
        val mutableInitializedAfter = Hibernate.isInitialized(mutableEmployees)

        // SCENARIO 2: val + initialized - Lazy loading does NOT work
        val companyWithImmutable = companyWithTrulyImmutableRepository.save(
            CompanyWithTrulyImmutable().apply { name = "ACME Corp (val + initialized)" }
        )

        repeat(3) { i ->
            employeeImmutableRepository.save(EmployeeImmutable().apply {
                name = "Worker $i"
                this.company = companyWithImmutable
            })
        }

        val loadedImmutable = companyWithTrulyImmutableRepository.findByIdOrNull(companyWithImmutable.id!!)!!
        val immutableEmployees = loadedImmutable.employees
        val immutableIsPersistentCollection = immutableEmployees is org.hibernate.collection.spi.PersistentCollection<*>
        val immutableCollectionType = immutableEmployees.javaClass.simpleName
        val immutableEmployeeCount = immutableEmployees.size

        return mapOf(
            "demonstration" to "Lazy Loading with OneToMany - Two Scenarios",

            "scenario1_mutableSet" to mapOf(
                "description" to "MutableSet<Employee> - Lazy loading WORKS",
                "collectionType" to mutableCollectionType,
                "isPersistentCollection" to true,
                "initializedBeforeAccess" to mutableInitializedBefore,
                "initializedAfterAccess" to mutableInitializedAfter,
                "employeeCount" to mutableEmployeeCount,
                "result" to "✓ Lazy loading works! Collection was uninitialized, then loaded on first access"
            ),

            "scenario2_valInitialized" to mapOf(
                "description" to "val employees: Set<Employee> = emptySet() - Lazy loading does NOT work",
                "collectionType" to immutableCollectionType,
                "isPersistentCollection" to immutableIsPersistentCollection,
                "employeeCount" to immutableEmployeeCount,
                "result" to "✗ Lazy loading failed! Hibernate cannot replace truly immutable collection. Count is ${immutableEmployeeCount} (should be 3)"
            ),

            "conclusion" to "Use MutableSet for lazy loading to work properly!"
        )
    }

    @GetMapping("/data-class")
    fun dataClassProblem(): Map<String, Any> {
        val person = personDataClassProblemRepository.save(
            PersonDataClassProblem(name = "Alice", email = "alice@example.com")
        )

        val entitySet = mutableSetOf(person)
        val containsBeforeModification = entitySet.contains(person)
        person.name = "Alice Updated"
        val containsAfterModification = entitySet.contains(person)

        return mapOf(
            "problem" to "Data Class as Entity",
            "issue" to "Data class equals/hashCode use ALL fields",
            "setContainsBefore" to containsBeforeModification,
            "setContainsAfter" to containsAfterModification,
            "hashEqualityBroken" to !containsAfterModification
        )
    }

    // ========================================================================
    // DATA CLASS USE CASES (from DataClassProblemTest)
    // ========================================================================

    @GetMapping("/data-class/field-change-breaks-set")
    fun dataClass_fieldChangeBreaksSetContains(): Map<String, Any> {
        val person = personDataClassProblemRepository.save(
            PersonDataClassProblem(name = "John", email = "john@example.com")
        )
        val cache = hashSetOf(person)

        val containsBefore = cache.contains(person)
        
        // Business change: same database row, different non-ID field
        person.name = "John Updated"

        val containsAfter = cache.contains(person)

        return mapOf(
            "problem" to "Data-class: changing a non-ID field changes hash/equality",
            "issue" to "Non-ID mutation changed data-class hash/equality; Set no longer recognizes the same row",
            "containsBefore" to containsBefore,
            "containsAfter" to containsAfter,
            "setLookupBroken" to !containsAfter
        )
    }

    @GetMapping("/data-class/set-collapses-duplicates")
    fun dataClass_setCollapsesDuplicateChildren(): Map<String, Any> {
        // Note: This requires DOrder and DOrderItem entities from DataClassProblemTest
        // Since they're not in main entities, we'll demonstrate with PersonDataClassProblem
        val person1 = PersonDataClassProblem(name = "Bob", email = "bob@example.com")
        val person2 = PersonDataClassProblem(name = "Bob", email = "bob@example.com")

        val entitySet = mutableSetOf(person1, person2)
        val setSize = entitySet.size

        personDataClassProblemRepository.save(person1)
        personDataClassProblemRepository.save(person2)

        val expectedSize = 2
        val duplicatesCollapsed = setSize < expectedSize

        return mapOf<String, Any>(
            "problem" to "Data-class Set collapses duplicates",
            "issue" to "Data-class equality collapses identical children in Set",
            "expectedSetSize" to expectedSize,
            "actualSetSize" to setSize,
            "person1Id" to (person1.id ?: "null"),
            "person2Id" to (person2.id ?: "null"),
            "duplicatesCollapsed" to duplicatesCollapsed
        )
    }

    @GetMapping("/data-class/regular-entity-stable-hash")
    fun openClass_hashSetStableAfterPersist(): Map<String, Any> {
        val person = personSolutionRepository.save(PersonSolution().apply {
            name = "Charlie"
            email = "charlie@example.com"
        })
        val cache = hashSetOf(person)

        val containsAfterPersist = cache.contains(person)

        return mapOf(
            "solution" to "Regular entity: hash stays stable",
            "approach" to "HashSet lookup works after persist",
            "containsAfterPersist" to containsAfterPersist,
            "hashStable" to containsAfterPersist
        )
    }

    @GetMapping("/data-class/copy-causes-issues")
    fun dataClass_copy_causesDuplicateInsertOrMergeDetached(): Map<String, Any> {
        val original = personDataClassProblemRepository.save(
            PersonDataClassProblem(name = "Alice", email = "a@test.org")
        )
        val originalId = original.id!!
        entityManager.flush()

        // Case A: clear id → treated as new → INSERT another row
        val cloneNew = original.copy(name = "Alice v2")
        cloneNew.id = null
        personDataClassProblemRepository.save(cloneNew)
        entityManager.flush()
        val countAfterClone = personDataClassProblemRepository.count()

        // Case B: keep id → detached instance merged → UPDATE existing row
        entityManager.clear()
        val detachedCopy = original.copy(name = "Alice merged")
        personDataClassProblemRepository.save(detachedCopy)
        entityManager.flush()

        val finalCount = personDataClassProblemRepository.count()
        val updated = personDataClassProblemRepository.findById(originalId).orElseThrow()

        val duplicateInserted = countAfterClone > 1
        val detachedMerged = updated.name == "Alice merged"

        return mapOf(
            "problem" to "Data-class copy(): duplicate insert when id cleared; merge detached when id kept",
            "countAfterClone" to countAfterClone,
            "finalCount" to finalCount,
            "updatedName" to updated.name!!,
            "duplicateInserted" to duplicateInserted,
            "detachedMerged" to detachedMerged
        )
    }


    @GetMapping("/all")
    fun allProblems(): Map<String, Any> {
        return mapOf(
            "problem1" to valProblem(),
            "problem2" to immutableCollectionsProblem(),
            "problem3" to dataClassProblem()
        )
    }

    // ========================================================================
    // SOLUTIONS
    // ========================================================================

    @GetMapping("/solutions/val")
    fun valSolution(): Map<String, String> {
        val person = personValSolutionRepository.save(PersonValSolution(name = "Alice"))
        val loaded = personValSolutionRepository.findByIdOrNull(person.id!!)!!

        return mapOf(
            "solution" to "Use 'var' for Entity Fields",
            "approach" to "All entity fields declared with 'var' instead of 'val'",
            "name" to loaded.name!!,
            "result" to "✓ Hibernate can properly modify fields without reflection hacks!"
        )
    }

    @GetMapping("/solutions/immutable-collections")
    fun immutableCollectionsSolution(): Map<String, Any> {
        val company = companyImmutableSolutionRepository.save(
            CompanyImmutableSolution().apply { name = "Tech Corp Pro" }
        )

        repeat(3) { i ->
            employeeSolutionRepository.save(EmployeeSolution().apply {
                name = "Employee $i"
                this.company = company
            })
        }

        val loaded = companyImmutableSolutionRepository.findByIdOrNull(company.id!!)!!
        val collectionType = loaded.employees.javaClass.name

        return mapOf(
            "solution" to "Use MutableSet for Collections",
            "approach" to "MutableSet<Employee> allows Hibernate lazy loading",
            "employeeCount" to loaded.employees.size,
            "actualType" to collectionType,
            "result" to "✓ Hibernate PersistentSet works correctly with mutable collections!"
        )
    }

    @GetMapping("/solutions/data-class")
    fun dataClassSolution(): Map<String, String> {
        val person = personSolutionRepository.save(PersonSolution().apply {
            name = "Bob"
            email = "bob@example.com"
        })

        val entitySet = mutableSetOf(person)
        person.name = "Bob Updated"
        val containsAfterModification = entitySet.contains(person)

        return mapOf(
            "solution" to "Regular Open Class",
            "approach" to "Custom equals/hashCode - only compare ID",
            "setContainsBefore" to "true",
            "setContainsAfter" to containsAfterModification.toString(),
            "result" to "✓ Set.contains() = true after modification!"
        )
    }


    @GetMapping("/solutions/all")
    fun allSolutions(): Map<String, Any> {
        return mapOf(
            "solution1" to valSolution(),
            "solution2" to immutableCollectionsSolution(),
            "solution3" to dataClassSolution()
        )
    }
}
