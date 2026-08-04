import type { DeliveryAddress, DeliveryAddressDeleteResult, DeliveryAddressPayload } from '../typings/api';
import { request } from './request';

/** 构造收货地址列表的后端相对路径。 */
export function buildDeliveryAddressPath(): string {
  return '/c/v1/delivery-addresses';
}

/** 查询当前登录账号有效收货地址，服务端默认地址优先返回。 */
export function getDeliveryAddresses(): Promise<DeliveryAddress[]> {
  return request(buildDeliveryAddressPath(), { method: 'GET' });
}

/** 新增当前登录账号的收货地址。 */
export function createDeliveryAddress(payload: DeliveryAddressPayload, idempotencyKey: string): Promise<DeliveryAddress> {
  return request(buildDeliveryAddressPath(), { method: 'POST', body: payload, headers: { 'X-Idempotency-Key': idempotencyKey } });
}

/** 更新当前登录账号拥有的收货地址。 */
export function updateDeliveryAddress(addressId: number, payload: DeliveryAddressPayload, idempotencyKey: string): Promise<DeliveryAddress> {
  return request(`/c/v1/delivery-addresses/${addressId}`, { method: 'PUT', body: payload, headers: { 'X-Idempotency-Key': idempotencyKey } });
}

/** 软删除当前登录账号拥有的收货地址。 */
export function deleteDeliveryAddress(addressId: number, idempotencyKey: string): Promise<DeliveryAddressDeleteResult> {
  return request(`/c/v1/delivery-addresses/${addressId}`, { method: 'DELETE', headers: { 'X-Idempotency-Key': idempotencyKey } });
}

/** 将当前登录账号拥有的地址设为默认地址。 */
export function setDefaultDeliveryAddress(addressId: number, idempotencyKey: string): Promise<DeliveryAddress> {
  return request(`/c/v1/delivery-addresses/${addressId}/default`, { method: 'POST', headers: { 'X-Idempotency-Key': idempotencyKey } });
}
