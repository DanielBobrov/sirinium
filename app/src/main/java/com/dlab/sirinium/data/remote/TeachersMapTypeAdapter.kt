package com.dlab.sirinium.data.remote

import com.dlab.sirinium.data.model.Teacher
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.IOException
import java.lang.reflect.ParameterizedType

class TeachersTypeAdapterFactory : TypeAdapterFactory {
    override fun <T : Any?> create(gson: Gson, typeToken: TypeToken<T>): TypeAdapter<T>? {
        val rawType = typeToken.rawType
        if (rawType != Map::class.java) {
            return null
        }

        val type = typeToken.type
        if (type !is ParameterizedType) {
            return null
        }

        val typeArgs = type.actualTypeArguments
        if (typeArgs.size != 2 || typeArgs[0] != String::class.java || typeArgs[1] != Teacher::class.java) {
            return null
        }

        @Suppress("UNCHECKED_CAST")
        return ActualTeachersMapTypeAdapter(gson) as TypeAdapter<T>
    }
}

internal class ActualTeachersMapTypeAdapter(private val gson: Gson) : TypeAdapter<Map<String, Teacher>?>() {

    // Используем getAdapter для получения стандартного адаптера для Map<String, Teacher>
    // Этот делегат будет использоваться для парсинга корректных объектов Teacher внутри карты.
    private val teacherAdapter: TypeAdapter<Teacher> by lazy {
        gson.getAdapter(Teacher::class.java)
    }

    @Throws(IOException::class)
    override fun write(out: JsonWriter, value: Map<String, Teacher>?) {
        if (value == null) {
            out.nullValue()
            return
        }
        // Стандартная сериализация для Map<String, Teacher>
        out.beginObject()
        for ((key, teacherValue) in value) {
            out.name(key)
            teacherAdapter.write(out, teacherValue)
        }
        out.endObject()
    }

    @Throws(IOException::class)
    override fun read(reader: JsonReader): Map<String, Teacher>? {
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> { // Это для самого поля "teachers"
                val result = mutableMapOf<String, Teacher>()
                reader.beginObject()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    when (reader.peek()) {
                        JsonToken.BEGIN_OBJECT -> { // Значение для ключа - это объект, пытаемся прочитать как Teacher
                            try {
                                val teacher = teacherAdapter.read(reader)
                                if (teacher != null) { // teacherAdapter.read может вернуть null, если JSON был "null"
                                    result[key] = teacher
                                }
                            } catch (e: JsonSyntaxException) {
                                System.err.println("JsonSyntaxException parsing Teacher object for key '$key': ${e.message}. Skipping this teacher entry.")
                                reader.skipValue() // Пропускаем некорректный объект Teacher
                            }
                        }
                        JsonToken.STRING -> { // Значение для ключа - строка, создаем "dummy" Teacher
                            val stringValue = reader.nextString()
                            System.err.println("Teacher value for key '$key' is a String: \"$stringValue\". Creating dummy/placeholder Teacher.")
                            result[key] = Teacher(id = key, lastName = stringValue, firstName = "(нет данных)", middleName = null, fio = stringValue)
                        }
                        JsonToken.NULL -> {
                            reader.nextNull() // Пропускаем null значение для этого ключа
                        }
                        else -> { // Неожиданный токен для значения преподавателя
                            System.err.println("Unexpected token for teacher value for key '$key': ${reader.peek()}. Skipping value.")
                            reader.skipValue()
                        }
                    }
                }
                reader.endObject()
                if (result.isEmpty() && reader.peek() == JsonToken.END_DOCUMENT) {
                    // Если объект был пустым и это конец документа, возможно, это был пустой объект "teachers": {}
                    // В этом случае возвращаем пустую карту.
                    // Однако, если result не пуст, он уже будет возвращен.
                    // Этот блок может быть излишним, так как пустая карта уже будет результатом.
                }
                result
            }
            JsonToken.STRING -> { // Поле "teachers" само по себе является строкой
                val problematicString = reader.nextString()
                System.err.println("Expected BEGIN_OBJECT for 'teachers' field, but was STRING: \"$problematicString\". Returning null for teachers.")
                null
            }
            JsonToken.NAME -> { // Поле "teachers" само по себе начинается с NAME (невалидный JSON для значения)
                val problematicName = reader.nextName()
                System.err.println("Expected a JSON value (object, string, or null) for 'teachers' field, but found a NAME token: \"$problematicName\". This indicates malformed JSON. Attempting to skip this name and its associated value.")
                try {
                    // После имени должно идти значение. Пропускаем его.
                    if (reader.peek() != JsonToken.END_DOCUMENT) { // Добавлена проверка на конец документа
                        reader.skipValue()
                    }
                } catch (e: Exception) {
                    System.err.println("Exception while skipping value after unexpected NAME token for 'teachers' field: ${e.message}")
                }
                null
            }
            JsonToken.NULL -> { // Поле "teachers" равно null
                reader.nextNull()
                null
            }
            else -> { // Неожиданный токен для поля "teachers"
                System.err.println("Expected BEGIN_OBJECT, STRING, or NULL for 'teachers' field, but was ${reader.peek()}. Skipping token.")
                reader.skipValue()
                null
            }
        }
    }
}
