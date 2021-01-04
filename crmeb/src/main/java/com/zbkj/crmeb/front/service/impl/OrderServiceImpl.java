package com.zbkj.crmeb.front.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.common.PageParamRequest;
import com.constants.Constants;
import com.exception.CrmebException;
import com.utils.CrmebUtil;
import com.utils.DateUtil;
import com.utils.RedisUtil;
import com.zbkj.crmeb.bargain.model.StoreBargain;
import com.zbkj.crmeb.bargain.service.StoreBargainService;
import com.zbkj.crmeb.combination.model.StoreCombination;
import com.zbkj.crmeb.combination.service.StoreCombinationService;
import com.zbkj.crmeb.express.model.ShippingTemplates;
import com.zbkj.crmeb.express.service.LogisticService;
import com.zbkj.crmeb.express.service.ShippingTemplatesService;
import com.zbkj.crmeb.express.vo.LogisticsResultVo;
import com.zbkj.crmeb.front.request.*;
import com.zbkj.crmeb.front.response.*;
import com.zbkj.crmeb.front.service.OrderService;
import com.zbkj.crmeb.front.vo.OrderAgainVo;
import com.zbkj.crmeb.marketing.response.StoreCouponUserResponse;
import com.zbkj.crmeb.payment.service.OrderPayService;
import com.zbkj.crmeb.payment.vo.wechat.CreateOrderResponseVo;
import com.zbkj.crmeb.seckill.model.StoreSeckill;
import com.zbkj.crmeb.seckill.service.StoreSeckillService;
import com.zbkj.crmeb.sms.service.SmsService;
import com.zbkj.crmeb.store.model.StoreCart;
import com.zbkj.crmeb.store.model.StoreOrder;
import com.zbkj.crmeb.store.model.StoreOrderInfo;
import com.zbkj.crmeb.store.request.StoreOrderInfoSearchRequest;
import com.zbkj.crmeb.store.request.StoreProductReplyAddRequest;
import com.zbkj.crmeb.store.response.StoreCartResponse;
import com.zbkj.crmeb.store.service.*;
import com.zbkj.crmeb.store.utilService.OrderUtils;
import com.zbkj.crmeb.store.vo.StoreOrderInfoVo;
import com.zbkj.crmeb.system.model.SystemAttachment;
import com.zbkj.crmeb.system.model.SystemStore;
import com.zbkj.crmeb.system.service.SystemAttachmentService;
import com.zbkj.crmeb.system.service.SystemConfigService;
import com.zbkj.crmeb.system.service.SystemStoreService;
import com.zbkj.crmeb.user.model.User;
import com.zbkj.crmeb.user.model.UserAddress;
import com.zbkj.crmeb.user.service.UserAddressService;
import com.zbkj.crmeb.user.service.UserService;
import com.zbkj.crmeb.wechat.service.impl.WechatSendMessageForMinService;
import com.zbkj.crmeb.wechat.vo.WechatSendMessageForCreateOrder;
import com.zbkj.crmeb.wechat.vo.WechatSendMessageForReFundNotify;
import org.apache.catalina.Store;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * H5端订单操作
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
public class OrderServiceImpl implements OrderService {

    @Autowired
    private UserService userService;

    @Autowired
    private StoreCartService storeCartService;

    @Autowired
    private StoreOrderService storeOrderService;

    @Autowired
    private StoreOrderInfoService storeOrderInfoService;

    @Autowired
    private StoreOrderStatusService storeOrderStatusService;

    @Autowired
    private ShippingTemplatesService shippingTemplatesService;

    @Autowired
    private UserAddressService userAddressService;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private StoreProductReplyService storeProductReplyService;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private SystemStoreService systemStoreService;


    @Autowired
    private OrderPayService orderPayService;

    @Autowired
    private OrderUtils orderUtils;

    @Autowired
    private SmsService smsService;

    @Autowired
    private SystemAttachmentService systemAttachmentService;

    @Autowired
    private LogisticService logisticsService;

    @Autowired
    private WechatSendMessageForMinService wechatSendMessageForMinService;

    @Autowired
    private StoreSeckillService storeSeckillService;

    @Autowired
    private StoreCombinationService storeCombinationService;

    @Autowired
    private StoreBargainService storeBargainService;

