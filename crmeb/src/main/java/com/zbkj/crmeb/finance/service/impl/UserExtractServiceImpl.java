package com.zbkj.crmeb.finance.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.common.CommonPage;
import com.common.PageParamRequest;
import com.constants.Constants;
import com.exception.CrmebException;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.utils.DateUtil;
import com.utils.vo.dateLimitUtilVo;
import com.zbkj.crmeb.finance.dao.UserExtractDao;
import com.zbkj.crmeb.finance.model.UserExtract;
import com.zbkj.crmeb.finance.request.UserExtractRequest;
import com.zbkj.crmeb.finance.request.UserExtractSearchRequest;
import com.zbkj.crmeb.finance.response.BalanceResponse;
import com.zbkj.crmeb.finance.response.UserExtractResponse;
import com.zbkj.crmeb.finance.service.UserExtractService;
import com.zbkj.crmeb.front.response.UserExtractRecordResponse;
import com.zbkj.crmeb.system.service.SystemAttachmentService;
import com.zbkj.crmeb.system.service.SystemConfigService;
import com.zbkj.crmeb.user.model.User;
import com.zbkj.crmeb.user.service.UserBillService;
import com.zbkj.crmeb.user.service.UserService;
import com.zbkj.crmeb.wechat.service.impl.WechatSendMessageForMinService;
import com.zbkj.crmeb.wechat.vo.WechatSendMessageForCash;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ZERO;

