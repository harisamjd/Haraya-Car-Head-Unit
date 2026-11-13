import { Env } from '../types.js';

export interface CreatePaymentParams {
  orderId: string;
  deviceId: string;
  amount: number;
  currency: string;
  email?: string;
  phone?: string;
  baseUrl: string;
}

export interface PaymentGatewayAdapter {
  readonly name: string;
  createPaymentUrl(params: CreatePaymentParams): Promise<string>;
  verifyWebhook(requestPayload: any): Promise<{ valid: boolean; orderId: string; status: 'PAID' | 'FAILED' }>;
}

export class MockPaymentAdapter implements PaymentGatewayAdapter {
  readonly name = 'mock';

  async createPaymentUrl(params: CreatePaymentParams): Promise<string> {
    const query = new URLSearchParams({
      order_id: params.orderId,
      device_id: params.deviceId,
      amount: String(params.amount)
    });
    return `${params.baseUrl}/api/mock/checkout?${query.toString()}`;
  }

  async verifyWebhook(payload: any): Promise<{ valid: boolean; orderId: string; status: 'PAID' | 'FAILED' }> {
    if (!payload || !payload.order_id) {
      return { valid: false, orderId: '', status: 'FAILED' };
    }
    const status = payload.status === 'PAID' ? 'PAID' : 'FAILED';
    return { valid: true, orderId: payload.order_id, status };
  }
}

export function getPaymentAdapter(env: Env): PaymentGatewayAdapter {
  // If mock enabled or in development, return MockPaymentAdapter
  return new MockPaymentAdapter();
}