    /**
     * 确认订单
     * @param cartIds 购物车id集合
     * @param isNew 是否再次下单=从缓存中获取数据
     * @return 确认订单response
     */
    @Override
    public ConfirmOrderResponse confirmOrder(List<String> cartIds, boolean isNew, boolean addAgain,boolean seckill, boolean bargain, boolean combination, Integer addressId) {
        ConfirmOrderResponse response = new ConfirmOrderResponse();
        // 获取运费默认模版
        // ShippingTemplates template = shippingTemplatesService.getById(69);
        // if (null == template)
        ShippingTemplates template = shippingTemplatesService.getById(1);
        if (null == template) throw new CrmebException("默认模板未配置，无法下单");
        User currentUserInfo = userService.getInfoException();

        List<StoreCartResponse> storeCartResponse;
        // 再次下单、秒杀、砍价、拼团
        if (addAgain || seckill || bargain || combination) { // 从redis缓存中获取重新下单和秒杀订单数据、砍价订单数据、拼团订单数据
            String cacheOrderData = orderUtils.getCacheOrderData(cartIds.get(0) + "");
            if (StrUtil.isBlank(cacheOrderData)) throw new CrmebException("未找到订单数据");
            storeCartResponse = JSONObject.parseArray(cacheOrderData, StoreCartResponse.class);
            if (seckill) {
                // 秒杀商品数据验证
                StoreCart storeCartPram = new StoreCart()
                        .setSeckillId(storeCartResponse.get(0).getSeckillId())
                        .setUid(currentUserInfo.getUid())
                        .setProductAttrUnique(storeCartResponse.get(0).getProductAttrUnique());
                orderUtils.validSecKill(storeCartPram, currentUserInfo);
            }
            if (bargain) {
                // 砍价商品数据验证
                StoreCart storeCartPram = new StoreCart()
                        .setBargainId(storeCartResponse.get(0).getBargainId())
                        .setUid(currentUserInfo.getUid())
                        .setProductAttrUnique(storeCartResponse.get(0).getProductAttrUnique());
                orderUtils.validBargain(storeCartPram, currentUserInfo);
            }
            if (combination) {
                // 拼团商品数据验证
                StoreCart storeCartPram = new StoreCart()
                        .setCombinationId(storeCartResponse.get(0).getCombinationId())
                        .setUid(currentUserInfo.getUid())
                        .setProductAttrUnique(storeCartResponse.get(0).getProductAttrUnique())
                        .setCartNum(storeCartResponse.get(0).getCartNum());
                if (ObjectUtil.isNotNull(storeCartResponse.get(0).getPinkId())) {
                    storeCartPram.setPinkId(storeCartResponse.get(0).getPinkId());
                }
                orderUtils.validCombination(storeCartPram, currentUserInfo);
            }
        }else if(isNew){// 获取立即购买数据
            storeCartResponse = storeCartService.getListByUserIdAndCartIds(currentUserInfo.getUid(),cartIds,1);
        }else{ // 获取购物车数据
            storeCartResponse = storeCartService.getListByUserIdAndCartIds(currentUserInfo.getUid(),cartIds,null);
        }

        // 这里需要根据参数判定，不能一直使用默认收货地址
        UserAddress defaultAddress;
        if (addressId > 0) {// 获取选择的地址
            defaultAddress = userAddressService.getById(addressId);
        } else {// 获取默认地址
            defaultAddress = userAddressService.getDefault();
        }

        // 计算订单金额
        PriceGroupResponse orderPriceGroup = orderUtils.getOrderPriceGroup(storeCartResponse, defaultAddress);

        // other
        HashMap<String, Object> otherMap = new HashMap<>();
        otherMap.put("offlinePostage",systemConfigService.getValueByKey("offline_postage"));
        otherMap.put("integralRatio",systemConfigService.getValueByKey("integral_ratio"));

        // 获取有效优惠券
        List<StoreCouponUserResponse> canUseUseCouponList = orderUtils.getCanUseCouponList(storeCartResponse);

        // 设置response信息
        StoreCouponUserResponse canUserCoupon = null;
        if(null != canUseUseCouponList && canUseUseCouponList.size() > 0){
            canUserCoupon = canUseUseCouponList.get(0);
        }
        response.setUsableCoupon(canUserCoupon);
        response.setAddressInfo(defaultAddress);
        response.setCartInfo(storeCartResponse);
        response.setPriceGroup(orderPriceGroup);
        response.setOfflinePostage(otherMap.get("offlinePostage").toString());
        response.setIntegralRatio(otherMap.get("integralRatio").toString());
        response.setUserInfo(currentUserInfo);
        response.setOfflinePayStatus(systemConfigService.getValueByKey("offline_pay_status"));
        response.setYuePayStatus(
                (systemConfigService.getValueByKey("balance_func_status").equals("1")
                        && systemConfigService.getValueByKey("yue_pay_status").equals("1"))? "1":"2"); // 1开启 2关闭
        response.setPayWeixinOpen(systemConfigService.getValueByKey("pay_weixin_open"));
        response.setStoreSelfMention(systemConfigService.getValueByKey("store_self_mention"));
        response.setOther(otherMap);
        response.setSystemStore(null);

        response.setSecKillId(Optional.ofNullable(storeCartResponse.get(0).getSeckillId()).orElse(0));
        response.setBargainId(Optional.ofNullable(storeCartResponse.get(0).getBargainId()).orElse(0));
        response.setCombinationId(Optional.ofNullable(storeCartResponse.get(0).getCombinationId()).orElse(0));
        response.setPinkId(Optional.ofNullable(storeCartResponse.get(0).getPinkId()).orElse(0));
        response.setOrderKey(orderUtils.cacheSetOrderInfo(currentUserInfo.getUid(), response));
        return response;
    }

