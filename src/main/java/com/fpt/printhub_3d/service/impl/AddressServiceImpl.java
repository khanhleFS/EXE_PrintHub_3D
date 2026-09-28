package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.dto.address.AddressRequestDTO;
import com.fpt.printhub_3d.dto.address.AddressResponseDTO;
import com.fpt.printhub_3d.entity.Address;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.AddressRepository;
import com.fpt.printhub_3d.service.AddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;

    private AddressResponseDTO toDTO(Address a) {
        return AddressResponseDTO.builder()
                .id(a.getId())
                .recipientName(a.getRecipientName())
                .phone(a.getPhone())
                .addressLine(a.getStreet())
                .province(a.getProvince())
                .isDefault(Boolean.TRUE.equals(a.getIsDefault()))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponseDTO> getMyAddresses(User user) {
        return addressRepository.findByUserIdOrderByIdAsc(user.getId())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public AddressResponseDTO createAddress(User user, AddressRequestDTO request) {
        List<Address> list = addressRepository.findByUserIdOrderByIdAsc(user.getId());

        Address address = new Address();
        address.setUser(user);
        address.setRecipientName(request.resolveRecipientName());
        address.setPhone(request.getPhone());
        address.setStreet(request.getAddressLine());
        address.setProvince(request.getProvince());

        boolean isDef = Boolean.TRUE.equals(request.getIsDefault()) || list.isEmpty();
        address.setIsDefault(isDef);

        if (isDef && !list.isEmpty()) {
            for (Address a : list) {
                a.setIsDefault(false);
                addressRepository.save(a);
            }
        }

        Address saved = addressRepository.save(address);
        return toDTO(saved);
    }

    @Override
    public AddressResponseDTO setDefaultAddress(Long id, User user) {
        Address address = addressRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy địa chỉ"));

        List<Address> list = addressRepository.findByUserIdOrderByIdAsc(user.getId());
        for (Address a : list) {
            a.setIsDefault(a.getId().equals(id));
            addressRepository.save(a);
        }

        address.setIsDefault(true);
        return toDTO(address);
    }

    @Override
    public void deleteAddress(Long id, User user) {
        Address address = addressRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy địa chỉ"));

        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        addressRepository.delete(address);

        if (wasDefault) {
            List<Address> remaining = addressRepository.findByUserIdOrderByIdAsc(user.getId());
            if (!remaining.isEmpty()) {
                Address first = remaining.getFirst();
                first.setIsDefault(true);
                addressRepository.save(first);
            }
        }
    }
}
