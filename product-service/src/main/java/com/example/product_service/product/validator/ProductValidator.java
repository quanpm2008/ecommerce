package com.example.product_service.product.validator;

import com.example.product_service.media.common.MediaType;
import com.example.product_service.product.dto.model.MediaItem;
import com.example.product_service.product.exception.ProductMediaInvalidException;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class ProductValidator {

    private static final String PLACEHOLDER_IMAGE = "https://placehold.co/600x400?text=No+Image";

    public void validateAndNormalizeMedia(List<MediaItem> mediaList) {
        if (mediaList == null || mediaList.isEmpty()) {
            throw new ProductMediaInvalidException("Sản phẩm phải có ít nhất 1 media (ảnh hoặc video).");
        }
        if (mediaList.size() > 9) {
            throw new ProductMediaInvalidException("Sản phẩm có tối đa 9 media.");
        }
        long videoCount = mediaList.stream()
                .filter(m -> m.getMediaType() == MediaType.VIDEO)
                .count();
        if (videoCount > 2) {
            throw new ProductMediaInvalidException("Sản phẩm chỉ được phép có tối đa 2 video.");
        }
        for (int i = 0; i < mediaList.size(); i++) {
            if (mediaList.get(i).getSortOrder() == null) {
                mediaList.get(i).setSortOrder(i + 1);
            }
        }
        MediaItem firstImage = mediaList.stream()
                .filter(m -> m.getMediaType() == MediaType.IMAGE)
                .min(Comparator.comparing(MediaItem::getSortOrder))
                .orElse(null);
        for (MediaItem item : mediaList) {
            item.setIsCover(firstImage != null && item == firstImage);
        }
    }

    public String resolveCoverImageUrl(List<MediaItem> mediaList) {
        if (mediaList == null || mediaList.isEmpty()) {
            return PLACEHOLDER_IMAGE;
        }
        return mediaList.stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsCover()) && m.getMediaType() == MediaType.IMAGE)
                .map(MediaItem::getObjectKey)
                .findFirst()
                .orElseGet(() -> mediaList.stream()
                        .filter(m -> m.getMediaType() == MediaType.IMAGE)
                        .min(Comparator.comparing(MediaItem::getSortOrder))
                        .map(MediaItem::getObjectKey)
                        .orElse(PLACEHOLDER_IMAGE));
    }
}
