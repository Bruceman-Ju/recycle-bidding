package com.recycle.bidding.merchant.controller;

import com.recycle.bidding.common.result.PageResult;
import com.recycle.bidding.common.result.Result;
import com.recycle.bidding.merchant.dto.CreateMerchantRequest;
import com.recycle.bidding.merchant.dto.UpdateMerchantRequest;
import com.recycle.bidding.merchant.entity.Merchant;
import com.recycle.bidding.merchant.service.MerchantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/merchant")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    @PostMapping
    public Result<Merchant> createMerchant(@Valid @RequestBody CreateMerchantRequest req) {
        Merchant merchant = merchantService.createMerchant(req.getCompanyName(), req.getContactName(), req.getPhone());
        return Result.ok(merchant);
    }

    @GetMapping("/{id}")
    public Result<Merchant> getMerchant(@PathVariable Long id) {
        Merchant merchant = merchantService.getMerchantById(id);
        return Result.ok(merchant);
    }

    @PutMapping("/{id}")
    public Result<Merchant> updateMerchant(@PathVariable Long id,
                                           @Valid @RequestBody UpdateMerchantRequest req) {
        Merchant merchant = merchantService.updateMerchant(id, req.getCompanyName(), req.getContactName(), req.getPhone());
        return Result.ok(merchant);
    }

    @GetMapping("/list")
    public Result<PageResult<Merchant>> listMerchants(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        PageResult<Merchant> result = merchantService.listMerchants(page, size);
        return Result.ok(result);
    }
}
