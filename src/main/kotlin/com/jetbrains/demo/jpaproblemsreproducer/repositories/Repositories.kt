package com.jetbrains.demo.jpaproblemsreproducer.repositories

import com.jetbrains.demo.jpaproblemsreproducer.entities.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

// Problem 1: Val vs Var
@Repository
interface PersonValProblemRepository : JpaRepository<PersonValProblem, Long>

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
interface PersonDataClassProblemRepository : JpaRepository<PersonDataClassProblem, Long>

@Repository
interface PersonSolutionRepository : JpaRepository<PersonSolution, Long>
