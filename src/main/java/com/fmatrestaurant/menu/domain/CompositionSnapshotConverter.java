package com.fmatrestaurant.menu.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.json.JsonMapper;

/** Stores an owned immutable value; historical slots never share rows with the live offer. */
@Converter
public class CompositionSnapshotConverter implements AttributeConverter<CompositionSnapshot, String> {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	@Override
	public String convertToDatabaseColumn(CompositionSnapshot snapshot) {
		return snapshot == null ? null : MAPPER.writeValueAsString(snapshot);
	}

	@Override
	public CompositionSnapshot convertToEntityAttribute(String definition) {
		return definition == null ? null : MAPPER.readValue(definition, CompositionSnapshot.class);
	}
}
