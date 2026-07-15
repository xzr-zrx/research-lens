package org.example.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractInvertedIndexConverterTest {
    @Test
    void rebuildsAbstractInOriginalOrder() throws Exception {
        var node = new ObjectMapper().readTree("{\"phase\":[0],\"imaging\":[1],\"method\":[2]}");
        assertEquals("phase imaging method", AbstractInvertedIndexConverter.convert(node));
    }
}
