package com.dmg.moviebooking.pricing.service;

import com.dmg.moviebooking.common.exception.ResourceNotFoundException;
import com.dmg.moviebooking.pricing.dto.DiscountCodeDtos.DiscountCodeRequest;
import com.dmg.moviebooking.pricing.entity.DiscountCode;
import com.dmg.moviebooking.pricing.repository.DiscountCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Admin CRUD for discount codes — discount application/redemption logic lives in {@link DiscountService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiscountCodeAdminService {

    private final DiscountCodeRepository discountCodeRepository;

    public List<DiscountCode> findAll() {
        return discountCodeRepository.findAll();
    }

    @Transactional
    public DiscountCode create(DiscountCodeRequest request) {
        DiscountCode discountCode = DiscountCode.builder()
                .code(request.code())
                .type(request.type())
                .value(request.value())
                .validFrom(request.validFrom())
                .validTo(request.validTo())
                .maxUses(request.maxUses())
                .usedCount(0)
                .active(request.active())
                .build();
        return discountCodeRepository.save(discountCode);
    }

    @Transactional
    public DiscountCode update(Long id, DiscountCodeRequest request) {
        DiscountCode discountCode = discountCodeRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("DiscountCode", id));
        discountCode.setCode(request.code());
        discountCode.setType(request.type());
        discountCode.setValue(request.value());
        discountCode.setValidFrom(request.validFrom());
        discountCode.setValidTo(request.validTo());
        discountCode.setMaxUses(request.maxUses());
        discountCode.setActive(request.active());
        return discountCode;
    }

    @Transactional
    public void delete(Long id) {
        if (!discountCodeRepository.existsById(id)) {
            throw ResourceNotFoundException.of("DiscountCode", id);
        }
        discountCodeRepository.deleteById(id);
    }
}
