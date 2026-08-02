package com.sphp.patient.common.constant;

/**
 * C端购药订单公共常量。
 */
public final class OrderConstant {

    /** 药品库存 Redisson 锁键前缀 */
    public static final String STOCK_LOCK_KEY_PREFIX = "cend:stock:lock:";
    /** 购药订单列表默认页码 */
    public static final int DEFAULT_PAGE_NO = 1;
    /** 购药订单列表默认页大小 */
    public static final int DEFAULT_PAGE_SIZE = 20;
    /** 购药订单列表最大页大小 */
    public static final int MAX_PAGE_SIZE = 100;
    /** 库存锁最大等待秒数 */
    public static final long STOCK_LOCK_WAIT_SECONDS = 1L;
    /** 库存锁租约秒数 */
    public static final long STOCK_LOCK_LEASE_SECONDS = 30L;
    /** C端业务交换机名称 */
    public static final String BUSINESS_EXCHANGE = "cend.business.exchange";
    /** C端死信交换机名称 */
    public static final String DLX_EXCHANGE = "cend.dlx.exchange";
    /** 购药订单待支付延迟队列 */
    public static final String DRUG_ORDER_DELAY_QUEUE = "cend.drug-order.delay.queue";
    /** 购药订单超时队列 */
    public static final String DRUG_ORDER_TIMEOUT_QUEUE = "cend.drug-order.timeout.queue";
    /** 购药订单创建路由键 */
    public static final String DRUG_ORDER_PENDING_ROUTING_KEY = "drug-order.pending";
    /** 购药订单超时路由键 */
    public static final String DRUG_ORDER_TIMEOUT_ROUTING_KEY = "drug-order.timeout";

    /**
     * 防止常量类被实例化。
     */
    private OrderConstant() {
    }
}
