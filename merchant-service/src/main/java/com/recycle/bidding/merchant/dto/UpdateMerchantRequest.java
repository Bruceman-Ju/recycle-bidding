package com.recycle.bidding.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateMerchantRequest {
    @NotBlank(message = "公司名称不能为空")
    private String companyName;

    @NotBlank(message = "联系人不能为空")
    private String contactName;

    @NotBlank(message = "联系电话不能为空")
    private String phone;
}
