package com.quantxt.nlp.types;

import com.google.gson.*;
import org.joda.time.DateTime;

import java.lang.reflect.Type;

/**
 * Created by matin on 4/30/17.
 */
public class DateTimeTypeConverter implements JsonSerializer<DateTime>, JsonDeserializer<DateTime> {

    @Override
    public JsonElement serialize(DateTime src, Type srcType, JsonSerializationContext context) {
        return new JsonPrimitive(src.toString());
    }

    @Override
    public DateTime deserialize(JsonElement json, Type type, JsonDeserializationContext context)
            throws JsonParseException {
        return new DateTime(json.getAsString());
    }
}