    /**
     * 订单创建
     * @param request 创建订单参数
     * @param orderKey orderKey
     * @return 创建订单结果
     */
    @Override
    public OrderPayResponse createOrder(OrderCreateRequest request, String orderKey, String ip) {
        OrderPayResponse orderPayResponse = new OrderPayResponse();
        User currentUser = userService.getInfo();
        if (ObjectUtil.isNull(currentUser)) throw new CrmebException("当前用户不存在！");
        // 检查订单是否存在
        if(orderUtils.checkOrderExist(orderKey, currentUser.getUid())) throw new CrmebException(orderKey + "订单已存在");

        // 检测支付方式
        if(!orderUtils.checkPayType(request.getPayType())) throw new CrmebException("暂不支持该支付方式，请刷新页面或者联系管理员");

        // 判断订单是否过期
        String existCacheOrder = orderUtils.cacheGetOrderInfo(currentUser.getUid(), orderKey);
        if(null == existCacheOrder) throw new CrmebException("订单已过期,请刷新当前页面!");

        ConfirmOrderResponse cor = JSONObject.parseObject(existCacheOrder,ConfirmOrderResponse.class);

        // 缓存订单并做计算
        orderUtils.computedOrder(request, cor, orderKey);

        // 检查订单状态
         StoreOrder orderCreated = orderUtils.createOrder(request, cor, 0, request.getStoreId(), orderKey);
        if(null == orderCreated)
            throw new CrmebException("订单生成失败");

        // 立即购买支付
        if(request.getIsNew()){
            HashMap<String, Object> resultMap = new HashMap<>();
            OrderPayRequest orderPayRequest = new OrderPayRequest();
            orderPayRequest.setFrom(request.getFrom());
            orderPayRequest.setPaytype(request.getPayType());
            orderPayRequest.setUni(orderCreated.getOrderId());
            boolean b = doPayOrder(orderPayRequest, ip, resultMap, orderCreated);
            OrderPayItemResponse itemResponse = new OrderPayItemResponse();
            String payType = null;
            switch (request.getPayType()){
                case Constants.PAY_TYPE_WE_CHAT:
                case Constants.PAY_TYPE_WE_CHAT_FROM_PROGRAM:
                    payType = "微信支付下单成功 请支付";
                    orderPayResponse.setStatus("WECHAT_PAY");
                    orderPayResponse.setJsConfig(resultMap.get("jsConfig"));
                    break;
                case Constants.PAY_TYPE_YUE:
                    payType = "余额支付成功";
                    orderPayResponse.setStatus("SUCCESS");
                    break;
                case Constants.PAY_TYPE_OFFLINE:
                    payType = "线下支付";
                    orderPayResponse.setStatus("SUCCESS");
                    break;
            }
            orderPayResponse.setMessage(payType);
            itemResponse.setKey(orderCreated.getOrderId());
            itemResponse.setOrderId(orderKey);
            orderPayResponse.setResult(itemResponse);
        }else{
            String tempStatus = "";
            switch (request.getPayType()){
                case Constants.PAY_TYPE_WE_CHAT:tempStatus="WECHAT_H5_PAY";  break;
                case Constants.PAY_TYPE_YUE:tempStatus="SUCCESS";  break;
                case Constants.PAY_TYPE_OFFLINE:tempStatus="SUCCESS";  break;
            }
            orderPayResponse.setStatus(tempStatus);
            OrderPayItemResponse itemResponse = new OrderPayItemResponse();
            itemResponse.setKey(orderCreated.getOrderId());
            itemResponse.setOrderId(orderKey);
            orderPayResponse.setResult(itemResponse);
        }

        // 清除购物车数据
        List<StoreCartResponse> cartInfo = cor.getCartInfo();
        List<StoreCartResponse> cartList = cartInfo.stream().filter(i -> ObjectUtil.isNotNull(i.getId())).collect(Collectors.toList());
        if (CollUtil.isNotEmpty(cartList)) {
            List<Integer> cartIdList = cartList.stream().map(temp -> temp.getId().intValue()).collect(Collectors.toList());
            storeCartService.deleteCartByIds(cartIdList);
        }

        // 小程序消息订阅发送
        WechatSendMessageForCreateOrder createOrder = new WechatSendMessageForCreateOrder(
                orderUtils.getPayTypeStrByOrder(orderCreated),orderUtils.getStoreNameAndCarNumString(orderCreated.getId()),
                orderCreated.getPayPrice()+"",orderCreated.getId()+"","CRMEB",orderCreated.getCreateTime()+"","创建订单成功","暂无地址",
                orderUtils.getPayTypeStrByOrder(orderCreated),orderCreated.getUserAddress());
        wechatSendMessageForMinService.sendCreateOrderMessage(createOrder, userService.getUserIdException());

        return orderPayResponse;
    }

    /**
     * 删除已完成订单
     * @param id Integer 订单id
     * @return 计算后的价格集合
     */
    @Override
    @Transactional(rollbackFor = {RuntimeException.class, Error.class, CrmebException.class})
    public Boolean delete(Integer id) {
        try{
            StoreOrder storeOrder = orderUtils.getInfoById(id);

            Map<String, String> statusMap = storeOrderService.getStatus(storeOrder);
            String orderStatus = statusMap.get("key");

            //检测删除状态
            orderUtils.checkDeleteStatus(orderStatus);

            //可以删除
            storeOrder.setIsDel(true);
            boolean result = storeOrderService.updateById(storeOrder);

            //后续操作放入redis
            redisUtil.lPush(Constants.ORDER_TASK_REDIS_KEY_AFTER_DELETE_BY_USER, id);

            return result;

        }catch (Exception e){
            throw new CrmebException("删除失败 " + e.getMessage());
        }

    }

    /**
     * 订单评价
     * @param request StoreProductReplyAddRequest 评论信息
     */
    @Override
    public boolean reply(StoreProductReplyAddRequest request) {
        StoreOrder storeOrderPram = new StoreOrder();
        storeOrderPram.setId(request.getOid());
        storeOrderPram.setUid(userService.getUserIdException());
        StoreOrder existStoreOrder = storeOrderService.getByEntityOne(storeOrderPram);
        if(null == existStoreOrder) throw new CrmebException("该订单不存在");
        // 秒杀商品 在这里处理秒杀评价参数 修改对应productId来评价
        if(null != existStoreOrder.getSeckillId() && existStoreOrder.getSeckillId() > 0){
            StoreSeckill currentSeckill = storeSeckillService.getById(existStoreOrder.getSeckillId());
            request.setProductId(currentSeckill.getProductId());

        }
        // 拼团商品
        if (ObjectUtil.isNotNull(existStoreOrder.getCombinationId()) && existStoreOrder.getCombinationId() > 0) {
            StoreCombination currentCombination = storeCombinationService.getById(existStoreOrder.getCombinationId());
            request.setProductId(currentCombination.getProductId());
        }
        // 砍价商品
        if (ObjectUtil.isNotNull(existStoreOrder.getBargainId()) && existStoreOrder.getBargainId() > 0) {
            StoreBargain tempBargain = storeBargainService.getById(existStoreOrder.getBargainId());
            request.setProductId(tempBargain.getProductId());
        }
        return storeProductReplyService.create(request);
    }

