package com.inkflow.crm.module.inventorycount.dto;

import lombok.*;
import java.util.List;
import java.util.UUID;

@Data @NoArgsConstructor @AllArgsConstructor
public class UpdateCountItemsRequest {
    private List<ItemUpdate> items;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ItemUpdate {
        private UUID productId;
        private Double actualQty;
    }
}
