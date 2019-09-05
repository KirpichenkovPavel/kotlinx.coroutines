/*
 * Copyright 2016-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("unused")

package kotlinx.coroutines.linearizability

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.jetbrains.kotlinx.lincheck.LinChecker
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.annotations.Param
import org.jetbrains.kotlinx.lincheck.paramgen.IntGen
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressOptions
import org.jetbrains.kotlinx.lincheck.verifier.VerifierState
import org.junit.*
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

// TODO: this test fails with KotlinNullPointerException because of missing continuation context
class ChannelsLCStressTestImpl: VerifierState() {
    companion object {
        var capacity = Integer.MIN_VALUE
    }

    private val c = Channel<Int>(capacity)

    @Operation
    suspend fun send(@Param(gen = IntGen::class) value: Int) = try {
        c.send(value)
    } catch (e : NumberedCancellationException) {
        e.testResult
    }

    @Operation
    fun offer(@Param(gen = IntGen::class) value: Int) = try {
        c.offer(value)
    } catch (e : NumberedCancellationException) {
        e.testResult
    }

    @Operation
    suspend fun receive() = try {
        c.receive()
    } catch (e : NumberedCancellationException) {
        e.testResult
    }

    @Operation
    fun poll() = try {
        c.poll()
    } catch (e : NumberedCancellationException) {
        e.testResult
    }

//    TODO: this operation should be (and can be!) linearizable, but is not
    @Operation
    fun close(@Param(gen = IntGen::class) token: Int) = c.close(NumberedCancellationException(token))

//    TODO: this operation should be (and can be!) linearizable, but is not
//    @Operation
    fun cancel(@Param(gen = IntGen::class) token: Int) = c.cancel(NumberedCancellationException(token))

    @Operation
    fun isClosedForReceive() = c.isClosedForReceive

    @Operation
    fun isClosedForSend() = c.isClosedForSend

    override fun extractState(): Any {
        val state = mutableListOf<Any>()
        while (true) {
            val x = poll() ?: break // no elements
            state.add(x)
            if (x is String) break // closed/cancelled
        }
        return state
    }
}

private class NumberedCancellationException(number: Int): CancellationException() {
    val testResult = "Closed($number)"
}

@RunWith(Parameterized::class)
class ChannelsLCStressTest(val capacity: Int): TestBase() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun parameters() = listOf(Channel.RENDEZVOUS, 1, 2, 4, Channel.UNLIMITED, Channel.CONFLATED).map { arrayOf(it) }
    }

    @Test
    fun test() {
        ChannelsLCStressTestImpl.capacity = this.capacity
        val options = StressOptions()
                .iterations(100 * stressTestMultiplierSqrt)
                .invocationsPerIteration(100000 * stressTestMultiplierSqrt)
                .threads(3)
                .actorsBefore(0)
        LinChecker.check(ChannelsLCStressTestImpl::class.java, options)
    }
}