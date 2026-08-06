package com.fpt.printhub_3d.dto.address;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddressRequestDTO {
    private String name;
    private String phone;
    private String addressLine;
    private String province;
    private Boolean isDefault = false;
}
