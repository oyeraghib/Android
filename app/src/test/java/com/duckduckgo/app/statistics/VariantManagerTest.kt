/*
 * Copyright (c) 2018 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.statistics

import com.duckduckgo.app.statistics.VariantManager.Companion.DEFAULT_VARIANT
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.CookiePromptManagementExperiment
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.OnboardingCustomizationExperiment
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.OptimiseOnboardingExperiment
import org.junit.Assert.*
import org.junit.Test

class VariantManagerTest {

    private val variants = VariantManager.ACTIVE_VARIANTS +
        VariantManager.REFERRER_VARIANTS +
        DEFAULT_VARIANT

    // SERP Experiment(s)

    @Test
    fun serpControlVariantHasExpectedWeightAndNoFeatures() {
        val variant = variants.first { it.key == "sc" }
        assertEqualsDouble(0.0, variant.weight)
        assertEquals(0, variant.features.size)
    }

    @Test
    fun serpExperimentalVariantHasExpectedWeightAndNoFeatures() {
        val variant = variants.first { it.key == "se" }
        assertEqualsDouble(0.0, variant.weight)
        assertEquals(0, variant.features.size)
    }

    @Test
    fun cookiePromptManagementControlVariantHasExpectedWeightAndNoFeatures() {
        val variant = variants.first { it.key == "ms" }
        assertEqualsDouble(1.0, variant.weight)
        assertEquals(0, variant.features.size)
        assertEquals(0, variant.features.size)
    }

    @Test
    fun cookiePromptManagementExperimentalVariantHasExpectedWeightAndFeatures() {
        val variant = variants.first { it.key == "mt" }
        assertEqualsDouble(1.0, variant.weight)
        assertEquals(1, variant.features.size)
        assertTrue(variant.hasFeature(CookiePromptManagementExperiment))
    }

    @Test
    fun optimiseOnboardingControlVariantHasExpectedWeightAndNoFeatures() {
        val variant = variants.first { it.key == "za" }
        assertEqualsDouble(0.0, variant.weight)
        assertEquals(0, variant.features.size)
        assertEquals(0, variant.features.size)
    }

    @Test
    fun optimiseOnboardingExperimentalVariantHasExpectedWeightAndFeatures() {
        val variant = variants.first { it.key == "zb" }
        assertEqualsDouble(0.0, variant.weight)
        assertEquals(1, variant.features.size)
        assertTrue(variant.hasFeature(OptimiseOnboardingExperiment))
    }

    @Test
    fun onboardingCustomisationControlVariantHasExpectedWeightAndNoFeatures() {
        val variant = variants.first { it.key == "mi" }
        assertEqualsDouble(1.0, variant.weight)
        assertEquals(0, variant.features.size)
        assertEquals(0, variant.features.size)
    }

    @Test
    fun onboardingCustomisationExperimentalVariantHasExpectedWeightAndFeatures() {
        val variant = variants.first { it.key == "mj" }
        assertEqualsDouble(1.0, variant.weight)
        assertEquals(1, variant.features.size)
        assertTrue(variant.hasFeature(OnboardingCustomizationExperiment))
    }

    @Test
    fun verifyNoDuplicateVariantNames() {
        val existingNames = mutableSetOf<String>()
        variants.forEach {
            if (!existingNames.add(it.key)) {
                fail("Duplicate variant name found: ${it.key}")
            }
        }
    }

    @Suppress("SameParameterValue")
    private fun assertEqualsDouble(
        expected: Double,
        actual: Double,
    ) {
        val comparison = expected.compareTo(actual)
        if (comparison != 0) {
            fail("Doubles are not equal. Expected $expected but was $actual")
        }
    }
}