/**
*  UserExtractServiceImpl 接口实现
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
@Service
public class UserExtractServiceImpl extends ServiceImpl<UserExtractDao, UserExtract> implements UserExtractService {

    @Resource
    private UserExtractDao dao;

    @Autowired
    private UserService userService;

    @Autowired
    private UserBillService userBillService;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private WechatSendMessageForMinService wechatSendMessageForMinService;

    @Autowired
    private SystemAttachmentService systemAttachmentService;


    /**
    * 列表
    * @param request 请求参数
    * @param pageParamRequest 分页类参数
    * @author Mr.Zhang
    * @since 2020-05-11
    * @return List<UserExtract>
    */
    @Override
    public List<UserExtract> getList(UserExtractSearchRequest request, PageParamRequest pageParamRequest) {
        PageHelper.startPage(pageParamRequest.getPage(), pageParamRequest.getLimit());

        //带 UserExtract 类的多条件查询
        LambdaQueryWrapper<UserExtract> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        if(!StringUtils.isBlank(request.getKeywords())){
            lambdaQueryWrapper.and(i -> i.
                    or().like(UserExtract::getWechat, request.getKeywords()).   //微信号
                    or().like(UserExtract::getRealName, request.getKeywords()). //名称
                    or().like(UserExtract::getBankCode, request.getKeywords()). //银行卡
                    or().like(UserExtract::getBankAddress, request.getKeywords()). //开户行
                    or().like(UserExtract::getAlipayCode, request.getKeywords()). //支付宝
                    or().like(UserExtract::getFailMsg, request.getKeywords()) //失败原因
            );
        }

        //提现状态
        if(request.getStatus() != null){
            lambdaQueryWrapper.eq(UserExtract::getStatus, request.getStatus());
        }

        //提现方式
        if(!StringUtils.isBlank(request.getExtractType())){
            lambdaQueryWrapper.eq(UserExtract::getExtractType, request.getExtractType());
        }

        //时间范围
        if(StringUtils.isNotBlank(request.getDateLimit())){
            dateLimitUtilVo dateLimit = DateUtil.getDateLimit(request.getDateLimit());
            if (dateLimit.getStartTime().length() == 10) {
                dateLimit.setStartTime(dateLimit.getStartTime().concat(" 00:00:00"));
                dateLimit.setEndTime(dateLimit.getEndTime().concat(" 23:59:59"));
            }
            lambdaQueryWrapper.between(UserExtract::getCreateTime, dateLimit.getStartTime(), dateLimit.getEndTime());
        }

        //按创建时间降序排列
        lambdaQueryWrapper.orderByDesc(UserExtract::getCreateTime, UserExtract::getId);

        List<UserExtract> extractList = dao.selectList(lambdaQueryWrapper);
        if (CollUtil.isEmpty(extractList)) {
            return extractList;
        }
        List<Integer> uidList = extractList.stream().map(o -> o.getUid()).distinct().collect(Collectors.toList());
        HashMap<Integer, User> userMap = userService.getMapListInUid(uidList);
        for (UserExtract userExtract : extractList) {
            userExtract.setNickName(Optional.ofNullable(userMap.get(userExtract.getUid()).getNickname()).orElse(""));
        }
        return extractList;
    }

    /**
     * 提现总金额
     * @author Mr.Zhang
     * @since 2020-05-11
     * @return BalanceResponse
     * 总佣金 = 已提现佣金 + 未提现佣金
     * 已提现佣金 = 用户成功提现的金额
     * 未提现佣金 = 用户未提现的佣金 = 可提现佣金 + 冻结佣金 = 用户佣金
     * 可提现佣金 = 包括解冻佣金、提现未通过的佣金 = 用户佣金 - 冻结期佣金
     * 待提现佣金 = 待审核状态的佣金
     * 冻结佣金 = 用户在冻结期的佣金，不包括退回佣金
     * 退回佣金 = 因退款导致的冻结佣金退回
     */
    @Override
    public BalanceResponse getBalance(String startTime,String endTime) {
//        BigDecimal withdrawn = getWithdrawn(startTime,endTime);//已提现
//        BigDecimal unDrawn = getUnDrawn(startTime,endTime);//审核中现
////        BigDecimal commissionTotal = user.getBrokeragePrice();//佣金总金额
//        BigDecimal commissionTotal = userBillService.getSumBrokerage();//佣金总金额
//        BigDecimal toBeWithdrawn = getWaiteForDrawn(startTime,endTime); //待提现

        BigDecimal withdrawn = getWithdrawn(startTime,endTime);//已提现
        BigDecimal toBeWithdrawn = getWithdrawning(startTime,endTime); //待提现(审核中)
//        BigDecimal unDrawn = userService.getUnCommissionPrice();//未提现
        //未提现佣金 = （单位时间内） 增加的佣金 - 消耗的佣金(佣金转余额) - 待提现的佣金 - 已提现金额  (可能会产生负值)
        String date = null;
        if (StrUtil.isNotBlank(startTime) && StrUtil.isNotBlank(endTime)) {
            date = startTime.concat(",").concat(endTime);
        }
        BigDecimal addSum = userBillService.getSumBigDecimal(1, null, Constants.USER_BILL_CATEGORY_BROKERAGE_PRICE, date, null);
        BigDecimal subSum = userBillService.getSumBigDecimal(0, null, Constants.USER_BILL_CATEGORY_BROKERAGE_PRICE, date, null);
        BigDecimal unDrawn = addSum.subtract(subSum).subtract(toBeWithdrawn).subtract(withdrawn);

        //佣金总金额 = 已提现 + 待提现 + 未提现
        BigDecimal commissionTotal = withdrawn.add(toBeWithdrawn).add(unDrawn);//佣金总金额

        return new BalanceResponse(withdrawn, unDrawn, commissionTotal, toBeWithdrawn);
    }


    /**
     * 提现总金额
     * @author Mr.Zhang
     * @since 2020-05-11
     * @return BalanceResponse
     */
    @Override
    public BigDecimal getWithdrawn(String startTime,String endTime) {
        return getSum(null,1,startTime,endTime);
    }

    /**
     * 审核中总金额
     * @author Mr.Zhang
     * @since 2020-05-11
     * @return BalanceResponse
     */
    @Override
    public BigDecimal getWithdrawning(String startTime, String endTime) {
        return getSum(null, 0,startTime,endTime);
    }

    /**
     * 获取待提现总金额
     *
     * @return 待提现总金额
     */
    @Override
    public BigDecimal getWaiteForDrawn(String startTime,String endTime) {
        return getSum(null,-1,startTime,endTime);
    }

    /**
     * 提现申请
     * @author Mr.Zhang
     * @since 2020-06-08
     * @return Boolean
     */
    @Override
    public Boolean create(UserExtractRequest request, Integer userId) {
        //添加判断，提现金额不能小于10元
        BigDecimal ten = new BigDecimal(10);
        if (request.getExtractPrice().compareTo(ten) < 0) {
            throw new CrmebException("最低提现金额10元");
        }
        //看是否有足够的金额可提现
        User user = userService.getById(userId);
        BigDecimal toBeWithdrawn = user.getBrokeragePrice();//提现总金额
        BigDecimal freeze = getFreeze(userId); //冻结的佣金
        BigDecimal money = toBeWithdrawn.subtract(freeze); //可提现总金额

        if(money.compareTo(ZERO) < 1){
            throw new CrmebException("您当前没有金额可以提现");
        }

        int result = money.compareTo(request.getExtractPrice());
        if(result < 0){
            throw new CrmebException("你当前最多可提现 " + toBeWithdrawn + "元");
        }
        UserExtract userExtract = new UserExtract();
        userExtract.setUid(userId);
        BeanUtils.copyProperties(request, userExtract);
        userExtract.setBalance(toBeWithdrawn.subtract(request.getExtractPrice()));
        //存入银行名称
//        userExtract.setBankName(request.getBankName());
        if (StrUtil.isNotBlank(userExtract.getQrcodeUrl())) {
            userExtract.setQrcodeUrl(systemAttachmentService.clearPrefix(userExtract.getQrcodeUrl()));
        }

        // 微信小程序订阅提现通知
        WechatSendMessageForCash cash = new WechatSendMessageForCash(
                "提现申请成功",request.getExtractPrice()+"",request.getBankName()+request.getBankCode(),
                DateUtil.nowDateTimeStr(),"暂无",request.getRealName(),"0",request.getExtractType(),"提现",
                "暂无",request.getExtractType(),"暂无",request.getRealName()
        );
        wechatSendMessageForMinService.sendCashMessage(cash,userId);
//        return save(userExtract);
        save(userExtract);
        // 扣除用户总金额
        return userService.upadteBrokeragePrice(user, toBeWithdrawn.subtract(request.getExtractPrice()));
    }


