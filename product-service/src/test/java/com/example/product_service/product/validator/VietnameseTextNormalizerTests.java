package com.example.product_service.product.validator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VietnameseTextNormalizerTests {

    @Test
    void normalizesVietnameseAccentsAndCaseConsistently() {
        assertEquals("dien thoai", VietnameseTextNormalizer.normalize("dien thoai"));
        assertEquals("dien thoai", VietnameseTextNormalizer.normalize("Điện Thoại"));
        assertEquals("dien thoai", VietnameseTextNormalizer.normalize("  ĐIỆN THOẠI  "));
        assertEquals("ao thun nu", VietnameseTextNormalizer.normalize("Áo thun nữ"));
    }

    @Test
    void handlesNullAndBlankInputs() {
        assertEquals("", VietnameseTextNormalizer.normalize(null));
        assertEquals("", VietnameseTextNormalizer.normalize("   "));
    }
}
