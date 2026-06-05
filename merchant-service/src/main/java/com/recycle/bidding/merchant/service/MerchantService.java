package com.recycle.bidding.merchant.service;

import com.recycle.bidding.common.result.PageResult;
import com.recycle.bidding.merchant.entity.Merchant;

public interface MerchantService {

    /**
     * 创建商户
     */
    Merchant createMerchant(String companyName, String contactName, String phone);

    /**
     * 根据ID查询商户
     */
    Merchant getMerchantById(Long id);

    /**
     * 更新商户信息
     */
    Merchant updateMerchant(Long id, String companyName, String contactName, String phone);

    /**
     * 商户分页列表
     */
    PageResult<Merchant> listMerchants(int page, int size);
}
