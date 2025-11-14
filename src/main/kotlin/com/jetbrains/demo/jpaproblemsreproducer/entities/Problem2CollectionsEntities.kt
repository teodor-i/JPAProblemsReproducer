package com.jetbrains.demo.jpaproblemsreproducer.entities

import jakarta.persistence.*

// ============================================================================
// PROBLEM 2: Immutable Kotlin Collection Types with @OneToMany/@ManyToMany
// ============================================================================

/**
 * PROBLEM: Using immutable collection types (Set, List) instead of mutable collections
 * KTIJ-34851 and KTIJ-33946
 */


/**
 * PROBLEM: Showing unhelpful text and no quick fix, when used var with immutable collection in Entity
 * Current text: 'One To Many' attribute value type should not be '? extends Book'
 * Shown Severity: Error
 * KTIJ-34851
 */
@Entity
@Table(name = "company_immutable_problem")
class CompanyImmutableProblem {
    @Id
    @GeneratedValue
    var id: Long? = null
    var name: String? = null

    @OneToMany(mappedBy = "company", fetch = FetchType.LAZY)
    var employees: Set<EmployeeProblem>? = null  // PROBLEM: Immutable Set
}

/**
 * PROBLEM: Broken quick fix. Appears, when used val with immutable collection in Entity
 * And possibly, immutable collection isn't a problem for Entity: looks for tests.
 * Shown Severity: Warning
 * KTIJ-33946
 */
@Entity
@Table(name = "company_immutable_problem")
class CompanyImmutableProblemVal {
    @Id
    @GeneratedValue
    var id: Long? = null
    var name: String? = null

    @OneToMany(mappedBy = "company", fetch = FetchType.LAZY)
    val employees: Set<EmployeeProblem>? = null  // PROBLEM: Immutable Set
}

@Entity
@Table(name = "employee_problem")
class EmployeeProblem {
    @Id
    @GeneratedValue
    var id: Long? = null
    var name: String? = null

    @ManyToOne
    @JoinColumn(name = "company_id")
    var company: CompanyImmutableProblem? = null
}

/**
 * SOLUTION: Use MutableSet for collections
 */
@Entity
@Table(name = "company_immutable_solution")
class CompanyImmutableSolution {
    @Id
    @GeneratedValue
    var id: Long? = null
    var name: String? = null

    @OneToMany(mappedBy = "company", fetch = FetchType.LAZY)
    var employees: MutableSet<EmployeeSolution> = mutableSetOf()  // SOLUTION: MutableSet
}

@Entity
@Table(name = "employee_solution")
class EmployeeSolution {
    @Id
    @GeneratedValue
    var id: Long? = null
    var name: String? = null

    @ManyToOne
    @JoinColumn(name = "company_id")
    var company: CompanyImmutableSolution? = null
}

@Entity
@Table(name = "company_with_truly_immutable")
class CompanyWithTrulyImmutable {
    @Id
    @GeneratedValue
    var id: Long? = null
    var name: String? = null

    @OneToMany(mappedBy = "company", fetch = FetchType.LAZY)
    val employees: Set<EmployeeImmutable> = emptySet() // final + initialized
}

@Entity
@Table(name = "employee_immutable")
class EmployeeImmutable {
    @Id
    @GeneratedValue
    var id: Long? = null
    var name: String? = null

    @ManyToOne
    @JoinColumn(name = "company_id")
    var company: CompanyWithTrulyImmutable? = null
}