package com.jetbrains.demo.jpaproblemsreproducer.problems

import jakarta.persistence.*
import org.junit.jupiter.api.Test

/**
 * Intentional reproducer: mutual references in data-class primary constructors cause recursive hashCode().
 *
 * DOrderRecursive includes items (Set<DOrderItemRecursive})
 * DOrderItemRecursive includes order (DOrderRecursive)
 *
 * data class generated equals/hashCode include ALL primary-ctor properties → infinite recursion.
 */
@Entity
@Table(name = "orders_recursive")
data class DOrderRecursive(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var number: String = "",
    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    var items: MutableSet<DOrderItemRecursive> = mutableSetOf()
)

@Entity
@Table(name = "order_items_recursive")
data class DOrderItemRecursive(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var productName: String = "",
    @ManyToOne(fetch = FetchType.LAZY)
    var order: DOrderRecursive? = null
)

class StrangeConstructorProblemTest {

    @Test
    fun recursive_hashCode_from_data_classes_blows_stack() {
        val order = DOrderRecursive(number = "ORD-RECURSIVE")
        val i1 = DOrderItemRecursive(productName = "Apple", order = order)
        val i2 = DOrderItemRecursive(productName = "Apple", order = order)

        // Adding to the set may already evaluate hashCode() and blow the stack,
        // but to be explicit we trigger it ourselves after inserts.
        order.items += i1
        order.items += i2

        // StackOverflowError. Explicitly trigger hashing:
        order.hashCode()
    }
}