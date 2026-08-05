package com.fpt.printhub_3d.dto.order;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BulkOrderRequestDTO {
    private String className;
    private String contactPhone;
    private String deliveryAddress;
    private List<BulkOrderItemDTO> items;

    @Getter
    @Setter
    public static class BulkOrderItemDTO {
        private String customName;
        private String customStudentId;
        private String rulerModel;
        private String color;
        private Integer quantity = 1;
    }
}
