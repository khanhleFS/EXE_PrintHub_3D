package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.address.AddressRequestDTO;
import com.fpt.printhub_3d.dto.address.AddressResponseDTO;
import com.fpt.printhub_3d.entity.User;

import java.util.List;

public interface AddressService {
    List<AddressResponseDTO> getMyAddresses(User user);
    AddressResponseDTO createAddress(User user, AddressRequestDTO request);
    AddressResponseDTO setDefaultAddress(Long id, User user);
    void deleteAddress(Long id, User user);
}
