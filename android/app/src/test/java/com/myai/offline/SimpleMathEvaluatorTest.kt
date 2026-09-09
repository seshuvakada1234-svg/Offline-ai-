package com.myai.offline

import com.myai.offline.utils.SimpleMathEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SimpleMathEvaluatorTest {

    @Test
    fun testBasicAddition() {
        val result = SimpleMathEvaluator.evaluate("5+7")
        assertNotNull(result)
        assertEquals(12.0, result!!, 0.0001)
    }

    @Test
    fun testBasicAdditionWithSpaces() {
        val result = SimpleMathEvaluator.evaluate("5 + 7")
        assertNotNull(result)
        assertEquals(12.0, result!!, 0.0001)
    }

    @Test
    fun testMultiplicationAndDivision() {
        val result = SimpleMathEvaluator.evaluate("10 * 5 / 2")
        assertNotNull(result)
        assertEquals(25.0, result!!, 0.0001)
    }

    @Test
    fun testOperatorPrecedence() {
        val result = SimpleMathEvaluator.evaluate("2 + 3 * 4")
        assertNotNull(result)
        assertEquals(14.0, result!!, 0.0001)
    }

    @Test
    fun testParentheses() {
        val result = SimpleMathEvaluator.evaluate("(2 + 3) * 4")
        assertNotNull(result)
        assertEquals(20.0, result!!, 0.0001)
    }

    @Test
    fun testDivisionByZero() {
        val result = SimpleMathEvaluator.evaluate("5 / 0")
        assertNull(result)
    }

    @Test
    fun testInvalidExpression() {
        val result = SimpleMathEvaluator.evaluate("hello + world")
        assertNull(result)
    }
}
