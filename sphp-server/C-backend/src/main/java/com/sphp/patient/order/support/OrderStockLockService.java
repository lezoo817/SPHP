package com.sphp.patient.order.support;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.common.constant.OrderConstant;
import com.sphp.patient.order.mapper.OrderStockRecord;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.sphp.patient.common.constant.OrderConstant.*;
import static com.sphp.shared.common.enums.ErrorCodeEnum.HIGH_CONCURRENCY_INVENTORY_CONFLICT;
import static com.sphp.shared.common.enums.ErrorCodeEnum.SYSTEM_ERROR;

/**
 * 购药下单的 Redisson 多药品库存锁服务。
 */
@Service
@RequiredArgsConstructor
public class OrderStockLockService {

    private final RedissonClient redissonClient;

    /**
     * 按稳定顺序获取药房药品库存锁，并在当前事务结束后统一释放。
     *
     * @param stocks 需要锁定的药品库存
     * @param action 锁内业务操作
     * @param <T> 业务返回类型
     * @return 锁内业务结果
     * @throws CAuthException 锁竞争、线程中断或事务环境异常时抛出
     */
    public <T> T executeWithStockLocks(List<OrderStockRecord> stocks, Supplier<T> action) {
        List<RLock> locks = stocks.stream()
                .sorted(Comparator.comparing(OrderStockRecord::pharmacyId) // 按药房 ID 排序
                        .thenComparing(OrderStockRecord::drugId)) // 按药品 ID 排序
                .map(stock -> redissonClient.getLock(
                        STOCK_LOCK_KEY_PREFIX + stock.pharmacyId() + ":" + stock.drugId()))
                .toList();
        List<RLock> acquired = new ArrayList<>();
        // 锁需覆盖数据库提交，提交或回滚完成后再释放，避免其他请求读到未提交库存。
        boolean deferredRelease = false;
        try {
            for (RLock lock : locks) {
                // 固定等待与租约时间，避免锁无限等待或泄漏。
                if (!lock.tryLock(STOCK_LOCK_WAIT_SECONDS, STOCK_LOCK_LEASE_SECONDS,
                        TimeUnit.SECONDS)) {
                    throw concurrentConflict();
                }
                acquired.add(lock);
            }
            // 事务需覆盖数据库提交，提交或回滚完成后再释放，避免其他请求读到未提交库存。
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                throw systemError("库存锁未处于事务环境");
            }
            T result = action.get(); // 锁内业务

            // 锁需覆盖数据库提交，提交或回滚完成后再释放，避免其他请求读到未提交库存。
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    releaseLocks(acquired);
                }
            });
            deferredRelease = true;
            return result;
        } catch (InterruptedException exception) {
            // 线程中断
            Thread.currentThread().interrupt();
            throw concurrentConflict();
        } finally {
            // 如果锁未处于事务环境，则立即释放锁
            if (!deferredRelease) {
                releaseLocks(acquired);
            }
        }
    }

    /**
     * 释放当前线程已持有的全部库存锁。
     *
     * @param locks 已获取的库存锁
     */
    private void releaseLocks(List<RLock> locks) {
        for (int index = locks.size() - 1; index >= 0; index--) {
            RLock lock = locks.get(index);
            // 避免锁未处于事务环境
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 创建库存并发竞争异常。
     *
     * @return HTTP 409 业务异常
     */
    private CAuthException concurrentConflict() {
        return new CAuthException(HIGH_CONCURRENCY_INVENTORY_CONFLICT,
                HttpStatus.CONFLICT, "当前药品库存正在处理中，请稍后重试");
    }

    /**
     * 创建系统异常。
     *
     * @param message 面向客户端提示
     * @return HTTP 500 业务异常
     */
    private CAuthException systemError(String message) {
        return new CAuthException(SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}
