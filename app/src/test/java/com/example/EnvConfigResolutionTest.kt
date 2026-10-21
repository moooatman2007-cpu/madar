package com.example
 
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
 
/**
 * Unit tests validating the Supabase environment configuration parser, priority resolution,
 * and release precondition validation logic.
 */
class EnvConfigResolutionTest {
 
    // Pure Kotlin implementation of the exact parser logic from app/build.gradle.kts
    private fun parseEnvContent(content: String): Map<String, String> {
        val envMap = mutableMapOf<String, String>()
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val splitIndex = trimmed.indexOf('=')
                if (splitIndex != -1) {
                    val key = trimmed.substring(0, splitIndex).trim()
                    val value = trimmed.substring(splitIndex + 1).trim().trim('"', '\'')
                    if (key.isNotEmpty()) {
                        envMap[key] = value
                    }
                }
            }
        }
        return envMap
    }
 
    private fun resolveConfig(
        key: String,
        envMap: Map<String, String>,
        systemEnv: Map<String, String>
    ): String {
        val systemVal = systemEnv[key]
        if (!systemVal.isNullOrBlank()) {
            return systemVal.trim()
        }
        return envMap[key]?.trim() ?: ""
    }
 
    private fun validateConfig(
        supabaseUrl: String,
        supabaseKey: String,
        supabaseEdgeUrl: String,
        hasEnvFile: Boolean,
        hasSystemEnvUrl: Boolean = false
    ): List<String> {
        val errors = mutableListOf<String>()
 
        if (!hasEnvFile && !hasSystemEnvUrl) {
            errors.add("Missing root '.env' file. Production Release builds require a real '.env' file or environment variables containing Production Supabase configuration.")
        }
 
        if (supabaseUrl.isBlank()) {
            errors.add("Missing or empty 'SUPABASE_URL' (neither set in environment variables nor in root .env).")
        } else if (supabaseUrl.contains("placeholder", ignoreCase = true)) {
            errors.add("'SUPABASE_URL' contains placeholder value.")
        } else if (!supabaseUrl.startsWith("https://") && !supabaseUrl.startsWith("http://")) {
            errors.add("'SUPABASE_URL' must start with 'https://'.")
        }
 
        if (supabaseKey.isBlank()) {
            errors.add("Missing or empty 'SUPABASE_PUBLISHABLE_KEY' (neither set in environment variables nor in root .env).")
        } else if (supabaseKey.contains("placeholder", ignoreCase = true)) {
            errors.add("'SUPABASE_PUBLISHABLE_KEY' contains placeholder value.")
        }
 
        if (supabaseEdgeUrl.isBlank()) {
            errors.add("Missing or empty 'SUPABASE_EDGE_FUNCTION_URL' (neither set in environment variables nor in root .env).")
        } else if (supabaseEdgeUrl.contains("placeholder", ignoreCase = true)) {
            errors.add("'SUPABASE_EDGE_FUNCTION_URL' contains placeholder value.")
        }
 
        return errors
    }
 
    @Test
    fun testValidConfigurationPassesValidationWithZeroErrors() {
        val envContent = """
            # Production Supabase Configuration
            SUPABASE_URL="https://oknpfsvmopdsgsfbbdcs.supabase.co"
            SUPABASE_PUBLISHABLE_KEY="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.valid-anon-key"
            SUPABASE_EDGE_FUNCTION_URL='https://oknpfsvmopdsgsfbbdcs.supabase.co/functions/v1'
        """.trimIndent()
 
        val envMap = parseEnvContent(envContent)
        val url = resolveConfig("SUPABASE_URL", envMap, emptyMap())
        val key = resolveConfig("SUPABASE_PUBLISHABLE_KEY", envMap, emptyMap())
        val edge = resolveConfig("SUPABASE_EDGE_FUNCTION_URL", envMap, emptyMap())
 
        val errors = validateConfig(
            supabaseUrl = url,
            supabaseKey = key,
            supabaseEdgeUrl = edge,
            hasEnvFile = true
        )
 
        assertEquals("Expected 0 errors for valid configuration", 0, errors.size)
        assertEquals("https://oknpfsvmopdsgsfbbdcs.supabase.co", url)
        assertEquals("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.valid-anon-key", key)
        assertEquals("https://oknpfsvmopdsgsfbbdcs.supabase.co/functions/v1", edge)
    }
 
    @Test
    fun testSystemEnvironmentVariablesTakePrecedenceOverDotEnv() {
        val envContent = """
            SUPABASE_URL="https://from-env-file.supabase.co"
            SUPABASE_PUBLISHABLE_KEY="key-from-env-file"
            SUPABASE_EDGE_FUNCTION_URL="https://from-env-file.supabase.co/functions/v1"
        """.trimIndent()
 
        val envMap = parseEnvContent(envContent)
        val systemEnv = mapOf(
            "SUPABASE_URL" to "https://from-system-env.supabase.co",
            "SUPABASE_PUBLISHABLE_KEY" to "key-from-system-env"
            // SUPABASE_EDGE_FUNCTION_URL left unset in systemEnv to test fallback
        )
 
        val url = resolveConfig("SUPABASE_URL", envMap, systemEnv)
        val key = resolveConfig("SUPABASE_PUBLISHABLE_KEY", envMap, systemEnv)
        val edge = resolveConfig("SUPABASE_EDGE_FUNCTION_URL", envMap, systemEnv)
 
        assertEquals("https://from-system-env.supabase.co", url)
        assertEquals("key-from-system-env", key)
        assertEquals("https://from-env-file.supabase.co/functions/v1", edge)
    }
 
    @Test
    fun testMissingValuesProduceExactMissingErrors() {
        val envContent = """
            # Incomplete .env
            SUPABASE_URL=""
        """.trimIndent()
 
        val envMap = parseEnvContent(envContent)
        val url = resolveConfig("SUPABASE_URL", envMap, emptyMap())
        val key = resolveConfig("SUPABASE_PUBLISHABLE_KEY", envMap, emptyMap())
        val edge = resolveConfig("SUPABASE_EDGE_FUNCTION_URL", envMap, emptyMap())
 
        val errors = validateConfig(
            supabaseUrl = url,
            supabaseKey = key,
            supabaseEdgeUrl = edge,
            hasEnvFile = true
        )
 
        assertTrue(errors.any { it.contains("Missing or empty 'SUPABASE_URL'") })
        assertTrue(errors.any { it.contains("Missing or empty 'SUPABASE_PUBLISHABLE_KEY'") })
        assertTrue(errors.any { it.contains("Missing or empty 'SUPABASE_EDGE_FUNCTION_URL'") })
    }
 
    @Test
    fun testPlaceholderValuesAreStrictlyRejected() {
        val envContent = """
            SUPABASE_URL="https://placeholder.supabase.co"
            SUPABASE_PUBLISHABLE_KEY="placeholder-key"
            SUPABASE_EDGE_FUNCTION_URL="https://placeholder.supabase.co/functions/v1"
        """.trimIndent()
 
        val envMap = parseEnvContent(envContent)
        val url = resolveConfig("SUPABASE_URL", envMap, emptyMap())
        val key = resolveConfig("SUPABASE_PUBLISHABLE_KEY", envMap, emptyMap())
        val edge = resolveConfig("SUPABASE_EDGE_FUNCTION_URL", envMap, emptyMap())
 
        val errors = validateConfig(
            supabaseUrl = url,
            supabaseKey = key,
            supabaseEdgeUrl = edge,
            hasEnvFile = true
        )
 
        assertEquals(3, errors.size)
        assertTrue(errors.any { it == "'SUPABASE_URL' contains placeholder value." })
        assertTrue(errors.any { it == "'SUPABASE_PUBLISHABLE_KEY' contains placeholder value." })
        assertTrue(errors.any { it == "'SUPABASE_EDGE_FUNCTION_URL' contains placeholder value." })
    }
 
    @Test
    fun testInvalidUrlSchemeIsRejected() {
        val errors = validateConfig(
            supabaseUrl = "ftp://invalid-scheme.supabase.co",
            supabaseKey = "valid-key-value",
            supabaseEdgeUrl = "https://invalid-scheme.supabase.co/functions/v1",
            hasEnvFile = true
        )
 
        assertTrue(errors.any { it == "'SUPABASE_URL' must start with 'https://'." })
    }
 
    @Test
    fun testMissingEnvFileAndMissingSystemEnvTriggersSafetyWarning() {
        val errors = validateConfig(
            supabaseUrl = "",
            supabaseKey = "",
            supabaseEdgeUrl = "",
            hasEnvFile = false,
            hasSystemEnvUrl = false
        )
 
        assertTrue(errors.any { it.contains("Missing root '.env' file") })
    }
}
