package com.jetbrains.demo.jpaproblemsreproducer.repositories

import com.jetbrains.demo.jpaproblemsreproducer.entities.*
import org.springframework.stereotype.Repository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

// Problem 1: Val vs Var
@Repository

interface PersonValProblemRepository : JpaRepository<SystemUser, Long> {

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SystemUser p set p.kind = :kind where p.id = :id")
    fun updateKind(
        @Param("id") id: Long,
        @Param("kind") kind: UserAcessRights
    ): Int
}

@Repository
interface PersonValSolutionRepository : JpaRepository<PersonValSolution, Long>

// Problem 2: Immutable Collections
@Repository
interface CompanyImmutableProblemRepository : JpaRepository<CompanyImmutableProblem, Long>

@Repository
interface EmployeeProblemRepository : JpaRepository<EmployeeProblem, Long>

@Repository
interface CompanyImmutableSolutionRepository : JpaRepository<CompanyImmutableSolution, Long>

@Repository
interface EmployeeSolutionRepository : JpaRepository<EmployeeSolution, Long>

@Repository
interface CompanyWithTrulyImmutableRepository : JpaRepository<CompanyWithTrulyImmutable, Long>

@Repository
interface EmployeeImmutableRepository : JpaRepository<EmployeeImmutable, Long>

// Problem 3: Data Class
@Repository
interface PersonDataClassProblemRepository : JpaRepository<Employee, Long>

@Repository
interface PersonSolutionRepository : JpaRepository<PersonSolution, Long>