    /**
     * 订单收货
     * @param id Integer 订单id
     */
    @Override
    public boolean take(Integer id) {
        try{
            StoreOrder storeOrder = orderUtils.getInfoById(id);
            if(!storeOrder.getStatus().equals(Constants.ORDER_STATUS_INT_SPIKE)){
                throw new CrmebException("订单状态错误");
            }

            //已收货，待评价
            storeOrder.setStatus(Constants.ORDER_STATUS_INT_BARGAIN);
            boolean result = storeOrderService.updateById(storeOrder);

            //后续操作放入redis
            redisUtil.lPush(Constants.ORDER_TASK_REDIS_KEY_AFTER_TAKE_BY_USER, id);
            return result;
        }catch (Exception e){
            throw new CrmebException("收货失败" + e.getMessage());
        }
    }

    /**
     * 订单取消
     * @param id Integer 订单id
     */
    @Override
    public boolean cancel(Integer id) {
        StoreOrder storeOrder = orderUtils.getInfoById(id);
        //已收货，待评价
        storeOrder.setIsDel(true);
        boolean result = storeOrderService.updateById(storeOrder);

        //后续操作放入redis
        redisUtil.lPush(Constants.ORDER_TASK_REDIS_KEY_AFTER_CANCEL_BY_USER, id);
        return result;
    }

    /**
     * 订单退款前验证
     * @param request 退款参数
     */
    @Override
    public boolean refundVerify(OrderRefundVerifyRequest request) {
        User currentUser = userService.getInfo();
        StoreOrder storeOrderPram = new StoreOrder();
        storeOrderPram.setOrderId(request.getUni());
        storeOrderPram.setIsDel(false);
        storeOrderPram.setPaid(true);
        StoreOrder existStoreOrder = storeOrderService.getByEntityOne(storeOrderPram);
        if(null == existStoreOrder) throw new CrmebException("支付订单不存在");
        if(existStoreOrder.getRefundStatus() == 2) throw new CrmebException("订单已退款");
        if(existStoreOrder.getRefundStatus() == 1) throw new CrmebException("正在申请退款中");
        if(existStoreOrder.getStatus() == 1) throw new CrmebException("当前订单无法退款");

        storeOrderStatusService.createLog(existStoreOrder.getId(), Constants.ORDER_LOG_REFUND_APPLY,"用户申请退款原因：" + request.getRefund_reason_wap_explain());

        existStoreOrder.setRefundStatus(1);
        existStoreOrder.setStatus(-1);
        existStoreOrder.setRefundReasonTime(DateUtil.nowDateTime());
        existStoreOrder.setRefundReasonWap(request.getText());
        existStoreOrder.setRefundReasonWapExplain(request.getRefund_reason_wap_explain());
        existStoreOrder.setRefundReasonWapImg(systemAttachmentService.clearPrefix(request.getRefund_reason_wap_img()));
        boolean updateOrderResult = storeOrderService.updateById(existStoreOrder);
        if(!updateOrderResult) throw new CrmebException("申请退款失败");

        HashMap<String, Object> smsInfo = new HashMap<>();
        smsInfo.put("orderId", existStoreOrder.getOrderId());
        smsInfo.put("adminName", currentUser.getNickname());
        boolean codeResult = smsService.pushCodeToList(currentUser.getPhone(),1, smsInfo);
        if(!codeResult) throw new CrmebException("短信加入发送队列失败");
        return true;
    }

    /**
     * 订单退款申请
     * @param request OrderRefundApplyRequest 退款参数
     */
    @Override
    public boolean refundApply(OrderRefundApplyRequest request) {
        StoreOrder storeOrder = orderUtils.getInfoById(request.getId());
        if(storeOrder.getRefundStatus() == 1){
            throw new CrmebException("正在申请退款中");
        }

        if(storeOrder.getRefundStatus() == 2){
            throw new CrmebException("订单已退款");
        }

        if(storeOrder.getStatus() == 1){
            throw new CrmebException("订单当前无法退款");
        }
        storeOrder.setRefundReasonWapImg(systemAttachmentService.clearPrefix(request.getReasonImage()));
        storeOrder.setRefundStatus(1);
        storeOrder.setRefundReasonWapExplain(request.getExplain());
        storeOrder.setRefundReason(request.getText());
        storeOrder.setRefundPrice(storeOrder.getPayPrice());
        storeOrder.setRefundReasonTime(DateUtil.nowDateTime());

        // 发送微信小程序订阅消息
        String storeNameAndCarNumString = orderUtils.getStoreNameAndCarNumString(storeOrder.getId());
        if(StringUtils.isNotBlank(storeNameAndCarNumString)){
            WechatSendMessageForReFundNotify notify = new WechatSendMessageForReFundNotify(
                    storeNameAndCarNumString,storeOrder.getPayPrice().toString(),
                    storeOrder.getCreateTime().toString(),storeOrder.getOrderId()+"",DateUtil.nowDateTimeStr(),
                    "CRMEB","发起申请",request.getExplain(),storeOrder.getPayPrice()+"",
                    request.getText(),storeOrder.getUserPhone(),"CRMEB");
            wechatSendMessageForMinService.sendReFundNotifyMessage(notify, userService.getUserId());
        }
        return storeOrderService.updateById(storeOrder);
    }

