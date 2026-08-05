package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.dto.address.AddressRequestDTO;
import com.fpt.printhub_3d.entity.Address;
import com.fpt.printhub_3d.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
@CrossOrigin("*")
public class AddressController {

    private final AddressRepository addressRepository;

    @GetMapping
    public ResponseEntity<List<Address>> getAllAddresses() {
        return ResponseEntity.ok(addressRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<Address> createAddress(@RequestBody AddressRequestDTO dto) {
        Address address = new Address();
        address.setRecipientName(dto.getName());
        address.setPhone(dto.getPhone());
        address.setStreet(dto.getAddressLine());
        address.setProvince(dto.getProvince());
        address.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : false);
        Address saved = addressRepository.save(address);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}/default")
    public ResponseEntity<Address> setDefaultAddress(@PathVariable Long id) {
        return addressRepository.findById(id).map(addr -> {
            addr.setIsDefault(true);
            return ResponseEntity.ok(addressRepository.save(addr));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long id) {
        addressRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
