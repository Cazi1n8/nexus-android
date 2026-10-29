package br.com.nexus.assistant.gestures

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Small indexed triangle meshes and their visible structural edges, with no asset download. */
data class SceneMesh(val vertices: FloatArray, val triangles: ShortArray, val edges: ShortArray) {
    val vertexCount: Int get() = vertices.size / 3
    init {
        require(vertices.isNotEmpty() && vertices.size % 3 == 0 && vertices.all(Float::isFinite))
        require(vertexCount <= 2_048 && triangles.size % 3 == 0 && edges.size % 2 == 0)
        require((triangles.asSequence() + edges.asSequence()).all { it.toInt() in 0 until vertexCount })
    }
}

object SceneMeshes {
    /** Shared geometry is independent from mutable objects and both mouse controllers. */
    private val meshes: Map<SceneShape, SceneMesh> by lazy { SceneShape.entries.associateWith(::generate) }
    fun forShape(shape: SceneShape): SceneMesh = meshes.getValue(shape)

    private class Builder {
        val vertices = mutableListOf<Float>()
        val triangles = mutableListOf<Short>()
        val edges = mutableListOf<Short>()
        fun vertex(x: Float, y: Float, z: Float): Int {
            val index = vertices.size / 3
            vertices.add(x); vertices.add(y); vertices.add(z)
            return index
        }
        fun triangle(a: Int, b: Int, c: Int) { triangles.add(a.toShort()); triangles.add(b.toShort()); triangles.add(c.toShort()) }
        fun edge(a: Int, b: Int) { edges.add(a.toShort()); edges.add(b.toShort()) }
        fun build() = SceneMesh(vertices.toFloatArray(), triangles.toShortArray(), edges.toShortArray())
    }

    private fun generate(shape: SceneShape): SceneMesh = when (shape) {
        SceneShape.CUBE -> cube()
        SceneShape.PYRAMID -> pyramid()
        SceneShape.SPHERE -> sphere()
        SceneShape.CYLINDER -> cylinder()
        SceneShape.CONE -> cone()
        SceneShape.TORUS -> torus()
    }

    private fun cube(): SceneMesh {
        val vertices = floatArrayOf(-1f,-1f,-1f, 1f,-1f,-1f, 1f,1f,-1f, -1f,1f,-1f,
            -1f,-1f,1f, 1f,-1f,1f, 1f,1f,1f, -1f,1f,1f)
        val triangles = shortArrayOf(0,2,1, 0,3,2, 4,5,6, 4,6,7, 0,1,5, 0,5,4,
            3,7,6, 3,6,2, 0,4,7, 0,7,3, 1,2,6, 1,6,5)
        val edges = shortArrayOf(0,1, 1,2, 2,3, 3,0, 4,5, 5,6, 6,7, 7,4, 0,4, 1,5, 2,6, 3,7)
        return SceneMesh(vertices, triangles, edges)
    }

    private fun pyramid(): SceneMesh = Builder().apply {
        vertex(-1f,-1f,-1f); vertex(1f,-1f,-1f); vertex(1f,-1f,1f); vertex(-1f,-1f,1f); vertex(0f,1f,0f)
        triangle(0,2,1); triangle(0,3,2)
        repeat(4) { side -> val next = (side + 1) % 4; triangle(side,next,4); edge(side,next); edge(side,4) }
    }.build()

    private fun sphere(): SceneMesh = Builder().apply {
        val longitude = 12
        val latitude = 8
        val top = vertex(0f,1f,0f)
        for (row in 1 until latitude) {
            val phi = PI * row / latitude
            repeat(longitude) { column ->
                val theta = 2 * PI * column / longitude
                vertex((sin(phi) * cos(theta)).toFloat(), cos(phi).toFloat(), (sin(phi) * sin(theta)).toFloat())
            }
        }
        val bottom = vertex(0f,-1f,0f)
        fun index(row: Int, column: Int) = 1 + row * longitude + column % longitude
        repeat(longitude) { column ->
            triangle(top,index(0,column+1),index(0,column)); edge(top,index(0,column))
            triangle(bottom,index(latitude-2,column),index(latitude-2,column+1)); edge(bottom,index(latitude-2,column))
        }
        for (row in 0 until latitude - 1) repeat(longitude) { column ->
            edge(index(row,column),index(row,column+1))
            if (row < latitude - 2) {
                val a = index(row,column); val b = index(row,column+1)
                val c = index(row+1,column); val d = index(row+1,column+1)
                triangle(a,b,c); triangle(b,d,c); edge(a,c)
            }
        }
    }.build()

    private fun cylinder(): SceneMesh = Builder().apply {
        val segments = 16
        for (ring in 0..1) repeat(segments) { index ->
            val angle = 2 * PI * index / segments
            vertex(cos(angle).toFloat(), if (ring == 0) -1f else 1f, sin(angle).toFloat())
        }
        val bottom = vertex(0f,-1f,0f); val top = vertex(0f,1f,0f)
        repeat(segments) { a ->
            val b = (a + 1) % segments; val c = a + segments; val d = b + segments
            triangle(a,b,c); triangle(b,d,c); triangle(bottom,b,a); triangle(top,c,d)
            edge(a,b); edge(c,d); edge(a,c)
        }
    }.build()

    private fun cone(): SceneMesh = Builder().apply {
        val segments = 16
        repeat(segments) { index ->
            val angle = 2 * PI * index / segments
            vertex(cos(angle).toFloat(),-1f,sin(angle).toFloat())
        }
        val tip = vertex(0f,1f,0f); val center = vertex(0f,-1f,0f)
        repeat(segments) { a ->
            val b = (a + 1) % segments
            triangle(a,b,tip); triangle(center,b,a); edge(a,b); edge(a,tip)
        }
    }.build()

    private fun torus(): SceneMesh = Builder().apply {
        val rings = 16; val sides = 8
        repeat(rings) { ring ->
            val u = 2 * PI * ring / rings
            repeat(sides) { side ->
                val v = 2 * PI * side / sides
                val radius = .7 + .3 * cos(v)
                vertex((radius * cos(u)).toFloat(),(.3 * sin(v)).toFloat(),(radius * sin(u)).toFloat())
            }
        }
        fun index(ring: Int, side: Int) = (ring % rings) * sides + side % sides
        repeat(rings) { ring -> repeat(sides) { side ->
            val a = index(ring,side); val b = index(ring+1,side)
            val c = index(ring,side+1); val d = index(ring+1,side+1)
            triangle(a,b,c); triangle(b,d,c); edge(a,b); edge(a,c)
        } }
    }.build()
}
