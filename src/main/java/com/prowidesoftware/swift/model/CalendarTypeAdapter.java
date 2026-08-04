/*
 * Copyright 2006-2026 Prowide
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
package com.prowidesoftware.swift.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Jackson module for {@link Calendar} that serializes months as 1-based (January=1, December=12)
 * instead of the Java Calendar internal 0-based convention (January=0, December=11).
 *
 * <p><b>Known limitations:</b>
 * <ul>
 *   <li>Milliseconds are not serialized. Any {@link Calendar#MILLISECOND} precision is lost on roundtrip.</li>
 *   <li>Timezone is not serialized. Deserialization always creates a {@link GregorianCalendar} in the JVM
 *       default timezone, regardless of the timezone set on the original Calendar instance.</li>
 * </ul>
 *
 * @since 10.3.13
 */
public class CalendarTypeAdapter extends SimpleModule {

    static final CalendarTypeAdapter INSTANCE = new CalendarTypeAdapter();

    /**
     * Module reproducing the legacy Calendar JSON format, where months are stored with the Java
     * {@link Calendar} internal 0-based convention.
     */
    static final SimpleModule LEGACY = new SimpleModule("PwLegacyCalendarModule")
            .addSerializer(Calendar.class, new CalendarJsonSerializer(0))
            .addDeserializer(Calendar.class, new CalendarJsonDeserializer(0));

    public CalendarTypeAdapter() {
        super("PwCalendarModule");
        addSerializer(Calendar.class, new CalendarJsonSerializer(1));
        addDeserializer(Calendar.class, new CalendarJsonDeserializer(1));
    }

    /**
     * Creates an ObjectMapper configured to reproduce the JSON shape of the model: properties are read from
     * fields (not getters), null values are omitted, unknown properties are ignored on read, and transient
     * fields are excluded.
     *
     * @param oneBasedCalendarMonths when true the 1-based month {@link Calendar} format is used
     */
    static ObjectMapper mapper(boolean oneBasedCalendarMonths) {
        ObjectMapper mapper = new ObjectMapper()
                .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(MapperFeature.PROPAGATE_TRANSIENT_MARKER)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .setDefaultPrettyPrinter(prettyPrinter());
        mapper.registerModule(oneBasedCalendarMonths ? INSTANCE : LEGACY);
        return mapper;
    }

    private static DefaultPrettyPrinter prettyPrinter() {
        return new GsonStylePrettyPrinter();
    }

    /**
     * Pretty printer producing the same layout as Gson: two spaces of indentation, indented array elements
     * and {@code "name": value} field separators.
     */
    private static class GsonStylePrettyPrinter extends DefaultPrettyPrinter {
        private static final DefaultIndenter INDENTER = new DefaultIndenter("  ", DefaultIndenter.SYS_LF);

        GsonStylePrettyPrinter() {
            _objectFieldValueSeparatorWithSpaces = ": ";
            indentObjectsWith(INDENTER);
            indentArraysWith(INDENTER);
        }

        @Override
        public DefaultPrettyPrinter createInstance() {
            return new GsonStylePrettyPrinter();
        }
    }

    static class CalendarJsonSerializer extends JsonSerializer<Calendar> {
        private final int monthOffset;

        CalendarJsonSerializer(int monthOffset) {
            this.monthOffset = monthOffset;
        }

        @Override
        public void serialize(Calendar src, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeNumberField("year", src.get(Calendar.YEAR));
            gen.writeNumberField("month", src.get(Calendar.MONTH) + monthOffset);
            gen.writeNumberField("dayOfMonth", src.get(Calendar.DAY_OF_MONTH));
            gen.writeNumberField("hourOfDay", src.get(Calendar.HOUR_OF_DAY));
            gen.writeNumberField("minute", src.get(Calendar.MINUTE));
            gen.writeNumberField("second", src.get(Calendar.SECOND));
            gen.writeEndObject();
        }
    }

    static class CalendarJsonDeserializer extends JsonDeserializer<Calendar> {
        private final int monthOffset;

        CalendarJsonDeserializer(int monthOffset) {
            this.monthOffset = monthOffset;
        }

        @Override
        public Calendar deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode node = parser.readValueAsTree();
            int year = getRequiredInt(parser, node, "year");
            int month = getRequiredInt(parser, node, "month") - monthOffset;
            int day = getRequiredInt(parser, node, "dayOfMonth");
            int hour = getOptionalInt(node, "hourOfDay");
            int minute = getOptionalInt(node, "minute");
            int second = getOptionalInt(node, "second");
            return new GregorianCalendar(year, month, day, hour, minute, second);
        }

        private static int getRequiredInt(JsonParser parser, JsonNode node, String field) throws JsonParseException {
            JsonNode element = node.get(field);
            if (element == null || element.isNull()) {
                throw new JsonParseException(parser, "Missing required Calendar field: " + field);
            }
            return element.asInt();
        }

        private static int getOptionalInt(JsonNode node, String field) {
            JsonNode element = node.get(field);
            return element == null || element.isNull() ? 0 : element.asInt();
        }
    }
}
