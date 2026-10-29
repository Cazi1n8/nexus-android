package br.com.nexus.assistant.gestures

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SceneCodecTest {
    @Test fun `scene roundtrip preserves all shapes transforms colors and selection`() {
        val objects = SceneShape.entries.mapIndexed { index, shape ->
            SceneObject("item-$index", shape, CubeState(center = HandPoint(.2f + index * .1f, .6f), yaw = 1.2f,
                pitch = -.2f, scale = .3f), 0xFF548BFF.toInt())
        }
        val scene = SceneState(objects, "item-2")
        assertEquals(scene, SceneCodec.decode(SceneCodec.encode(scene)))
    }

    @Test fun `empty scene roundtrip is valid`() {
        val empty = SceneState(emptyList(), null)
        assertEquals(empty, SceneCodec.decode(SceneCodec.encode(empty)))
    }

    @Test fun `duplicate ids unknown selection unknown shape and future version are rejected`() {
        fun root() = JSONObject(SceneCodec.encode(SceneState()))
        val duplicate = root().apply { getJSONArray("objects").put(getJSONArray("objects").getJSONObject(0)) }
        assertThrows(IllegalArgumentException::class.java) { SceneCodec.decode(duplicate.toString()) }
        assertThrows(IllegalArgumentException::class.java) { SceneCodec.decode(root().put("selectedObjectId", "absent").toString()) }
        val unknown = root().apply { getJSONArray("objects").getJSONObject(0).put("shape", "EXECUTABLE") }
        assertThrows(IllegalArgumentException::class.java) { SceneCodec.decode(unknown.toString()) }
        assertThrows(IllegalArgumentException::class.java) { SceneCodec.decode(root().put("schemaVersion", 2).toString()) }
    }

    @Test fun `oversize data excessive objects and out of range transforms are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SceneCodec.decode("x".repeat(SceneCodec.MAX_BYTES + 1)) }
        val excessive = SceneState(List(SceneState.MAX_OBJECTS + 1) { SceneObject("item-$it") })
        assertThrows(IllegalArgumentException::class.java) { SceneCodec.encode(excessive) }
        val root = JSONObject(SceneCodec.encode(SceneState()))
        root.getJSONArray("objects").getJSONObject(0).put("scale", 1000)
        assertThrows(IllegalArgumentException::class.java) { SceneCodec.decode(root.toString()) }
    }

    @Test fun `fractional or out of range color does not silently wrap`() {
        for (color in listOf(3.5, 4294967296.0)) {
            val root = JSONObject(SceneCodec.encode(SceneState()))
            root.getJSONArray("objects").getJSONObject(0).put("color", color)
            assertThrows(IllegalArgumentException::class.java) { SceneCodec.decode(root.toString()) }
        }
    }

    @Test fun `selection grip is never persisted and alpha is always opaque`() {
        val source = SceneState(listOf(SceneObject("object", transform = CubeState(selected = true, bimanual = true), colorArgb = 0x0031E7FA)))
        val restored = SceneCodec.decode(SceneCodec.encode(source)).selectedObject!!
        assertFalse(restored.transform.selected)
        assertFalse(restored.transform.bimanual)
        assertEquals(255, restored.colorArgb ushr 24)
    }
}
