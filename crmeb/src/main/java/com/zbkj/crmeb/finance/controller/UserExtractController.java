package com.zbkj.crmeb.finance.controller;

import com.common.CommonPage;
import com.common.CommonResult;
import com.common.PageParamRequest;
import com.utils.DateUtil;
import com.utils.vo.dateLimitUtilVo;
import com.zbkj.crmeb.finance.model.UserExtract;
import com.zbkj.crmeb.finance.request.UserExtractRequest;
import com.zbkj.crmeb.finance.request.UserExtractSearchRequest;
import com.zbkj.crmeb.finance.response.BalanceResponse;
import com.zbkj.crmeb.finance.service.UserExtractService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/**
 * 用户提现表 前端控制器
 *  +----------------------------------------------------------------------
 *  | CRMEB [ CRMEB赋能开发者，助力企业发展 ]
 *  +----------------------------------------------------------------------
 *  | Copyright (c) 2016~2020 https://www.crmeb.com All rights reserved.
 *  +----------------------------------------------------------------------
 *  | Licensed CRMEB并不是自由软件，未经许可不能去掉CRMEB相关版权
 *  +----------------------------------------------------------------------
 *  | Author: CRMEB Team <admin@crmeb.com>
 *  +----------------------------------------------------------------------
 */
@Slf4j
@RestController
@RequestMapping("api/admin/finance/apply")
@Api(tags = "财务 -- 提现申请")

public class UserExtractController {

    @Autowired
    private UserExtractService userExtractService;

    /**
     * 分页显示用户提现表
     * @param request 搜索条件
     * @param pageParamRequest 分页参数
     * @author Mr.Zhang
     * @since 2020-05-11
     */
    @ApiOperation(value = "分页列表")
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public CommonResult<CommonPage<UserExtract>>  getList(@Validated UserExtractSearchRequest request, @Validated PageParamRequest pageParamRequest){
        CommonPage<UserExtract> userExtractCommonPage = CommonPage.restPage(userExtractService.getList(request, pageParamRequest));
        return CommonResult.success(userExtractCommonPage);
    }

    /**
     * 修改用户提现表
     * @param id integer id
     * @param userExtractRequest 修改参数
     * @author Mr.Zhang
     * @since 2020-05-11
     */
    @ApiOperation(value = "修改")
    @RequestMapping(value = "/update", method = RequestMethod.POST)
    public CommonResult<String> update(@RequestParam Integer id, @Validated UserExtractRequest userExtractRequest){
        UserExtract userExtract = new UserExtract();
        BeanUtils.copyProperties(userExtractRequest, userExtract);
        userExtract.setId(id);

        if(userExtractService.updateById(userExtract)){
            return CommonResult.success();
        }else{
            return CommonResult.failed();
        }
    }

    /**
     * 提现总金额
     * @Param dateLimit 时间限制 today,yesterday,lately7,lately30,month,year,/yyyy-MM-dd hh:mm:ss,yyyy-MM-dd hh:mm:ss/
     * @author Mr.Zhang
     * @since 2020-05-11
     */
    @ApiOperation(value = "提现总金额")
    @RequestMapping(value = "/balance", method = RequestMethod.POST)
    public CommonResult<BalanceResponse> balance(
            @RequestParam(value = "dateLimit", required = false,
                    defaultValue = "")
                    String dateLimit){
        String startTime = null;
        String endTime = null;
        if(StringUtils.isNotBlank(dateLimit)){
            dateLimitUtilVo dateRage = DateUtil.getDateLimit(dateLimit);
            startTime = dateRage.getStartTime();
            endTime = dateRage.getEndTime();
        }

        return CommonResult.success(userExtractService.getBalance(startTime,endTime));
    }

    /**
     * 提现审核
     * @param id    提现id
     * @param status    审核状态 -1 未通过 0 审核中 1 已提现
     * @param backMessage   驳回原因
     * @return 审核结果
     */
    @ApiOperation(value = "提现申请审核")
    @RequestMapping(value = "/apply", method = RequestMethod.POST)
    public CommonResult<String> updateStatus(@RequestParam(value = "id") Integer id,
                                             @RequestParam(value = "status",defaultValue = "审核状态 -1 未通过 0 审核中 1 已提现") Integer status,
                                             @RequestParam(value = "backMessage",defaultValue = "驳回原因", required = false) String backMessage){
        if(userExtractService.updateStatus(id, status, backMessage)){
            return CommonResult.success();
        }else{
            return CommonResult.failed();
        }
    }
}



