package com.jetbrains.demo.jpaproblemsreproducer.entities

import jakarta.persistence.*

// ============================================================================
// PROBLEM 1: Val vs Var - Mutability Problem
// ============================================================================

/**
 * PROBLEM: Using 'val' for entity fields
 * KTIJ-33476
 */
@Entity
@Table(name = "person_val_problem")
class SystemUser(
    @Id @GeneratedValue
    var id: Long? = null,

    val name: String = "John",

    @Enumerated(EnumType.STRING)
    val kind: UserAcessRights = UserAcessRights.CUSTOMER // PROBLEM: Hibernate can set this via reflection on load
)

enum class UserAcessRights { CUSTOMER, ADMIN }


/**
 * SOLUTION: Use 'var' for all entity fields
 */
@Entity
@Table(name = "person_val_solution")
class PersonValSolution(
    @Id @GeneratedValue var id: Long? = null,
    var name: String? = null
)