    /**
     * 订单退款申请Task使用
     * @param applyList OrderRefundApplyRequest 退款参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean refundApplyTask(List<OrderRefundApplyRequest> applyList) {
        if (CollUtil.isEmpty(applyList)) {
            return false;
        }
        List<StoreOrder> orderList = CollUtil.newArrayList();
        List<Map<String, Object>> notifyMapList = CollUtil.newArrayList();
        for (OrderRefundApplyRequest request : applyList) {
            StoreOrder storeOrder = storeOrderService.getById(request.getId());
            if(ObjectUtil.isNull(storeOrder)){
                //订单号错误
                throw new CrmebException("没有找到相关订单信息!");
            }
            if(storeOrder.getRefundStatus() == 1){
                throw new CrmebException("正在申请退款中");
            }

            if(storeOrder.getRefundStatus() == 2){
                throw new CrmebException("订单已退款");
            }

            if(storeOrder.getStatus() == 1){
                throw new CrmebException("订单当前无法退款");
            }
            storeOrder.setRefundReasonWapImg(systemAttachmentService.clearPrefix(request.getReasonImage()));
            storeOrder.setRefundStatus(1);
            storeOrder.setRefundReasonWapExplain(request.getExplain());
            storeOrder.setRefundReason(request.getText());
            storeOrder.setRefundPrice(BigDecimal.ZERO);
            storeOrder.setRefundReasonTime(DateUtil.nowDateTime());
            orderList.add(storeOrder);

            // 发送微信小程序订阅消息
            String storeNameAndCarNumString = orderUtils.getStoreNameAndCarNumString(storeOrder.getId());
            if(StringUtils.isNotBlank(storeNameAndCarNumString)){
                WechatSendMessageForReFundNotify notify = new WechatSendMessageForReFundNotify(
                        storeNameAndCarNumString,storeOrder.getPayPrice().toString(),
                        storeOrder.getCreateTime().toString(),storeOrder.getOrderId()+"",DateUtil.nowDateTimeStr(),
                        "CRMEB","发起申请",request.getExplain(),storeOrder.getPayPrice()+"",
                        request.getText(),storeOrder.getUserPhone(),"CRMEB");
                Map<String, Object> map = CollUtil.newHashMap();
                map.put("notify", notify);
                map.put("uid", storeOrder.getUid());
                notifyMapList.add(map);
            }
        }

        boolean batch = storeOrderService.updateBatchById(orderList, 100);
        if (batch && notifyMapList.size() > 0) {
            // 发送微信小程序订阅消息
            notifyMapList.forEach(i -> {
                WechatSendMessageForReFundNotify notify = (WechatSendMessageForReFundNotify) i.get("notify");
                Integer uid = (Integer) i.get("uid");
                wechatSendMessageForMinService.sendReFundNotifyMessage(notify, uid);
            });
        }

        return batch;
    }


    /**
     * 再次下单
     * @param request 参数
     * @return 下单结果
     */
    @Override
    public HashMap<String, Object> againOrder(OrderAgainRequest request) {
        // 检查是否已经有相同的商品再次下单
//        StoreCartResponse cacheOrderAgain = orderUtils.getCacheOrderAgain(request.getNui());
//        if(null != cacheOrderAgain) throw new CrmebException("已经有相同的订单存在");

        HashMap<String, Object> resultMap = new HashMap<>();
        User currentUser = userService.getInfo();
        StoreOrder storeOrder = new StoreOrder();
        storeOrder.setUid(currentUser.getUid());
        storeOrder.setUnique(request.getNui());
        StoreOrder storeOrderExist = storeOrderService.getInfoByEntity(storeOrder);
        if(null == storeOrderExist) throw new CrmebException("订单不存在");
        OrderAgainVo orderAgainVo = orderUtils.tidyOrder(storeOrderExist, true, false);
//        List<StoreCart> storeCartResultList = new ArrayList<>();
        for (StoreOrderInfoVo oldCartInfo : orderAgainVo.getCartInfo()) { // todo 确实是否仅仅一条数据
            // todo 营销产品类型二期
            List<String> orderAgainCacheKeys = storeOrderService.addCartAgain(userService.getUserIdException(), oldCartInfo.getProductId(), oldCartInfo.getInfo().getCartNum(),
                    storeOrderExist.getUnique(), oldCartInfo.getInfo().getType(), true,
                    oldCartInfo.getInfo().getCombinationId(), oldCartInfo.getInfo().getSeckillId(), oldCartInfo.getInfo().getBargainId());
            resultMap.put("cateId",orderAgainCacheKeys.get(0));

        }
        if(resultMap.size() == 0) throw new CrmebException("再来一单失败，请重新下单");
//        resultMap.put("cateId",storeCartResultList.stream().map(StoreCart::getId).distinct().collect(Collectors.toList()));
        return resultMap;
    }

    /**
     * 支付订单
     * @param request 支付参数
     * @return 支付结果
     */
    @Override
    @Transactional(rollbackFor = {RuntimeException.class, Error.class, CrmebException.class})
    public HashMap<String, Object> payOrder(OrderPayRequest request, String ip) {
        HashMap<String, Object> resultMap = new HashMap<>();
        User currentUser = userService.getInfo();
        StoreOrder storeOrderPram = new StoreOrder();
        storeOrderPram.setOrderId(request.getUni());
        storeOrderPram.setUid(currentUser.getUid());
        storeOrderPram.setIsDel(false);
        StoreOrder existStoreOrder = storeOrderService.getInfoByEntity(storeOrderPram);
        if(null == existStoreOrder) throw new CrmebException("订单不存在");
        if(existStoreOrder.getPaid()) throw new CrmebException("该订单已支付");
        // 判断支付类型是否更改
        if(!existStoreOrder.getPayType().equals(request.getPaytype())){
            boolean changePayTypeResult = changePayType(request.getPaytype(), existStoreOrder.getOrderId());
            if(!changePayTypeResult) throw new CrmebException("更新订单支付状态失败");
        }
        // 支付
        if (doPayOrder(request, ip, resultMap, existStoreOrder)) return resultMap;
        throw new CrmebException("支付方式错误");
    }