//    /**
//     * 可提现总金额
//     * @author Mr.Zhang
//     * @since 2020-06-08
//     * @return Boolean
//     */
//    @Override
//    public BigDecimal getToBeWihDraw(Integer userId) {
//        //可提现佣金
//        //返佣 +
//        BigDecimal withDrawable = userBillService.getSumBigDecimal(1, userId, Constants.USER_BILL_CATEGORY_MONEY, Constants.SEARCH_DATE_LATELY_30, Constants.USER_BILL_TYPE_BROKERAGE);
//
//        //退款退的佣金 -
//        BigDecimal refund = userBillService.getSumBigDecimal(0, userId, Constants.USER_BILL_CATEGORY_MONEY, Constants.SEARCH_DATE_LATELY_30, Constants.USER_BILL_TYPE_BROKERAGE);
//
//        BigDecimal subtract = withDrawable.subtract(refund);
//        subtract = (subtract.compareTo(ZERO) < 1) ? ZERO : subtract;
//
//        //用户累计佣金
//        BigDecimal brokeragePrice = userService.getById(userId).getBrokeragePrice();
//
//        //可提现佣金
//        return brokeragePrice.subtract(subtract);
//    }

    /**
     * 冻结的佣金
     * @author Mr.Zhang
     * @since 2020-06-08
     * @return Boolean
     */
    @Override
    public BigDecimal getFreeze(Integer userId) {
//        //冻结时间
//        //查看是否在冻结期之内， 如果在是需要回滚的，如果不在则不需要回滚
//        String time = systemConfigService.getValueByKey(Constants.CONFIG_KEY_STORE_BROKERAGE_EXTRACT_TIME);
//
//        String date = null;
//        if(StringUtils.isNotBlank(time)){
//            String startTime = DateUtil.nowDateTime(Constants.DATE_FORMAT);
//            String endTime = DateUtil.addDay(DateUtil.nowDateTime(), Integer.parseInt(time), Constants.DATE_FORMAT);
//            date = startTime + "," + endTime;
//        }
//
//        //在此期间获得的佣金
//        BigDecimal getSum = userBillService.getSumBigDecimal(1, userId, Constants.USER_BILL_CATEGORY_BROKERAGE_PRICE, null, date);
//
//        //在此期间消耗的佣金
//        BigDecimal subSum = userBillService.getSumBigDecimal(0, userId, Constants.USER_BILL_CATEGORY_BROKERAGE_PRICE, null, date);
//
//        //冻结的佣金
//        return getSum.subtract(subSum);
        String time = systemConfigService.getValueByKey(Constants.CONFIG_KEY_STORE_BROKERAGE_EXTRACT_TIME);
        if (StrUtil.isBlank(time)) {
            return BigDecimal.ZERO;
        }
        String endTime = DateUtil.nowDateTime(Constants.DATE_FORMAT);
        String startTime = DateUtil.addDay(DateUtil.nowDateTime(), -Integer.parseInt(time), Constants.DATE_FORMAT);
        String date = startTime + "," + endTime;
        //在冻结期的资金
        BigDecimal getSum = userBillService.getSumBigDecimal(1, userId, Constants.USER_BILL_CATEGORY_BROKERAGE_PRICE, date, null);
        return getSum;
    }

    /**
     * 根据状态获取总额
     * @author Mr.Zhang
     * @since 2020-05-11 edite by stivepeim 2020-09-29
     * @return BigDecimal
     */
    private BigDecimal getSum(Integer userId, int status, String startTime, String endTime) {
        LambdaQueryWrapper<UserExtract> lqw = Wrappers.lambdaQuery();
        if(null != userId) lqw.eq(UserExtract::getUid,userId);
        lqw.eq(UserExtract::getStatus,status);
        if(StringUtils.isNotBlank(startTime) && StringUtils.isNotBlank(endTime)){
            lqw.between(UserExtract::getCreateTime,startTime,endTime);
        }
        List<UserExtract> userExtracts = dao.selectList(lqw);
//        double sum = 0;
        BigDecimal sum = ZERO;
        if(null != userExtracts && userExtracts.size() > 0) {
            sum = userExtracts.stream().map(UserExtract::getExtractPrice).reduce(ZERO, BigDecimal::add);
        }
//        sum = userExtracts.stream().mapToDouble(e -> e.getExtractPrice().doubleValue()).sum();
        return sum;
    }

    /**
     * 获取用户对应的提现数据
     * @param userId 用户id
     * @return 提现数据
     */
    @Override
    public UserExtractResponse getUserExtractByUserId(Integer userId) {
        QueryWrapper<UserExtract> qw = new QueryWrapper<>();
        qw.select("SUM(extract_price) as extract_price,count(id) as id, uid");
        qw.ge("status", 1);
        qw.eq("uid",userId);
        qw.groupBy("uid");
        UserExtract ux = dao.selectOne(qw);
        UserExtractResponse uexr = new UserExtractResponse();
//        uexr.setEuid(ux.getUid());
        if(null != ux){
            uexr.setExtractCountNum(ux.getId()); // 这里的id其实是数量，借变量传递
            uexr.setExtractCountPrice(ux.getExtractPrice());
        }else{
            uexr.setExtractCountNum(0); // 这里的id其实是数量，借变量传递
            uexr.setExtractCountPrice(ZERO);
        }

        return uexr;
    }

    /**
     * 根据用户id集合获取对应提现用户集合
     * @param userIds 用户id集合
     * @return 提现用户集合
     */
    @Override
    public List<UserExtract> getListByUserIds(List<Integer> userIds) {
        LambdaQueryWrapper<UserExtract> lqw = new LambdaQueryWrapper<>();
        lqw.in(UserExtract::getUid, userIds);
        return dao.selectList(lqw);
    }

    /**
     * 提现审核
     *
     * @param id          提现申请id
     * @param status      审核状态 -1 未通过 0 审核中 1 已提现
     * @param backMessage 驳回原因
     * @return 审核结果
     */
    @Override
    public boolean updateStatus(Integer id, Integer status, String backMessage) {
        if(status == -1 && StringUtils.isBlank(backMessage))
            throw new CrmebException("驳回时请填写驳回原因");

        UserExtract ue = new UserExtract().setId(id).setStatus(status).setFailMsg(backMessage);
        if (status == -1) {//未通过时恢复用户总金额
            UserExtract userExtract = getById(id);
            User user = userService.getById(userExtract.getUid());
            userService.upadteBrokeragePrice(user, user.getBrokeragePrice().add(userExtract.getExtractPrice()));
        }
        //TODO 审核申请通过已提现，是否添加user_bill用户账单表记录
        return dao.updateById(ue) > 0;
    }

    /**
     * 提现记录
     * @return
     */
    @Override
    public PageInfo<UserExtractRecordResponse> getExtractRecord(Integer userId, PageParamRequest pageParamRequest){
//        PageHelper.startPage(pageParamRequest.getPage(), pageParamRequest.getLimit());
        Page<UserExtract> userExtractPage = PageHelper.startPage(pageParamRequest.getPage(), pageParamRequest.getLimit());
        QueryWrapper<UserExtract> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("uid", userId);

        queryWrapper.groupBy("left(create_time, 7)");
        queryWrapper.orderByDesc("left(create_time, 7)");
        List<UserExtract> list = dao.selectList(queryWrapper);
        if(CollUtil.isEmpty(list)){
            return new PageInfo<>();
        }
        ArrayList<UserExtractRecordResponse> userExtractRecordResponseList = CollectionUtil.newArrayList();
        for (UserExtract userExtract : list) {
            String date = DateUtil.dateToStr(userExtract.getCreateTime(), Constants.DATE_FORMAT_MONTH);
            userExtractRecordResponseList.add(new UserExtractRecordResponse(date, getListByMonth(userId, date)));
        }

        return CommonPage.copyPageInfo(userExtractPage, userExtractRecordResponseList);
    }

    private List<UserExtract> getListByMonth(Integer userId, String date) {
        LambdaQueryWrapper<UserExtract> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(UserExtract::getUid, userId);
//        lambdaQueryWrapper.in(UserExtract::getStatus, -1, 1);
        lambdaQueryWrapper.orderByDesc(UserExtract::getCreateTime, UserExtract::getId);
        return dao.selectList(lambdaQueryWrapper);
    }

    /**
     * 获取用户提现总金额
     * @param userId
     * @return
     */
    @Override
    public BigDecimal getExtractTotalMoney(Integer userId){
        return getSum(userId, 1, null, null);
    }
}

