/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("unused")

package kotlinx.coroutines.linearizability

import kotlinx.coroutines.*
import kotlinx.coroutines.internal.*
import org.jetbrains.kotlinx.lincheck.LinChecker
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.annotations.Param
import org.jetbrains.kotlinx.lincheck.paramgen.IntGen
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressOptions
import kotlin.test.*

@Param(name = "value", gen = IntGen::class)
class LockFreeListLCStressTest : TestBase() {
    class Node(val value: Int): LockFreeLinkedListNode()

    private val q: LockFreeLinkedListHead = LockFreeLinkedListHead()

    @Operation
    fun addLast(@Param(name = "value") value: Int) {
        q.addLast(Node(value))
    }

    @Operation
    fun addLastIfNotSame(@Param(name = "value") value: Int) {
        q.addLastIfPrev(Node(value)) { !it.isSame(value) }
    }

    @Operation
    fun removeFirst(): Int? {
        val node = q.removeFirstOrNull() ?: return null
        return (node as Node).value
    }

    @Operation
    fun removeFirstOrPeekIfNotSame(@Param(name = "value") value: Int): Int? {
        val node = q.removeFirstIfIsInstanceOfOrPeekIf<Node> { !it.isSame(value) } ?: return null
        return node.value
    }

    private fun Any.isSame(value: Int) = this is Node && this.value == value

    @Test
    fun testAddRemoveLinearizability() {
        val options = StressOptions()
            .iterations(100 * stressTestMultiplierSqrt)
            .invocationsPerIteration(1000 * stressTestMultiplierSqrt)
            .threads(3)
        LinChecker.check(LockFreeListLCStressTest::class.java, options)
    }

    private var _curElements: ArrayList<Int>? = null
    private val curElements: ArrayList<Int> get() {
        if (_curElements == null) {
            _curElements = ArrayList()
            q.forEach<Node> { _curElements!!.add(it.value) }
        }
        return _curElements!!
    }

    override fun equals(other: Any?): Boolean {
        other as LockFreeListLCStressTest
        return curElements == other.curElements
    }

    override fun hashCode(): Int {
        return curElements.hashCode()
    }
}