package com.example.product_service.product.validator;

import com.example.product_service.media.common.MediaType;
import com.example.product_service.product.dto.model.MediaItem;
import com.example.product_service.product.exception.ProductMediaInvalidException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductValidatorTests {

    private final ProductValidator validator = new ProductValidator();

    @Test
    void acceptsUpToNineFilesWithTwoVideosAndSelectsFirstImageAsCover() {
        List<MediaItem> items = new ArrayList<>();
        items.add(MediaItem.builder().objectKey("v1.mp4").mediaType(MediaType.VIDEO).sortOrder(1).build());
        items.add(MediaItem.builder().objectKey("v2.mp4").mediaType(MediaType.VIDEO).sortOrder(2).build());
        for (int i = 3; i <= 9; i++) {
            items.add(MediaItem.builder().objectKey("img" + i + ".jpg").mediaType(MediaType.IMAGE).sortOrder(i).build());
        }

        validator.validateAndNormalizeMedia(items);

        assertEquals("img3.jpg", validator.resolveCoverImageUrl(items));
        assertFalse(Boolean.TRUE.equals(items.get(0).getIsCover()));
        assertTrue(Boolean.TRUE.equals(items.get(2).getIsCover()));
    }

    @Test
    void usesPlaceholderWhenOnlyVideosExist() {
        List<MediaItem> items = List.of(
                MediaItem.builder().objectKey("v1.mp4").mediaType(MediaType.VIDEO).sortOrder(1).build()
        );
        validator.validateAndNormalizeMedia(items);
        assertTrue(validator.resolveCoverImageUrl(items).contains("placehold.co"));
    }

    @Test
    void rejectsEmptyOrOverLimitMedia() {
        assertThrows(ProductMediaInvalidException.class, () -> validator.validateAndNormalizeMedia(List.of()));

        List<MediaItem> tenImages = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            tenImages.add(MediaItem.builder().objectKey("img" + i + ".jpg").mediaType(MediaType.IMAGE).build());
        }
        assertThrows(ProductMediaInvalidException.class, () -> validator.validateAndNormalizeMedia(tenImages));

        List<MediaItem> threeVideos = List.of(
                MediaItem.builder().objectKey("v1.mp4").mediaType(MediaType.VIDEO).build(),
                MediaItem.builder().objectKey("v2.mp4").mediaType(MediaType.VIDEO).build(),
                MediaItem.builder().objectKey("v3.mp4").mediaType(MediaType.VIDEO).build()
        );
        assertThrows(ProductMediaInvalidException.class, () -> validator.validateAndNormalizeMedia(threeVideos));
    }
}
