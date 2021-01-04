package com.zbkj.crmeb.user.service;

import com.common.PageParamRequest;
import com.zbkj.crmeb.front.request.UserAddressRequest;
import com.zbkj.crmeb.user.model.UserAddress;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * UserAddressService 接口实现
 * +----------------------------------------------------------------------
 * | CRMEB [ CRMEB赋能开发者，助力企业发展 ]
 * +----------------------------------------------------------------------
 * | Copyright (c) 2016~2020 https://www.crmeb.com All rights reserved.
 * +----------------------------------------------------------------------
 * | Licensed CRMEB并不是自由软件，未经许可不能去掉CRMEB相关版权
 * +----------------------------------------------------------------------
 * | Author: CRMEB Team <admin@crmeb.com>
 * +----------------------------------------------------------------------
 */
public interface UserAddressService extends IService<UserAddress> {

    List<UserAddress> getList(PageParamRequest pageParamRequest);

    /**
     * 根据基本条件查询
     * @param address 查询条件
     * @return 查询到的地址
     */
    UserAddress getUserAddress(UserAddress address);

    UserAddress create(UserAddressRequest request);

    boolean def(Integer id);

    boolean delete(Integer id);

    UserAddress getDefault();

    UserAddress getById(Integer addressId);

    /**
     * 根据地址参数获取用户收货地址
     * @param userAddress
     * @param pageParamRequest
     * @return
     */
    List<UserAddress> getListByUserAddress(UserAddress userAddress, PageParamRequest pageParamRequest);
}