    /**
     * 订单列表
     * @param status 类型
     * @param pageRequest 分页
     */
    @Override
    public List<OrderAgainVo> list(Integer status, PageParamRequest pageRequest) {
        List<OrderAgainVo> listResponses = new ArrayList<>();
        User currentUser = userService.getInfo();
        StoreOrder storeOrderPram = new StoreOrder();
        storeOrderPram.setUid(currentUser.getUid());
        storeOrderPram.setStatus(status);

        List<StoreOrder> orderList = storeOrderService.getUserOrderList(storeOrderPram, pageRequest);
        for (StoreOrder storeOrder : orderList) {
            OrderAgainVo orderAgainVo = new OrderAgainVo();
            StoreOrder storeOrderResult = new StoreOrder();
            BeanUtils.copyProperties(storeOrder, storeOrderResult);
            orderAgainVo.setStoreOrder(storeOrderResult);
            orderAgainVo = orderUtils.tidyOrder(storeOrder, true, false);
            if(null != orderAgainVo.getStatus() && orderAgainVo.getStatus().getType() == 3){
                for (StoreOrderInfoVo storeOrderInfoVo : orderAgainVo.getCartInfo()) {
                    if(orderAgainVo.getStatus().getType() == 3){
                        storeOrderInfoVo.getInfo().setIsReply(
                                storeProductReplyService.isReply(storeOrderInfoVo.getUnique(),"product",storeOrderInfoVo.getOrderId()).size());
                        storeOrderInfoVo.getInfo().setAddTime(storeOrderInfoVo.getInfo().getAddTime());
                    }
                }
            }
            listResponses.add(orderAgainVo);
        }
        return listResponses;
    }

    /**
     * 订单详情
     * @param orderId 订单id
     */
    @Override
    public StoreOrderDetailResponse detailOrder(String orderId) {
        StoreOrderDetailResponse storeOrderDetailResponse = new StoreOrderDetailResponse();
        User currentUser = userService.getInfo();
        StoreOrder storeOrderPram = new StoreOrder();
        storeOrderPram.setUid(currentUser.getUid());
        storeOrderPram.setUnique(orderId);
        storeOrderPram.setIsDel(false);
        StoreOrder storeOrderResult = storeOrderService.getInfoJustOrderInfo(storeOrderPram);
        if(null == storeOrderResult) throw new CrmebException("订单不存在");

        BeanUtils.copyProperties(storeOrderResult, storeOrderDetailResponse);
        // 是否开启门店自提
        String storeSelfMention = systemConfigService.getValueByKey("store_self_mention");
        if(!Boolean.valueOf(storeSelfMention)) storeOrderResult.setShippingType(1);
        if(storeOrderResult.getVerifyCode().length()>0){
            String verifyCode = storeOrderResult.getVerifyCode();
            List<String> verifyCodeList = new ArrayList<>();
            verifyCodeList.add(verifyCode.substring(0,4));
            verifyCodeList.add(verifyCode.substring(4,4));
            verifyCodeList.add(verifyCode.substring(8));
            storeOrderDetailResponse.setPVerifyCodes(verifyCodeList);
        }
        Date dateY = DateUtil.strToDate(storeOrderResult.getCreateTime().toString(), Constants.DATE_FORMAT_DATE);
        Date dateH = DateUtil.strToDate(storeOrderResult.getCreateTime().toString(), Constants.DATE_FORMAT_HHMMSS);
        storeOrderDetailResponse.setAddTimeH(dateY);
        storeOrderDetailResponse.setAddTimeH(dateH);
        SystemStore systemStorePram = new SystemStore();
//        systemStorePram.setIsShow(true);
//        systemStorePram.setIsDel(false);
        systemStorePram.setId(storeOrderResult.getStoreId());
        storeOrderDetailResponse.setSystemStore(systemStoreService.getByCondition(systemStorePram));

        if(storeOrderResult.getShippingType() == 3 && storeOrderResult.getVerifyCode().length() > 0){
            String name = storeOrderResult.getVerifyCode()+".jpg";
            SystemAttachment systemAttachmentPram = new SystemAttachment();
            systemAttachmentPram.setName(name);
            // todo 二维码前端生成
        }
        storeOrderDetailResponse.setMapKey(systemConfigService.getValueByKey("tengxun_map_key"));
//        StoreOrder storeOrder = new StoreOrder();
//        BeanUtils.copyProperties(storeOrderDetailResponse,storeOrder);
        OrderAgainVo orderAgainVo = orderUtils.tidyOrder(storeOrderResult, true, true);
        BeanUtils.copyProperties(orderAgainVo.getStoreOrder(), storeOrderDetailResponse);
        storeOrderDetailResponse.setCartInfo(orderAgainVo.getCartInfo());
        storeOrderDetailResponse.setPStatus(orderAgainVo.getStatus());
        storeOrderDetailResponse.setStatusPic(orderAgainVo.getStatusPic());
        return storeOrderDetailResponse;
    }

    /**
     * 订单tap data
     * @return 订单状态数据量
     */
    @Override
    public OrderDataResponse orderData() {
        OrderDataResponse result = new OrderDataResponse();
        User currentUser = userService.getInfo();

        StoreOrder storeOrderOrderCountPram = new StoreOrder();
        storeOrderOrderCountPram.setIsDel(false).setPaid(true).setUid(currentUser.getUid()).setRefundStatus(0);
        result.setOrderCount(storeOrderService.getByEntity(storeOrderOrderCountPram).size());

        StoreOrder storeOrderSumPricePram = new StoreOrder();
        storeOrderSumPricePram.setIsDel(false).setPaid(true).setUid(currentUser.getUid()).setRefundStatus(0);
        List<StoreOrder> storeOrdersSumPriceResult = storeOrderService.getByEntity(storeOrderSumPricePram);
        double sumPrice = storeOrdersSumPriceResult.stream().mapToDouble(e -> e.getPayPrice().doubleValue()).sum();
        result.setSumPrice(BigDecimal.valueOf(sumPrice));

        // 未支付
        result.setUnPaidCount(storeOrderService.getTopDataUtil(Constants.ORDER_STATUS_H5_UNPAID, currentUser.getUid()).size());
        // 待发货
        result.setUnShippedCount(storeOrderService.getTopDataUtil(Constants.ORDER_STATUS_H5_NOT_SHIPPED, currentUser.getUid()).size());
        // 待收货
        result.setReceivedCount(storeOrderService.getTopDataUtil(Constants.ORDER_STATUS_H5_SPIKE, currentUser.getUid()).size());
        // 待核销
        result.setEvaluatedCount(storeOrderService.getTopDataUtil(Constants.ORDER_STATUS_H5_JUDGE, currentUser.getUid()).size());
        // 已完成
        result.setCompleteCount(storeOrderService.getTopDataUtil(Constants.ORDER_STATUS_H5_COMPLETE, currentUser.getUid()).size());
        // 退款中和已退款
        result.setRefundCount(storeOrderService.getTopDataUtil(Constants.ORDER_STATUS_H5_REFUND, currentUser.getUid()).size());
        return result;
    }

