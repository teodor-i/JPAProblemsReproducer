package com.jetbrains.demo.jpaproblemsreproducer.problems

import jakarta.persistence.*
import org.hibernate.LazyInitializationException
import org.hibernate.proxy.HibernateProxy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.test.annotation.Rollback

// ───────────────────────────────────────────────────────────────────────────────
// Repositories
// ───────────────────────────────────────────────────────────────────────────────

interface UserDataRepo : JpaRepository<UserDataEntity, Long>
interface UserRepo : JpaRepository<UserEntity, Long>

interface DUserRepo : JpaRepository<DUser, Long>
interface DGadgetRepo : JpaRepository<DGadget, Long>

interface SUserRepo : JpaRepository<SUser, Long>
interface SGadgetRepo : JpaRepository<SGadget, Long>

interface DOrderRepo : JpaRepository<DOrder, Long>
interface DOrderItemRepo : JpaRepository<DOrderItem, Long>

interface EmployeeRepo : JpaRepository<Employee, Long>

// ───────────────────────────────────────────────────────────────────────────────
// Data classes used for equals/hash and lazy issues
// ───────────────────────────────────────────────────────────────────────────────
// KTIJ-34603

@Entity
@Table(name = "users_data")
data class UserDataEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val name: String = "",
    val email: String = ""
)

@Entity
@Table(name = "employees_dc")
data class Employee(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var name: String = "",
    var department: String = ""
)

// ───────────────────────────────────────────────────────────────────────────────
// Regular entity with JPA-friendly equals/hashCode (proxy-safe)
// ───────────────────────────────────────────────────────────────────────────────

@Entity
@Table(name = "users_ok")
class UserEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var name: String = "",
    var email: String = ""
) {
    final override fun equals(other: Any?): Boolean { // Kind of idiomatic way
        if (this === other) return true
        if (other == null) return false
        val thisClass = effectiveClass(this)
        val otherClass = effectiveClass(other)
        if (thisClass != otherClass) return false
        other as UserEntity
        return this.id != null && this.id == other.id
    }

    final override fun hashCode(): Int = effectiveClass(this).hashCode()

    private fun effectiveClass(x: Any): Class<*> =
        if (x is HibernateProxy) x.hibernateLazyInitializer.persistentClass else x.javaClass
}

// ───────────────────────────────────────────────────────────────────────────────
// Data-class with LAZY collection → toString() includes it; fails when detached
// ───────────────────────────────────────────────────────────────────────────────

@Entity
@Table(name = "d_user")
data class DUser(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val email: String = "",
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    val gadgets: List<DGadget> = emptyList()
)

@Entity
@Table(name = "d_gadget")
data class DGadget(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val name: String = "",
    @ManyToOne(fetch = FetchType.LAZY)
    val user: DUser? = null
)

// ───────────────────────────────────────────────────────────────────────────────
// Safe regular class: custom toString() avoids lazy fields
// ───────────────────────────────────────────────────────────────────────────────

@Entity
@Table(name = "s_user")
class SUser(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var email: String = "",
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    var gadgets: List<SGadget> = emptyList()
) {
    final override fun toString(): String = "SUser(id=$id,email=$email)"
}

@Entity
@Table(name = "s_gadget")
class SGadget(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var name: String = "",
    @ManyToOne(fetch = FetchType.LAZY)
    var user: SUser? = null
)

// ───────────────────────────────────────────────────────────────────────────────
// Data-class Order/OrderItem demonstrating Set duplicate suppression
// ───────────────────────────────────────────────────────────────────────────────

@Entity
@Table(name = "orders_dc")
data class DOrder(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var number: String = ""
) {
    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    var items: MutableSet<DOrderItem> = mutableSetOf()
}

@Entity
@Table(name = "order_items_dc")
data class DOrderItem(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var productName: String = ""
) {
    @ManyToOne(fetch = FetchType.LAZY)
    var order: DOrder? = null
}

// ───────────────────────────────────────────────────────────────────────────────
// Tests
// ───────────────────────────────────────────────────────────────────────────────

