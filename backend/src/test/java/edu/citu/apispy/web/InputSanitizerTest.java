package edu.citu.apispy.web;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputSanitizerTest {

	@Test
	void textTrimsAndStripsControls() {
		assertEquals("agent", InputSanitizer.text("  agent\u0000\u0001  "));
		assertNull(InputSanitizer.optionalText("   "));
	}

	@Test
	void bodyKeepsNewlines() {
		assertEquals("line1\nline2", InputSanitizer.body("line1\nline2\u0000"));
	}

	@Test
	void passwordKeepsSpacesAndSpecials() {
		assertEquals(" Pass 1!", InputSanitizer.password(" Pass 1!\u0000"));
	}

	@Test
	void headersRejectOversizedMaps() {
		Map<String, String> tooMany = new java.util.LinkedHashMap<>();
		for (int i = 0; i < InputLimits.HEADER_ENTRIES + 1; i++) {
			tooMany.put("h" + i, "v");
		}
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> InputSanitizer.headers(tooMany));
		assertTrue(ex.getMessage().contains("Too many headers"));
	}
}