    /**
     * 查询退款理由
     * @return 退款理由集合
     */
    @Override
    public List<String> getRefundReason(){
        String reasonString = systemConfigService.getValueByKey("stor_reason");
        reasonString = CrmebUtil.UnicodeToCN(reasonString);
        reasonString = reasonString.replace("rn", "n");
        return Arrays.asList(reasonString.split("\\n"));
    }

    /**
     * 计算订单价格
     * @param request 订单提交参数
     * @param orderKey 订单key
     * @return 订单计算结果
     */
    @Override
    public HashMap<String, Object> computedOrder(OrderComputedRequest request, String orderKey) {
        HashMap<String, Object> resultMap = new HashMap<>();
        User currentUser = userService.getInfoException();
        // 检查订单是否存在 --正常的购买流程
        if(orderUtils.checkOrderExist(orderKey, currentUser.getUid())) {
            OrderPayItemResponse itemResponse = new OrderPayItemResponse(orderKey,orderKey);
            OrderPayResponse orderPayResponse = new OrderPayResponse("extend_order",itemResponse);
            resultMap.put("result", orderPayResponse);
            return  resultMap;
        }

        // 立即购买或者秒杀或者砍价
        String existCacheOrder = orderUtils.cacheGetOrderInfo(userService.getUserIdException(), orderKey);
        if(null == existCacheOrder) throw new CrmebException("订单已过期,请刷新当前页面!");
        ConfirmOrderResponse cor = JSONObject.parseObject(existCacheOrder,ConfirmOrderResponse.class);

        // 秒杀商品数据验证
        if(null != cor.getSecKillId() && cor.getSecKillId()>0){
            StoreCart storeCartPram = new StoreCart()
                    .setSeckillId(cor.getCartInfo().get(0).getSeckillId())
                    .setUid(currentUser.getUid())
                    .setProductAttrUnique(cor.getCartInfo().get(0).getProductAttrUnique());
            orderUtils.validSecKill(storeCartPram, currentUser);
        }

        // 砍价商品数据校验
        if (ObjectUtil.isNotNull(cor.getBargainId()) && cor.getBargainId() > 0) {
            StoreCart storeCartPram = new StoreCart()
                    .setBargainId(cor.getCartInfo().get(0).getBargainId())
                    .setUid(currentUser.getUid())
                    .setProductAttrUnique(cor.getCartInfo().get(0).getProductAttrUnique());
            orderUtils.validBargain(storeCartPram, currentUser);
        }

        // 拼团商品数据校验
        if (ObjectUtil.isNotNull(cor.getCombinationId()) && cor.getCombinationId() >0) {
            StoreCart storeCartPram = new StoreCart()
                    .setCombinationId(cor.getCartInfo().get(0).getCombinationId())
                    .setUid(currentUser.getUid())
                    .setCartNum(cor.getCartInfo().get(0).getCartNum())
                    .setProductAttrUnique(cor.getCartInfo().get(0).getProductAttrUnique());
            orderUtils.validCombination(storeCartPram, currentUser);
        }

        OrderCreateRequest orderCreateRequest = new OrderCreateRequest();
        BeanUtils.copyProperties(request,orderCreateRequest);
        ComputeOrderResponse priceGroup = orderUtils.computedOrder(orderCreateRequest, cor, orderKey);
        if(null == priceGroup){
            throw new CrmebException("计算失败");
        }else{
            OrderPayItemResponse resultItemResponse = new OrderPayItemResponse(orderKey,priceGroup);
            OrderPayResponse resultOrderPayResponse = new OrderPayResponse("NONE",resultItemResponse);
            resultMap.put("result", resultOrderPayResponse);
            return resultMap;
        }
    }

    /**
     * 订单物流查看
     * @param orderId 订单id
     */
    @Override
    public Object expressOrder(String orderId) {
        HashMap<String,Object> resultMap = new HashMap<>();
        StoreOrder storeOrderPram = new StoreOrder();
        storeOrderPram.setOrderId(orderId);
        StoreOrder existOrder = storeOrderService.getByEntityOne(storeOrderPram);
        if(null== existOrder) throw new CrmebException("未找到该订单信息");
        if(!existOrder.getDeliveryType().equals(Constants.ORDER_LOG_EXPRESS) || StringUtils.isBlank(existOrder.getDeliveryType()))
            throw new CrmebException("该订单不存在快递订单号");

        LogisticsResultVo expressInfo = logisticsService.info(existOrder.getDeliveryId(), null, Optional.ofNullable(existOrder.getDeliveryCode()).orElse(""), storeOrderPram.getUserPhone());

        PageParamRequest page = new PageParamRequest();
        page.setPage(1); page.setLimit(999);
        StoreOrderInfoSearchRequest storeOrderInfoPram = new StoreOrderInfoSearchRequest();
        storeOrderInfoPram.setOrderId(existOrder.getId());
        List<StoreOrderInfo> list = storeOrderInfoService.getList(storeOrderInfoPram, page);
        List<HashMap<String, Object>> cartInfos = new ArrayList<>();
        for (StoreOrderInfo storeInfo : list) {
            HashMap<String, Object> cartInfo = new HashMap<>();
            StoreCartResponse scr = JSONObject.parseObject(storeInfo.getInfo(), StoreCartResponse.class);
            cartInfo.put("cartNum", scr.getCartNum());
            cartInfo.put("truePrice", scr.getTruePrice());
            cartInfo.put("productInfo", scr.getProductInfo());
            cartInfos.add(cartInfo);
        }
        HashMap<String, Object> orderInfo = new HashMap<>();
        orderInfo.put("deliveryId", existOrder.getDeliveryId());
        orderInfo.put("deliveryName", existOrder.getDeliveryName());
        orderInfo.put("deliveryType", existOrder.getDeliveryType());
        orderInfo.put("cartInfo", cartInfos);

        resultMap.put("order", orderInfo);
        resultMap.put("express", expressInfo);
        return resultMap;
//        expressService.getExpressInfo();

    }

