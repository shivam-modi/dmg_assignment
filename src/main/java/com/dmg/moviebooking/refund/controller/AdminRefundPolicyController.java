package com.dmg.moviebooking.refund.controller;

import com.dmg.moviebooking.refund.dto.RefundPolicyRuleDtos.RefundPolicyRuleRequest;
import com.dmg.moviebooking.refund.dto.RefundPolicyRuleDtos.RefundPolicyRuleResponse;
import com.dmg.moviebooking.refund.service.RefundPolicyAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/refund-policies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminRefundPolicyController {

    private final RefundPolicyAdminService refundPolicyAdminService;

    @GetMapping
    public List<RefundPolicyRuleResponse> list() {
        return refundPolicyAdminService.findAll().stream().map(RefundPolicyRuleResponse::from).toList();
    }

    @PutMapping
    public RefundPolicyRuleResponse upsert(@Valid @RequestBody RefundPolicyRuleRequest request) {
        return RefundPolicyRuleResponse.from(refundPolicyAdminService.upsert(request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        refundPolicyAdminService.delete(id);
    }
}
