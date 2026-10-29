package br.com.nexus.assistant.gestures

import org.json.JSONArray
import org.json.JSONObject

/** Bounded, versioned scene data. No executable objects, class names or Java deserialization. */
object SceneCodec {
    const val MAX_BYTES = 64 * 1024

    fun encode(scene: SceneState): String {
        scene.validated()
        val objects = JSONArray()
        scene.objects.forEach { item ->
            val transform = item.transform
            objects.put(JSONObject().put("id",item.id).put("shape",item.shape.name)
                .put("x",transform.center.x).put("y",transform.center.y)
                .put("yaw",sceneAngle(transform.yaw)).put("pitch",transform.pitch).put("scale",transform.scale)
                .put("color",item.colorArgb or 0xFF000000.toInt()))
        }
        val text = JSONObject().put("schemaVersion",1).put("objects",objects)
            .put("selectedObjectId",scene.selectedObjectId ?: JSONObject.NULL).toString()
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "O arquivo da cena ultrapassa o limite." }
        return text
    }

    fun decode(text: String): SceneState {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "O arquivo da cena ultrapassa o limite." }
        val root = JSONObject(text)
        require(root.opt("schemaVersion") == 1) { "A versão do arquivo da cena não é compatível." }
        val array = root.getJSONArray("objects")
        require(array.length() <= SceneState.MAX_OBJECTS) { "A cena suporta até 12 objetos." }
        val objects = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val shape = SceneShape.entries.firstOrNull { it.name == item.getString("shape") }
                ?: throw IllegalArgumentException("A cena contém uma forma desconhecida.")
            val color = item.get("color") as? Number ?: throw IllegalArgumentException("Cor inválida na cena.")
            val integer = color.toLong()
            require(integer in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() && integer.toDouble() == color.toDouble()) {
                "Cor inválida na cena."
            }
            SceneObject(item.getString("id"),shape,CubeState(
                center = HandPoint(number(item,"x"),number(item,"y")),
                yaw = sceneAngle(number(item,"yaw")),pitch = number(item,"pitch"),scale = number(item,"scale")),
                colorArgb = integer.toInt() or 0xFF000000.toInt())
        }
        val selected = if (root.isNull("selectedObjectId")) null else root.getString("selectedObjectId")
        return SceneState(objects,selected).validated()
    }

    private fun number(item: JSONObject, name: String): Float {
        val value = item.get(name) as? Number ?: throw IllegalArgumentException("Transformação inválida na cena.")
        val converted = value.toFloat()
        require(converted.isFinite()) { "Transformação inválida na cena." }
        return converted
    }
}