    /**
     *获取待评价信息
     * @return
     */
    @Override
    public Object getReplyProduct(GetProductReply productReply) {
        HashMap<String,Object> resultMap = new HashMap<>();
//        StoreOrder storeOrderPram = new StoreOrder();
//        storeOrderPram.setUnique(unique);
//        StoreOrder existOrder = storeOrderService.getByEntityOne(storeOrderPram);
//        if(null== existOrder) throw new CrmebException("未找到该订单信息");
        StoreOrderInfoSearchRequest soinfoRequest = new StoreOrderInfoSearchRequest();
        soinfoRequest.setUnique(productReply.getUni());
        soinfoRequest.setOrderId(productReply.getOrderId());
        PageParamRequest pageRequest = new PageParamRequest();
        pageRequest.setLimit(999); pageRequest.setPage(1);
        List<StoreOrderInfo> orderInfos = storeOrderInfoService.getList(soinfoRequest, pageRequest);
        // 这里其实只能拿到一条信息
        for (StoreOrderInfo storeInfo : orderInfos) {
            HashMap<String, Object> cartInfo = new HashMap<>();
            StoreCartResponse scr = JSONObject.parseObject(storeInfo.getInfo(), StoreCartResponse.class);
            resultMap.put("cartNum", scr.getCartNum());
            cartInfo.put("truePrice", scr.getTruePrice());
            resultMap.put("productInfo", scr.getProductInfo());
//            resultMap.put("orderInfo", existOrder);
            resultMap.put("productId", scr.getProductInfo().getId());
        }
        return resultMap;
    }

    /**
     * 更换支付类型
     * @param payType 支付类型
     */
    @Override
    public boolean changePayType(String payType,String orderId) {
        StoreOrder storeOrderPram = new StoreOrder();
        storeOrderPram.setOrderId(orderId);
        StoreOrder existOrder = storeOrderService.getByEntityOne(storeOrderPram);
        if(null == existOrder) throw new CrmebException("未找到订单信息");
        existOrder.setPayType(payType);
        return storeOrderService.updateById(existOrder);
    }

    ///////////////////////////////////////////////////////////////////// 自定义方法

    /**
     * 仅仅支付
     * 订单支付完之后给出状态值 以便前端使用， 后期这里也可以抽出对象
     * ORDEREEXIST, EXTENDORDER, PAYERROR, SUCCESS, WECHATPAY, PAYDEFICIENCY, WECHATH5PAY
     * @param request           支付订单参数
     * @param ip                发起支付IP
     * @param resultMap         计算订单时价格集合
     * @param existStoreOrder   当前订单
     * @return                  支付状态
     */
    @Transactional(rollbackFor = {RuntimeException.class, Error.class, CrmebException.class})
    public boolean doPayOrder(OrderPayRequest request, String ip, HashMap<String, Object> resultMap, StoreOrder existStoreOrder) {
        existStoreOrder.setPayType(request.getPaytype());
        CreateOrderResponseVo orderPayResult = orderPayService.payOrder(existStoreOrder.getId(), request.getFrom(), ip);
        // 下面组装前端所需数据
        switch (existStoreOrder.getPayType()){
            case Constants.PAY_TYPE_WE_CHAT:
            case Constants.PAY_TYPE_WE_CHAT_FROM_PROGRAM:
                if(existStoreOrder.getPayType().equals(Constants.PAY_TYPE_WE_CHAT_FROM_H5)){
                    OrderPayItemResponse itemResponse = new OrderPayItemResponse(orderPayResult.getPrepayId(), existStoreOrder.getOrderId());
                    OrderPayResponse orderPayResponse = new OrderPayResponse("WECHAT_H5_PAY",itemResponse);
                    resultMap.put("result", orderPayResponse.getResult());
                    resultMap.put("status","WECHAT_H5_PAY");
                    resultMap.put("jsConfig", orderPayResult.getTransJsConfig());
                    return true;
                }else{
                    OrderPayItemResponse itemResponse = new OrderPayItemResponse(orderPayResult.getPrepayId(), existStoreOrder.getOrderId());
                    OrderPayResponse orderPayResponse = new OrderPayResponse("WECHAT_PAY",itemResponse);
                    resultMap.put("result", orderPayResponse.getResult());
                    resultMap.put("status","WECHAT_PAY");
                    resultMap.put("jsConfig", orderPayResult.getTransJsConfig());
                    return true;
                }
            case Constants.PAY_TYPE_YUE:
                OrderPayResponse orderPayResponseY = new OrderPayResponse("SUCCESS",
                        new OrderPayItemResponse(request.getUni(), existStoreOrder.getOrderId()));
                resultMap.put("result", orderPayResponseY.getResult());
                resultMap.put("status","SUCCESS");
                return true;
            case Constants.PAY_TYPE_OFFLINE:
                StoreOrder storeOrderOffLinePram = new StoreOrder();
                storeOrderOffLinePram.setOrderId(existStoreOrder.getOrderId());
                storeOrderOffLinePram.setPayType(Constants.PAY_TYPE_OFFLINE);
                boolean offlineResult = storeOrderService.updateByEntity(storeOrderOffLinePram);
                if(offlineResult){
                    resultMap.put("result", "订单创建成功");
                    return true;
                }else{
                    throw new CrmebException("支付失败");
                }
        }
        return false;
    }

}
