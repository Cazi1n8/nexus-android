package br.com.nexus.assistant.gestures

import org.junit.Assert.*
import org.junit.Test

class SceneMeshTest {
    @Test fun `each generated mesh is closed with no degenerate triangles`() {
        SceneShape.entries.forEach { shape ->
            val mesh = SceneMeshes.forShape(shape)
            assertTrue(mesh.vertexCount <= 256)
            assertTrue(mesh.edges.isNotEmpty())
            val edges = mutableMapOf<Pair<Int, Int>, Int>()
            mesh.triangles.toList().chunked(3).forEach { triangle ->
                val a = triangle[0].toInt(); val b = triangle[1].toInt(); val c = triangle[2].toInt()
                val ux = mesh.vertices[b * 3] - mesh.vertices[a * 3]
                val uy = mesh.vertices[b * 3 + 1] - mesh.vertices[a * 3 + 1]
                val uz = mesh.vertices[b * 3 + 2] - mesh.vertices[a * 3 + 2]
                val vx = mesh.vertices[c * 3] - mesh.vertices[a * 3]
                val vy = mesh.vertices[c * 3 + 1] - mesh.vertices[a * 3 + 1]
                val vz = mesh.vertices[c * 3 + 2] - mesh.vertices[a * 3 + 2]
                val area = (uy * vz - uz * vy).let { it * it } +
                    (uz * vx - ux * vz).let { it * it } + (ux * vy - uy * vx).let { it * it }
                assertTrue("$shape has a degenerate face", area > 1e-8f)
                listOf(a to b, b to c, c to a).forEach { (first, second) ->
                    val edge = minOf(first, second) to maxOf(first, second)
                    edges[edge] = (edges[edge] ?: 0) + 1
                }
            }
            assertTrue("$shape has a hole or non-manifold edge", edges.values.all { it == 2 })
        }
    }

    @Test fun `meshes have distinct geometry and are reused across scene objects`() {
        val meshes = SceneShape.entries.map(SceneMeshes::forShape)
        assertEquals(6, meshes.map { it.vertices.toList() }.toSet().size)
        SceneShape.entries.forEach { assertSame(SceneMeshes.forShape(it), SceneMeshes.forShape(it)) }
    }

    @Test fun `invalid vertex and index arrays cannot enter renderer`() {
        assertThrows(IllegalArgumentException::class.java) { SceneMesh(floatArrayOf(Float.NaN, 0f, 0f), shortArrayOf(), shortArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) { SceneMesh(floatArrayOf(0f, 0f, 0f), shortArrayOf(0, 0, 2), shortArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) { SceneMesh(floatArrayOf(0f, 0f), shortArrayOf(), shortArrayOf()) }
    }
}
