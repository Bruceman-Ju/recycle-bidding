package com.recycle.bidding.merchant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.recycle.bidding.common.exception.BizException;
import com.recycle.bidding.common.exception.ErrorCode;
import com.recycle.bidding.common.result.PageResult;
import com.recycle.bidding.merchant.entity.Merchant;
import com.recycle.bidding.merchant.repository.MerchantRepository;
import com.recycle.bidding.merchant.service.MerchantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantServiceImpl implements MerchantService {

    private final MerchantRepository merchantRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Merchant createMerchant(String companyName, String contactName, String phone) {
        Merchant merchant = Merchant.builder()
                .companyName(companyName)
                .contactName(contactName)
                .phone(phone)
                .status(1)
                .creditScore(100)
                .build();
        merchantRepository.insert(merchant);
        log.info("商户创建成功: id={}, companyName={}", merchant.getId(), companyName);
        return merchant;
    }

    @Override
    public Merchant getMerchantById(Long id) {
        Merchant merchant = merchantRepository.selectById(id);
        if (merchant == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "商户不存在");
        }
        return merchant;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Merchant updateMerchant(Long id, String companyName, String contactName, String phone) {
        Merchant merchant = merchantRepository.selectById(id);
        if (merchant == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "商户不存在");
        }
        merchant.setCompanyName(companyName);
        merchant.setContactName(contactName);
        merchant.setPhone(phone);
        merchantRepository.updateById(merchant);
        log.info("商户信息更新成功: id={}", id);
        return merchant;
    }

    @Override
    public PageResult<Merchant> listMerchants(int page, int size) {
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<Merchant>()
                .orderByDesc(Merchant::getCreatedAt);
        IPage<Merchant> result = merchantRepository.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(result.getTotal(), (int) result.getCurrent(), (int) result.getSize(), result.getRecords());
    }
}
