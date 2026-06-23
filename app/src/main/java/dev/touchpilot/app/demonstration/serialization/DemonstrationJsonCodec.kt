package dev.touchpilot.app.demonstration.serialization

import dev.touchpilot.app.demonstration.DemonstrationSession
import org.json.JSONObject

object DemonstrationJsonCodec {
    fun encode(session: DemonstrationSession): String {
        return session.toJson().toString(2)
    }

    fun decode(json: String): DemonstrationSession {
        return DemonstrationSession.fromJson(JSONObject(json))
    }

    fun encodeCompact(session: DemonstrationSession): String {
        return session.toJson().toString()
    }
}

object DemonstrationSerializer {
    fun toJson(session: DemonstrationSession, pretty: Boolean = true): String {
        return if (pretty) DemonstrationJsonCodec.encode(session) else DemonstrationJsonCodec.encodeCompact(session)
    }

    fun toJsonObject(session: DemonstrationSession): JSONObject = session.toJson()
}

object DemonstrationDeserializer {
    fun fromJson(json: String): DemonstrationSession = DemonstrationJsonCodec.decode(json)

    fun fromJsonObject(json: JSONObject): DemonstrationSession = DemonstrationSession.fromJson(json)
}
