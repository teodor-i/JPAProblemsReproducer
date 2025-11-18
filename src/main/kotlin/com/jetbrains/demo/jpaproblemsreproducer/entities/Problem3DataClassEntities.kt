package com.jetbrains.demo.jpaproblemsreproducer.entities

import jakarta.persistence.*

// ============================================================================
// PROBLEM 3: Data Class as Entity
// ============================================================================

/**
 * PROBLEM: Data Class as Entity
 * KTIJ-34603
 */
@Entity
@Table(name = "person_data_class_problem")
data class Employee(
    @Id
    @GeneratedValue
    var id: Long? = null,

    var name: String? = null,
    var email: String? = null,
    var department: String? = null
)
// Data class auto-generates:
// - equals() that compares id, name, email
// - hashCode() that uses id, name, email
// - toString() that prints all fields
// All of these are WRONG for JPA entities!

/**
 * SOLUTION: Regular open class (NOT data class)
 *
 * User Story:
 * This demonstrates the correct way to implement a JPA entity in Kotlin. By using a regular
 * open class instead of a data class, we avoid all the problems associated with data classes:
 * - Entities can be added to Sets and modified without breaking collection contracts
 * - JPA can create proxy subclasses for lazy loading
 * - toString() only accesses eagerly fetched fields, avoiding lazy initialization issues
 * - The entity follows JPA's mutable entity model
 *
 * Correct implementation:
 * - Open class (not final) -> allows JPA to generate proxy subclasses for lazy loading
 * - Custom equals() that only compares type and ID -> stable across entity modifications
 * - Custom hashCode() that returns constant -> doesn't change when entity is modified
 * - toString() only accesses eagerly fetched attributes -> avoids lazy initialization issues
 * - All fields are var and nullable -> follows JPA requirements and Kotlin null-safety best practices
 *
 * Why this works:
 * - equals() based on ID only: entities remain findable in Sets/Maps after modification
 * - Constant hashCode(): entities don't "move" in hash-based collections when modified
 * - Open class: JPA can create proxy subclasses for lazy loading relationships
 * - Nullable fields: honest about what JPA can guarantee (reflection can set null values)
 */
@Entity
@Table(name = "person_solution")
class PersonSolution {

    @Id
    @GeneratedValue
    var id: Long? = null

    var name: String? = null
    var email: String? = null

    // Correct equals: only compare type and ID
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PersonSolution) return false
        return id != null && id == other.id
    }

    // Correct hashCode: return constant value
    override fun hashCode(): Int = javaClass.hashCode()

    // Correct toString: only eagerly fetched attributes
    override fun toString(): String = "PersonSolution(id=$id)"
}