@DataJpaTest
class DataClassProblemTest2(
    @Autowired private val userDataRepo: UserDataRepo,
    @Autowired private val userRepo: UserRepo,
    @Autowired private val entityManager: EntityManager,
    @Autowired private val dUserRepo: DUserRepo,
    @Autowired private val dGadgetRepo: DGadgetRepo,
    @Autowired private val sUserRepo: SUserRepo,
    @Autowired private val sGadgetRepo: SGadgetRepo,
    @Autowired private val dOrderRepo: DOrderRepo,
    @Autowired private val dOrderItemRepo: DOrderItemRepo,
    @Autowired private val employeeRepo: EmployeeRepo,
) {

    // 1) Data-class: changing a non-ID field changes hash/equality → Set contains() fails
    @Test
    @Rollback
    fun dataClass_fieldChangeBreaksSetContains() {
        val e = employeeRepo.save(Employee(name = "John", department = "Sales"))
        val cache = hashSetOf(e)

        // Business change: same database row, different non-ID field
        e.department = "Support"

        assertFalse(
            cache.contains(e),
            "Non-ID mutation changed data-class hash/equality; Set no longer recognizes the same row"
        )
    }

    // 2) Data-class Set collapses duplicates in @OneToMany → only one child is persisted
    @Test
    @Rollback
    fun dataClass_setCollapsesDuplicateChildren() {
        val order = DOrder(number = "ORD-001")
        order.items += DOrderItem(productName = "Apple").also { it.order = order }
        order.items += DOrderItem(productName = "Apple").also { it.order = order } // equal by fields ⇒ same set entry

        dOrderRepo.save(order)
        entityManager.flush()

        assertEquals(
            1,
            dOrderItemRepo.count(),
            "Data-class equality collapses identical children in Set; only one OrderItem persisted"
        )
    }

    // 3) Regular entity: hash stays stable → HashSet lookup works
    @Test
    @Rollback
    fun openClass_hashSetStableAfterPersist() {
        val u = UserEntity(name = "Bob", email = "bob@example.com")
        val cache = hashSetOf(u)
        userRepo.save(u)
        assertTrue(cache.contains(u), "Regular entity: hash bucket stable")
    }

    // 4) Data-class with LAZY collection: toString() after detach → LazyInitializationException
    @Test
    @Rollback
    fun dataClass_toStringTriggersLazyInitWhenDetached() {
        val u = dUserRepo.save(DUser(email = "u@test.org"))
        dGadgetRepo.save(DGadget(name = "g1", user = u))
        dGadgetRepo.save(DGadget(name = "g2", user = u))
        entityManager.flush()
        entityManager.clear()

        val loaded = dUserRepo.findById(u.id!!).orElseThrow()
        entityManager.clear() // detach

        assertThrows(LazyInitializationException::class.java) {
            loaded.toString() // data-class toString includes lazy collection
        }
    }

    // 5) Regular class with safe toString(): no lazy init, no exception
    @Test
    @Rollback
    fun regularClass_safeToStringDoesNotTriggerLazyInit() {
        val u = sUserRepo.save(SUser(email = "u@test.org"))
        sGadgetRepo.save(SGadget(name = "g1", user = u))
        sGadgetRepo.save(SGadget(name = "g2", user = u))
        entityManager.flush()
        entityManager.clear()

        val loaded = sUserRepo.findById(u.id!!).orElseThrow()
        entityManager.clear()

        assertDoesNotThrow { loaded.toString() } // safe toString
    }

    // 6) Data-class copy(): duplicate insert when id cleared; merge detached when id kept
    @Test
    @Rollback
    fun dataClass_copy_causesDuplicateInsertOrMergeDetached() {
        val original = userDataRepo.save(UserDataEntity(name = "Alice", email = "a@test.org"))
        val originalId = original.id!!
        entityManager.flush()

        // Case A: clear id → treated as new → INSERT another row
        val cloneNew = original.copy(id = null, name = "Alice v2")
        userDataRepo.save(cloneNew)
        entityManager.flush()
        assertEquals(2, userDataRepo.count(), "copy(id = null) should insert a new row")

        // Case B: keep id → detached instance merged → UPDATE existing row
        entityManager.clear()
        val detachedCopy = original.copy(name = "Alice merged")
        userDataRepo.save(detachedCopy)
        entityManager.flush()

        assertEquals(2, userDataRepo.count(), "Merging detached copy should not create another row")
        val updated = userDataRepo.findById(originalId).orElseThrow()
        assertEquals("Alice merged", updated.name, "Detached copy should update the existing row by id")
    }
}